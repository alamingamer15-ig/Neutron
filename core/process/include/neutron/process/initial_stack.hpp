#pragma once

#include "neutron/elf/loaded_image.hpp"
#include "neutron/memory/guest_memory.hpp"

#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace neutron::process {

struct InitialStackOptions {
    std::vector<std::string> arguments;
    std::vector<std::string> environment;
    std::string executable_path;
    std::uint64_t stack_top = 0x00007ffffff00000ULL;
    std::uint64_t stack_size = 8ULL * 1024ULL * 1024ULL;
    std::uint64_t page_size = 4096;
    std::uint64_t interpreter_base = 0;
    std::size_t maximum_string_bytes = 2ULL * 1024ULL * 1024ULL;
};

struct InitialStack {
    std::uint64_t stack_pointer = 0;
    std::uint64_t stack_base = 0;
    std::uint64_t stack_size = 0;
    std::uint64_t argument_count = 0;
};

enum class StackErrorCode {
    None = 0,
    InvalidInput,
    InvalidStackRange,
    TooManyStrings,
    StringsTooLarge,
    StackOverflow,
    MappingFailure,
    MemoryWriteFailure
};

struct StackError {
    StackErrorCode code = StackErrorCode::None;
    std::string message;
};

class InitialStackBuilder final {
public:
    // Builds the Linux x86-64 process-entry stack (argc, argv, envp, auxv).
    // The page immediately below the stack is deliberately left unmapped.
    // On failure, newly mapped stack pages are rolled back and output is unchanged.
    static bool Build(memory::GuestMemory& memory,
                      const elf::LoadedImage& image,
                      const InitialStackOptions& options,
                      InitialStack* output,
                      StackError* error = nullptr);
};

const char* StackErrorCodeName(StackErrorCode code) noexcept;

} // namespace neutron::process
