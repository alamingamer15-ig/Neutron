#pragma once

#include "neutron/elf/elf_types.hpp"
#include "neutron/elf/loaded_image.hpp"
#include "neutron/memory/guest_memory.hpp"

#include <cstdint>
#include <string>
#include <vector>

namespace neutron::elf {

enum class LoadErrorCode {
    None = 0,
    InvalidInput,
    UnsupportedFileType,
    InvalidSegment,
    AddressOverflow,
    ImageTooLarge,
    MappingFailure,
    MemoryWriteFailure,
    PermissionFailure,
    InvalidEntryPoint
};

struct LoadError {
    LoadErrorCode code = LoadErrorCode::None;
    std::string message;
};

class ElfLoader final {
public:
    static constexpr std::uint64_t kGuestPageSize = 4096;

    // Loads PT_LOAD segments into guest memory. The ParsedElf must have been
    // produced by ElfParser; file_bytes must be the exact bytes that were parsed.
    // On failure, every mapping created by this call is rolled back and output
    // remains unchanged.
    static bool Load(const std::vector<std::uint8_t>& file_bytes,
                     const ParsedElf& elf,
                     memory::GuestMemory& guest_memory,
                     LoadedImage* output,
                     LoadError* error = nullptr);
};

const char* LoadErrorCodeName(LoadErrorCode code) noexcept;

} // namespace neutron::elf
