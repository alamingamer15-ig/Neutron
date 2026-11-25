#pragma once

#include "neutron/elf/elf_types.hpp"

#include <cstdint>
#include <string>
#include <vector>

namespace neutron::elf {

enum class ParseErrorCode {
    None = 0,
    NullOutput,
    FileTooSmall,
    BadMagic,
    UnsupportedClass,
    UnsupportedByteOrder,
    UnsupportedIdentificationVersion,
    UnsupportedFileType,
    UnsupportedMachine,
    InvalidHeader,
    InvalidProgramHeaderTable,
    TooManyProgramHeaders,
    InvalidSegment,
    InvalidAlignment,
    MultipleInterpreters,
    InvalidInterpreter,
    MissingLoadSegment,
    InvalidEntryPoint
};

struct ParseError {
    ParseErrorCode code = ParseErrorCode::None;
    std::string message;
};

class ElfParser final {
public:
    // Parses an x86-64, little-endian ELF64 executable from an in-memory file.
    // The input is never modified. On failure, output is left unchanged.
    static bool Parse(
        const std::vector<std::uint8_t>& bytes,
        ParsedElf* output,
        ParseError* error = nullptr
    );
};

const char* ParseErrorCodeName(ParseErrorCode code) noexcept;

} // namespace neutron::elf
