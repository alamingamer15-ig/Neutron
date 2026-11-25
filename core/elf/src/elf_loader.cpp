#include "neutron/elf/elf_loader.hpp"

#include <algorithm>
#include <iterator>
#include <limits>
#include <map>
#include <utility>
#include <vector>

namespace neutron::elf {
namespace {

constexpr std::uint64_t kPageMask = ElfLoader::kGuestPageSize - 1;
constexpr std::uint64_t kMaximumUserAddress = 0x00007fffffffffffULL;
constexpr std::uint64_t kMaximumImageSize = 1024ULL * 1024ULL * 1024ULL;
constexpr std::uint64_t kPiePreferredBase = 0x0000004000000000ULL;

struct Mapping {
    std::uint64_t address;
    std::uint64_t size;
    std::uint32_t permissions;
};

void SetError(LoadError* error, LoadErrorCode code, const char* message) {
    if (error != nullptr) {
        error->code = code;
        error->message = message;
    }
}

bool AddOverflows(std::uint64_t left, std::uint64_t right) {
    return right > std::numeric_limits<std::uint64_t>::max() - left;
}

bool AlignUp(std::uint64_t value, std::uint64_t* result) {
    if (result == nullptr || AddOverflows(value, kPageMask)) return false;
    *result = (value + kPageMask) & ~kPageMask;
    return true;
}

std::uint32_t ToMemoryPermissions(std::uint32_t flags) {
    std::uint32_t result = memory::kMemoryNone;
    if ((flags & kPfRead) != 0) result |= memory::kMemoryRead;
    if ((flags & kPfWrite) != 0) result |= memory::kMemoryWrite;
    if ((flags & kPfExecute) != 0) result |= memory::kMemoryExecute;
    return result;
}

void RollBack(memory::GuestMemory& guest_memory,
              const std::vector<Mapping>& mappings) noexcept {
    for (auto it = mappings.rbegin(); it != mappings.rend(); ++it) {
        guest_memory.Unmap(it->address, it->size);
    }
}

} // namespace

bool ElfLoader::Load(const std::vector<std::uint8_t>& file_bytes,
                     const ParsedElf& elf,
                     memory::GuestMemory& guest_memory,
                     LoadedImage* output,
                     LoadError* error) {
    if (error != nullptr) *error = {};
    if (output == nullptr || file_bytes.empty() || elf.load_segment_indices.empty()) {
        SetError(error, LoadErrorCode::InvalidInput,
                 "Output, ELF bytes, or PT_LOAD segment list is invalid");
        return false;
    }
    if (elf.header.type != kEtExec && elf.header.type != kEtDyn) {
        SetError(error, LoadErrorCode::UnsupportedFileType,
                 "Only ET_EXEC and ET_DYN images can be loaded");
        return false;
    }

    std::uint64_t minimum_page = std::numeric_limits<std::uint64_t>::max();
    std::uint64_t maximum_page = 0;
    for (const std::size_t index : elf.load_segment_indices) {
        if (index >= elf.program_headers.size()) {
            SetError(error, LoadErrorCode::InvalidSegment,
                     "PT_LOAD index is outside the program-header array");
            return false;
        }
        const ProgramHeader& segment = elf.program_headers[index];
        if (segment.file_size > segment.memory_size ||
            segment.offset > file_bytes.size() ||
            segment.file_size > file_bytes.size() - segment.offset ||
            AddOverflows(segment.virtual_address, segment.memory_size)) {
            SetError(error, LoadErrorCode::InvalidSegment,
                     "PT_LOAD file or virtual-memory range is invalid");
            return false;
        }
        if (segment.memory_size == 0) continue;

        const std::uint64_t start = segment.virtual_address & ~kPageMask;
        std::uint64_t end = 0;
        if (!AlignUp(segment.virtual_address + segment.memory_size, &end)) {
            SetError(error, LoadErrorCode::AddressOverflow,
                     "PT_LOAD page range overflows");
            return false;
        }
        minimum_page = std::min(minimum_page, start);
        maximum_page = std::max(maximum_page, end);
    }

    if (minimum_page == std::numeric_limits<std::uint64_t>::max() ||
        maximum_page <= minimum_page || maximum_page - minimum_page > kMaximumImageSize) {
        SetError(error, LoadErrorCode::ImageTooLarge,
                 "ELF image is empty or exceeds the 1 GiB loader limit");
        return false;
    }

    std::uint64_t load_bias = 0;
    if (elf.header.type == kEtDyn) {
        if (minimum_page > kPiePreferredBase) {
            SetError(error, LoadErrorCode::AddressOverflow,
                     "ET_DYN image cannot fit at the loader's current PIE base");
            return false;
        }
        load_bias = kPiePreferredBase - minimum_page;
    }

    if (AddOverflows(load_bias, maximum_page) ||
        load_bias + maximum_page - 1 > kMaximumUserAddress) {
        SetError(error, LoadErrorCode::AddressOverflow,
                 "Loaded image exceeds the supported x86-64 user address range");
        return false;
    }

    // Build a page map first. Adjacent PT_LOAD segments may share a page;
    // mapping each segment independently would incorrectly collide.
    std::map<std::uint64_t, std::uint32_t> page_permissions;
    for (const std::size_t index : elf.load_segment_indices) {
        const ProgramHeader& segment = elf.program_headers[index];
        if (segment.memory_size == 0) continue;
        const std::uint64_t start = (load_bias + segment.virtual_address) & ~kPageMask;
        std::uint64_t end = 0;
        if (!AlignUp(load_bias + segment.virtual_address + segment.memory_size, &end)) {
            SetError(error, LoadErrorCode::AddressOverflow,
                     "Loaded PT_LOAD page range overflows");
            return false;
        }
        const std::uint32_t permissions = ToMemoryPermissions(segment.flags);
        for (std::uint64_t page = start; page < end; page += kGuestPageSize) {
            page_permissions[page] |= permissions;
        }
    }

    std::vector<Mapping> mappings;
    for (auto it = page_permissions.begin(); it != page_permissions.end();) {
        const std::uint64_t start = it->first;
        const std::uint32_t permissions = it->second;
        std::uint64_t end = start + kGuestPageSize;
        auto next = std::next(it);
        while (next != page_permissions.end() &&
               next->first == end && next->second == permissions) {
            end += kGuestPageSize;
            ++next;
        }
        const Mapping mapping{start, end - start, permissions};
        // Keep pages writable while the loader initializes file data and BSS.
        // The ELF's exact permissions are installed in the Protect pass below.
        constexpr std::uint32_t kLoaderWritePermissions =
            memory::kMemoryRead | memory::kMemoryWrite;
        if (!guest_memory.Map(mapping.address, mapping.size, kLoaderWritePermissions)) {
            RollBack(guest_memory, mappings);
            SetError(error, LoadErrorCode::MappingFailure,
                     "Guest memory rejected a PT_LOAD mapping");
            return false;
        }
        mappings.push_back(mapping);
        it = next;
    }

    for (const std::size_t index : elf.load_segment_indices) {
        const ProgramHeader& segment = elf.program_headers[index];
        if (segment.memory_size == 0) continue;
        const std::uint64_t destination = load_bias + segment.virtual_address;
        if (segment.file_size != 0 &&
            !guest_memory.Write(destination,
                                file_bytes.data() + static_cast<std::size_t>(segment.offset),
                                static_cast<std::size_t>(segment.file_size))) {
            RollBack(guest_memory, mappings);
            SetError(error, LoadErrorCode::MemoryWriteFailure,
                     "Could not copy PT_LOAD file contents into guest memory");
            return false;
        }
        const std::uint64_t zero_size = segment.memory_size - segment.file_size;
        if (zero_size != 0 &&
            !guest_memory.Zero(destination + segment.file_size, zero_size)) {
            RollBack(guest_memory, mappings);
            SetError(error, LoadErrorCode::MemoryWriteFailure,
                     "Could not zero the PT_LOAD BSS region");
            return false;
        }
    }

    LoadedImage loaded;
    loaded.load_bias = load_bias;
    loaded.entry_point = load_bias + elf.header.entry;
    loaded.image_base = load_bias + minimum_page;
    loaded.image_size = maximum_page - minimum_page;
    loaded.program_header_count = elf.header.program_header_count;
    loaded.interpreter_path = elf.interpreter;

    // Prefer PT_PHDR; otherwise derive the in-memory address from the load
    // segment containing the complete program-header table.
    for (const ProgramHeader& ph : elf.program_headers) {
        if (ph.type == kPtPhdr) {
            if (AddOverflows(load_bias, ph.virtual_address)) {
                RollBack(guest_memory, mappings);
                SetError(error, LoadErrorCode::AddressOverflow,
                         "PT_PHDR address overflows");
                return false;
            }
            loaded.program_header_address = load_bias + ph.virtual_address;
            break;
        }
    }
    if (loaded.program_header_address == 0) {
        const std::uint64_t table_size =
            static_cast<std::uint64_t>(elf.header.program_header_count) *
            elf.header.program_header_entry_size;
        for (const std::size_t index : elf.load_segment_indices) {
            const ProgramHeader& ph = elf.program_headers[index];
            if (elf.header.program_header_offset >= ph.offset &&
                table_size <= ph.file_size &&
                elf.header.program_header_offset - ph.offset <= ph.file_size - table_size) {
                const std::uint64_t delta = elf.header.program_header_offset - ph.offset;
                const std::uint64_t phdr_base = load_bias + ph.virtual_address;
                if (AddOverflows(phdr_base, delta)) break;
                loaded.program_header_address = phdr_base + delta;
                break;
            }
        }
    }

    bool entry_is_executable = false;
    for (const std::size_t index : elf.load_segment_indices) {
        const ProgramHeader& ph = elf.program_headers[index];
        if ((ph.flags & kPfExecute) == 0 || ph.memory_size == 0) continue;
        const std::uint64_t begin = load_bias + ph.virtual_address;
        const std::uint64_t end = begin + ph.memory_size;
        if (loaded.entry_point >= begin && loaded.entry_point < end) {
            entry_is_executable = true;
            break;
        }
    }
    if (!entry_is_executable) {
        RollBack(guest_memory, mappings);
        SetError(error, LoadErrorCode::InvalidEntryPoint,
                 "Loaded entry point is not inside an executable PT_LOAD segment");
        return false;
    }

    // Final permissions are applied only after all segment bytes are copied.
    for (const Mapping& mapping : mappings) {
        if (!guest_memory.Protect(mapping.address, mapping.size, mapping.permissions)) {
            RollBack(guest_memory, mappings);
            SetError(error, LoadErrorCode::PermissionFailure,
                     "Could not apply final guest page permissions");
            return false;
        }
    }

    *output = std::move(loaded);
    return true;
}

const char* LoadErrorCodeName(LoadErrorCode code) noexcept {
    switch (code) {
        case LoadErrorCode::None: return "none";
        case LoadErrorCode::InvalidInput: return "invalid_input";
        case LoadErrorCode::UnsupportedFileType: return "unsupported_file_type";
        case LoadErrorCode::InvalidSegment: return "invalid_segment";
        case LoadErrorCode::AddressOverflow: return "address_overflow";
        case LoadErrorCode::ImageTooLarge: return "image_too_large";
        case LoadErrorCode::MappingFailure: return "mapping_failure";
        case LoadErrorCode::MemoryWriteFailure: return "memory_write_failure";
        case LoadErrorCode::PermissionFailure: return "permission_failure";
        case LoadErrorCode::InvalidEntryPoint: return "invalid_entry_point";
    }
    return "unknown";
}

} // namespace neutron::elf
