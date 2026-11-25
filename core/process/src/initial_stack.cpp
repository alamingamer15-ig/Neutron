#include "neutron/process/initial_stack.hpp"

#include "neutron/memory/guest_memory.hpp"

#include <algorithm>
#include <array>
#include <cstring>
#include <limits>
#include <random>
#include <utility>

namespace neutron::process {
namespace {

constexpr std::uint64_t kMaximumUserAddress = 0x00007fffffffffffULL;
constexpr std::uint32_t kStackPermissions =
    memory::kMemoryRead | memory::kMemoryWrite;

// Linux x86-64 auxiliary-vector identifiers.
constexpr std::uint64_t kAtNull = 0;
constexpr std::uint64_t kAtPhdr = 3;
constexpr std::uint64_t kAtPhent = 4;
constexpr std::uint64_t kAtPhnum = 5;
constexpr std::uint64_t kAtPagesz = 6;
constexpr std::uint64_t kAtBase = 7;
constexpr std::uint64_t kAtEntry = 9;
constexpr std::uint64_t kAtRandom = 25;
constexpr std::uint64_t kAtExecfn = 31;
constexpr std::uint64_t kAtPlatform = 15;

struct AuxEntry {
    std::uint64_t type;
    std::uint64_t value;
};

void SetError(StackError* error, StackErrorCode code, const char* message) {
    if (error != nullptr) {
        error->code = code;
        error->message = message;
    }
}

bool IsPowerOfTwo(std::uint64_t value) {
    return value != 0 && (value & (value - 1)) == 0;
}

bool AddOverflows(std::uint64_t left, std::uint64_t right) {
    return right > std::numeric_limits<std::uint64_t>::max() - left;
}

std::uint64_t AlignDown(std::uint64_t value, std::uint64_t alignment) {
    return value & ~(alignment - 1);
}

void AppendWord(std::vector<std::uint8_t>* bytes, std::uint64_t value) {
    for (unsigned int i = 0; i < 8; ++i) {
        bytes->push_back(static_cast<std::uint8_t>(value >> (i * 8)));
    }
}

} // namespace

bool InitialStackBuilder::Build(memory::GuestMemory& memory,
                                const elf::LoadedImage& image,
                                const InitialStackOptions& options,
                                InitialStack* output,
                                StackError* error) {
    if (error != nullptr) *error = {};
    if (output == nullptr || options.arguments.size() > 65536 ||
        options.environment.size() > 65536 || options.executable_path.empty() ||
        options.page_size != memory::SparseGuestMemory::kPageSize ||
        !IsPowerOfTwo(options.page_size) ||
        options.stack_size == 0 ||
        (options.stack_size % options.page_size) != 0 ||
        (options.stack_top % options.page_size) != 0 ||
        options.stack_top > kMaximumUserAddress ||
        options.stack_size > options.stack_top ||
        options.maximum_string_bytes == 0) {
        SetError(error, StackErrorCode::InvalidInput,
                 "Invalid output, argument count, executable path, or stack configuration");
        return false;
    }

    const std::uint64_t stack_base = options.stack_top - options.stack_size;
    if (stack_base < options.page_size ||
        AddOverflows(stack_base, options.stack_size) ||
        stack_base + options.stack_size - 1 > kMaximumUserAddress) {
        SetError(error, StackErrorCode::InvalidStackRange,
                 "Stack range is outside the supported x86-64 user address space");
        return false;
    }

    std::size_t string_bytes = options.executable_path.size() + 1;
    for (const std::string& value : options.arguments) {
        if (value.find('\0') != std::string::npos ||
            value.size() > options.maximum_string_bytes - std::min(
                string_bytes, options.maximum_string_bytes)) {
            SetError(error, StackErrorCode::StringsTooLarge,
                     "Argument or environment strings exceed the configured limit");
            return false;
        }
        string_bytes += value.size() + 1;
    }
    for (const std::string& value : options.environment) {
        if (value.find('\0') != std::string::npos ||
            string_bytes > options.maximum_string_bytes ||
            value.size() + 1 > options.maximum_string_bytes - string_bytes) {
            SetError(error, StackErrorCode::StringsTooLarge,
                     "Argument or environment strings exceed the configured limit");
            return false;
        }
        string_bytes += value.size() + 1;
    }
    if (options.executable_path.find('\0') != std::string::npos ||
        string_bytes > options.maximum_string_bytes) {
        SetError(error, StackErrorCode::StringsTooLarge,
                 "Executable path or process strings exceed the configured limit");
        return false;
    }

    if (!memory.Map(stack_base, options.stack_size, kStackPermissions)) {
        SetError(error, StackErrorCode::MappingFailure,
                 "Could not map the guest process stack");
        return false;
    }

    const auto fail = [&](StackErrorCode code, const char* message) {
        memory.Unmap(stack_base, options.stack_size);
        SetError(error, code, message);
        return false;
    };

    std::uint64_t cursor = options.stack_top;
    const auto push_string = [&](const std::string& value,
                                 std::uint64_t* address) -> bool {
        const std::uint64_t amount = static_cast<std::uint64_t>(value.size()) + 1;
        if (amount > cursor - stack_base) return false;
        cursor -= amount;
        if (!value.empty() &&
            !memory.Write(cursor, value.data(), value.size())) return false;
        const std::uint8_t terminator = 0;
        if (!memory.Write(cursor + value.size(), &terminator, 1)) return false;
        *address = cursor;
        return true;
    };

    std::vector<std::uint64_t> argument_addresses(options.arguments.size());
    std::vector<std::uint64_t> environment_addresses(options.environment.size());
    std::uint64_t executable_address = 0;
    std::uint64_t platform_address = 0;
    std::uint64_t random_address = 0;

    if (!push_string(options.executable_path, &executable_address)) {
        return fail(StackErrorCode::StackOverflow, "Executable path does not fit on the stack");
    }
    for (std::size_t i = options.environment.size(); i > 0; --i) {
        if (!push_string(options.environment[i - 1], &environment_addresses[i - 1])) {
            return fail(StackErrorCode::StackOverflow, "Environment strings do not fit on the stack");
        }
    }
    for (std::size_t i = options.arguments.size(); i > 0; --i) {
        if (!push_string(options.arguments[i - 1], &argument_addresses[i - 1])) {
            return fail(StackErrorCode::StackOverflow, "Argument strings do not fit on the stack");
        }
    }

    const std::string platform = "x86_64";
    if (!push_string(platform, &platform_address)) {
        return fail(StackErrorCode::StackOverflow, "Platform string does not fit on the stack");
    }

    std::array<std::uint8_t, 16> random_bytes{};
    try {
        std::random_device random_device;
        for (std::uint8_t& byte : random_bytes) {
            byte = static_cast<std::uint8_t>(random_device());
        }
    } catch (...) {
        return fail(StackErrorCode::MemoryWriteFailure,
                    "Could not obtain process auxiliary-vector random bytes");
    }
    if (random_bytes.size() > cursor - stack_base) {
        return fail(StackErrorCode::StackOverflow, "AT_RANDOM data does not fit on the stack");
    }
    cursor -= random_bytes.size();
    random_address = cursor;
    if (!memory.Write(random_address, random_bytes.data(), random_bytes.size())) {
        return fail(StackErrorCode::MemoryWriteFailure, "Could not write AT_RANDOM data");
    }

    std::vector<AuxEntry> auxiliary;
    auxiliary.push_back({kAtPhdr, image.program_header_address});
    auxiliary.push_back({kAtPhent, 56});
    auxiliary.push_back({kAtPhnum, image.program_header_count});
    auxiliary.push_back({kAtPagesz, options.page_size});
    auxiliary.push_back({kAtBase, options.interpreter_base});
    auxiliary.push_back({kAtEntry, image.entry_point});
    auxiliary.push_back({kAtRandom, random_address});
    auxiliary.push_back({kAtExecfn, executable_address});
    auxiliary.push_back({kAtPlatform, platform_address});
    auxiliary.push_back({kAtNull, 0});

    std::vector<std::uint64_t> words;
    words.reserve(1 + argument_addresses.size() + 1 +
                  environment_addresses.size() + 1 + auxiliary.size() * 2);
    words.push_back(argument_addresses.size());
    words.insert(words.end(), argument_addresses.begin(), argument_addresses.end());
    words.push_back(0);
    words.insert(words.end(), environment_addresses.begin(), environment_addresses.end());
    words.push_back(0);
    for (const AuxEntry& entry : auxiliary) {
        words.push_back(entry.type);
        words.push_back(entry.value);
    }

    if (words.size() > std::numeric_limits<std::size_t>::max() / 8) {
        return fail(StackErrorCode::StackOverflow, "Initial stack table size overflows");
    }
    const std::uint64_t table_size = static_cast<std::uint64_t>(words.size()) * 8;
    if (table_size > cursor - stack_base) {
        return fail(StackErrorCode::StackOverflow, "Initial stack tables do not fit");
    }
    const std::uint64_t stack_pointer = AlignDown(cursor - table_size, 16);
    if (stack_pointer < stack_base) {
        return fail(StackErrorCode::StackOverflow, "Aligned initial stack does not fit");
    }

    std::vector<std::uint8_t> table;
    table.reserve(static_cast<std::size_t>(table_size));
    for (const std::uint64_t word : words) AppendWord(&table, word);
    if (!memory.Write(stack_pointer, table.data(), table.size())) {
        return fail(StackErrorCode::MemoryWriteFailure, "Could not write argc, argv, envp, and auxv");
    }

    InitialStack result;
    result.stack_pointer = stack_pointer;
    result.stack_base = stack_base;
    result.stack_size = options.stack_size;
    result.argument_count = argument_addresses.size();
    *output = result;
    return true;
}

const char* StackErrorCodeName(StackErrorCode code) noexcept {
    switch (code) {
        case StackErrorCode::None: return "none";
        case StackErrorCode::InvalidInput: return "invalid_input";
        case StackErrorCode::InvalidStackRange: return "invalid_stack_range";
        case StackErrorCode::TooManyStrings: return "too_many_strings";
        case StackErrorCode::StringsTooLarge: return "strings_too_large";
        case StackErrorCode::StackOverflow: return "stack_overflow";
        case StackErrorCode::MappingFailure: return "mapping_failure";
        case StackErrorCode::MemoryWriteFailure: return "memory_write_failure";
    }
    return "unknown";
}

} // namespace neutron::process
