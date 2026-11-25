#include "neutron/elf/elf_validator.hpp"

namespace neutron::elf {
namespace {

void SetError(ParseError* error, ParseErrorCode code, const char* message) {
    if (error != nullptr) {
        error->code = code;
        error->message = message;
    }
}

} // namespace

bool ValidateExecutable(const ParsedElf& elf, ParseError* error) {
    if (error != nullptr) *error = {};

    if (elf.load_segment_indices.empty()) {
        SetError(error, ParseErrorCode::MissingLoadSegment,
                 "ELF has no PT_LOAD segments");
        return false;
    }

    bool entry_is_executable = false;
    for (const std::size_t index : elf.load_segment_indices) {
        if (index >= elf.program_headers.size()) {
            SetError(error, ParseErrorCode::InvalidSegment,
                     "Internal PT_LOAD index is out of range");
            return false;
        }
        const ProgramHeader& segment = elf.program_headers[index];
        if ((segment.flags & kPfExecute) == 0 || segment.memory_size == 0) {
            continue;
        }
        const std::uint64_t end = segment.virtual_address + segment.memory_size;
        if (elf.header.entry >= segment.virtual_address && elf.header.entry < end) {
            entry_is_executable = true;
            break;
        }
    }

    if (!entry_is_executable) {
        SetError(error, ParseErrorCode::InvalidEntryPoint,
                 "ELF entry point is not inside an executable PT_LOAD segment");
        return false;
    }
    return true;
}

} // namespace neutron::elf
