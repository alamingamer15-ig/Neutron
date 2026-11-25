#include "neutron/process/initial_stack.hpp"

#include <cassert>
#include <cstdint>
#include <string>
#include <vector>

namespace {

std::uint64_t ReadWord(const neutron::memory::GuestMemory& memory,
                       std::uint64_t address) {
    std::uint8_t bytes[8]{};
    assert(memory.Read(address, bytes, sizeof(bytes)));
    std::uint64_t value = 0;
    for (unsigned int i = 0; i < 8; ++i)
        value |= static_cast<std::uint64_t>(bytes[i]) << (i * 8);
    return value;
}

std::string ReadString(const neutron::memory::GuestMemory& memory,
                       std::uint64_t address) {
    std::string value;
    for (std::size_t i = 0; i < 4096; ++i) {
        char byte = 0;
        assert(memory.Read(address + i, &byte, 1));
        if (byte == '\0') return value;
        value.push_back(byte);
    }
    assert(false && "unterminated guest string");
    return {};
}

void TestInitialStackLayout() {
    using namespace neutron;

    memory::SparseGuestMemory memory(16 * 1024 * 1024);
    elf::LoadedImage image;
    image.entry_point = 0x400123;
    image.program_header_address = 0x400040;
    image.program_header_count = 7;

    process::InitialStackOptions options;
    options.arguments = {"/bin/demo", "--verbose"};
    options.environment = {"LANG=C", "HOME=/home/guest"};
    options.executable_path = "/bin/demo";
    options.stack_size = 64 * 1024;

    process::InitialStack stack;
    process::StackError error;
    assert(process::InitialStackBuilder::Build(
        memory, image, options, &stack, &error));

    assert(stack.stack_pointer % 16 == 0);
    assert(stack.argument_count == 2);
    assert(stack.stack_base == options.stack_top - options.stack_size);
    assert(memory.MappedBytes() == options.stack_size);

    const std::uint64_t sp = stack.stack_pointer;
    assert(ReadWord(memory, sp) == 2); // argc
    const std::uint64_t argv0 = ReadWord(memory, sp + 8);
    const std::uint64_t argv1 = ReadWord(memory, sp + 16);
    assert(ReadWord(memory, sp + 24) == 0);
    assert(ReadString(memory, argv0) == "/bin/demo");
    assert(ReadString(memory, argv1) == "--verbose");

    const std::uint64_t env0 = ReadWord(memory, sp + 32);
    const std::uint64_t env1 = ReadWord(memory, sp + 40);
    assert(ReadWord(memory, sp + 48) == 0);
    assert(ReadString(memory, env0) == "LANG=C");
    assert(ReadString(memory, env1) == "HOME=/home/guest");

    std::uint64_t cursor = sp + 56;
    bool saw_phdr = false;
    bool saw_phnum = false;
    bool saw_entry = false;
    bool saw_random = false;
    bool saw_execfn = false;
    bool saw_platform = false;
    for (;;) {
        const std::uint64_t type = ReadWord(memory, cursor);
        const std::uint64_t value = ReadWord(memory, cursor + 8);
        cursor += 16;
        if (type == 0) {
            assert(value == 0);
            break;
        }
        if (type == 3) { saw_phdr = value == image.program_header_address; }
        if (type == 5) { saw_phnum = value == image.program_header_count; }
        if (type == 9) { saw_entry = value == image.entry_point; }
        if (type == 25) {
            std::uint8_t random_data[16]{};
            assert(memory.Read(value, random_data, sizeof(random_data)));
            saw_random = true;
        }
        if (type == 31) saw_execfn = ReadString(memory, value) == options.executable_path;
        if (type == 15) saw_platform = ReadString(memory, value) == "x86_64";
    }
    assert(saw_phdr && saw_phnum && saw_entry && saw_random && saw_execfn && saw_platform);

    // A guard page immediately below the stack remains unmapped.
    std::uint8_t byte = 0;
    assert(!memory.Read(stack.stack_base - 1, &byte, 1));
}

void TestStackMappingConflictAndOutputPreservation() {
    using namespace neutron;

    memory::SparseGuestMemory memory(1024 * 1024);
    process::InitialStackOptions options;
    options.arguments = {"guest"};
    options.executable_path = "guest";
    options.stack_size = 16 * 1024;

    const std::uint64_t base = options.stack_top - options.stack_size;
    assert(memory.Map(base, 4096, memory::kMemoryRead));
    process::InitialStack output;
    output.stack_pointer = 0x12345678;
    process::StackError error;
    assert(!process::InitialStackBuilder::Build(
        memory, elf::LoadedImage{}, options, &output, &error));
    assert(error.code == process::StackErrorCode::MappingFailure);
    assert(output.stack_pointer == 0x12345678);
    assert(memory.MappedBytes() == 4096);
}

void TestRejectOversizedStrings() {
    using namespace neutron;

    memory::SparseGuestMemory memory(1024 * 1024);
    process::InitialStackOptions options;
    options.arguments = {"this string is too long"};
    options.executable_path = "guest";
    options.maximum_string_bytes = 8;
    options.stack_size = 16 * 1024;

    process::InitialStack output;
    process::StackError error;
    assert(!process::InitialStackBuilder::Build(
        memory, elf::LoadedImage{}, options, &output, &error));
    assert(error.code == process::StackErrorCode::StringsTooLarge);
    assert(memory.MappedBytes() == 0);
}

} // namespace

int main() {
    TestInitialStackLayout();
    TestStackMappingConflictAndOutputPreservation();
    TestRejectOversizedStrings();
    return 0;
}
