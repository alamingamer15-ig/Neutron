#include "neutron/elf/elf_parser.hpp"
#include "neutron/elf/elf_validator.hpp"

#include <limits>
#include <utility>

namespace neutron::elf {
namespace {

constexpr std::size_t kElfHeaderSize = 64;
constexpr std::size_t kProgramHeaderSize = 56;
constexpr std::uint16_t kElfClass64 = 2;
constexpr std::uint8_t kElfDataLittleEndian = 1;
constexpr std::uint8_t kElfVersionCurrent = 1;
constexpr std::size_t kMaximumProgramHeaders = 4096;
constexpr std::uint64_t kMaximumInterpreterSize = 4096;

bool RangeFits(std::uint64_t offset, std::uint64_t size, std::size_t total) {
    return offset <= total && size <= static_cast<std::uint64_t>(total) - offset;
}

bool ReadU16(const std::vector<std::uint8_t>& data, std::size_t offset,
             std::uint16_t* value) {
    if (value == nullptr || !RangeFits(offset, 2, data.size())) return false;
    *value = static_cast<std::uint16_t>(data[offset]) |
             (static_cast<std::uint16_t>(data[offset + 1]) << 8);
    return true;
}

bool ReadU32(const std::vector<std::uint8_t>& data, std::size_t offset,
             std::uint32_t* value) {
    if (value == nullptr || !RangeFits(offset, 4, data.size())) return false;
    *value = static_cast<std::uint32_t>(data[offset]) |
             (static_cast<std::uint32_t>(data[offset + 1]) << 8) |
             (static_cast<std::uint32_t>(data[offset + 2]) << 16) |
             (static_cast<std::uint32_t>(data[offset + 3]) << 24);
    return true;
}

bool ReadU64(const std::vector<std::uint8_t>& data, std::size_t offset,
             std::uint64_t* value) {
    if (value == nullptr || !RangeFits(offset, 8, data.size())) return false;
    std::uint64_t result = 0;
    for (unsigned int i = 0; i < 8; ++i) {
        result |= static_cast<std::uint64_t>(data[offset + i]) << (i * 8);
    }
    *value = result;
    return true;
}

void SetError(ParseError* error, ParseErrorCode code, const char* message) {
    if (error != nullptr) {
        error->code = code;
        error->message = message;
    }
}

bool ParseHeader(const std::vector<std::uint8_t>& bytes, Elf64Header* header,
                 ParseError* error) {
    if (bytes.size() < kElfHeaderSize) {
        SetError(error, ParseErrorCode::FileTooSmall, "ELF file is smaller than the 64-byte ELF64 header");
        return false;
    }
    if (bytes[0] != 0x7f || bytes[1] != 'E' || bytes[2] != 'L' || bytes[3] != 'F') {
        SetError(error, ParseErrorCode::BadMagic, "ELF magic is missing");
        return false;
    }
    if (bytes[4] != kElfClass64) {
        SetError(error, ParseErrorCode::UnsupportedClass, "Only ELFCLASS64 is supported");
        return false;
    }
    if (bytes[5] != kElfDataLittleEndian) {
        SetError(error, ParseErrorCode::UnsupportedByteOrder, "Only little-endian ELF files are supported");
        return false;
    }
    if (bytes[6] != kElfVersionCurrent) {
        SetError(error, ParseErrorCode::UnsupportedIdentificationVersion, "Unsupported ELF identification version");
        return false;
    }

    if (!ReadU16(bytes, 16, &header->type) ||
        !ReadU16(bytes, 18, &header->machine) ||
        !ReadU32(bytes, 20, &header->version) ||
        !ReadU64(bytes, 24, &header->entry) ||
        !ReadU64(bytes, 32, &header->program_header_offset) ||
        !ReadU64(bytes, 40, &header->section_header_offset) ||
        !ReadU32(bytes, 48, &header->flags) ||
        !ReadU16(bytes, 52, &header->header_size) ||
        !ReadU16(bytes, 54, &header->program_header_entry_size) ||
        !ReadU16(bytes, 56, &header->program_header_count) ||
        !ReadU16(bytes, 58, &header->section_header_entry_size) ||
        !ReadU16(bytes, 60, &header->section_header_count) ||
        !ReadU16(bytes, 62, &header->section_name_string_table_index)) {
        SetError(error, ParseErrorCode::InvalidHeader, "Could not read ELF header fields");
        return false;
    }

    if (header->type != kEtExec && header->type != kEtDyn) {
        SetError(error, ParseErrorCode::UnsupportedFileType, "Only ET_EXEC and ET_DYN ELF files are supported");
        return false;
    }
    if (header->machine != kEmX86_64) {
        SetError(error, ParseErrorCode::UnsupportedMachine, "ELF machine is not x86-64 (EM_X86_64)");
        return false;
    }
    if (header->version != kElfVersionCurrent || header->header_size != kElfHeaderSize) {
        SetError(error, ParseErrorCode::InvalidHeader, "Invalid ELF version or ELF header size");
        return false;
    }
    if (header->program_header_count == 0 ||
        header->program_header_count > kMaximumProgramHeaders ||
        header->program_header_entry_size != kProgramHeaderSize) {
        SetError(error, ParseErrorCode::InvalidProgramHeaderTable, "Invalid or unsupported program-header table dimensions");
        return false;
    }

    const std::uint64_t table_size =
        static_cast<std::uint64_t>(header->program_header_count) *
        header->program_header_entry_size;
    if (!RangeFits(header->program_header_offset, table_size, bytes.size())) {
        SetError(error, ParseErrorCode::InvalidProgramHeaderTable, "Program-header table extends beyond the file");
        return false;
    }
    return true;
}

bool ParseProgramHeaders(const std::vector<std::uint8_t>& bytes,
                         ParsedElf* parsed, ParseError* error) {
    for (std::uint16_t i = 0; i < parsed->header.program_header_count; ++i) {
        const std::uint64_t raw_offset = parsed->header.program_header_offset +
            static_cast<std::uint64_t>(i) * parsed->header.program_header_entry_size;
        const std::size_t offset = static_cast<std::size_t>(raw_offset);
        ProgramHeader ph;
        if (!ReadU32(bytes, offset, &ph.type) ||
            !ReadU32(bytes, offset + 4, &ph.flags) ||
            !ReadU64(bytes, offset + 8, &ph.offset) ||
            !ReadU64(bytes, offset + 16, &ph.virtual_address) ||
            !ReadU64(bytes, offset + 24, &ph.physical_address) ||
            !ReadU64(bytes, offset + 32, &ph.file_size) ||
            !ReadU64(bytes, offset + 40, &ph.memory_size) ||
            !ReadU64(bytes, offset + 48, &ph.alignment)) {
            SetError(error, ParseErrorCode::InvalidProgramHeaderTable, "Could not read a program header");
            return false;
        }

        if (ph.type == kPtLoad) {
            if (ph.file_size > ph.memory_size ||
                !RangeFits(ph.offset, ph.file_size, bytes.size()) ||
                ph.virtual_address > std::numeric_limits<std::uint64_t>::max() - ph.memory_size) {
                SetError(error, ParseErrorCode::InvalidSegment, "PT_LOAD has invalid file or virtual-memory bounds");
                return false;
            }
            if (ph.alignment > 1 &&
                ((ph.alignment & (ph.alignment - 1)) != 0 ||
                 (ph.virtual_address % ph.alignment) != (ph.offset % ph.alignment))) {
                SetError(error, ParseErrorCode::InvalidAlignment, "PT_LOAD alignment is invalid");
                return false;
            }
            parsed->load_segment_indices.push_back(parsed->program_headers.size());
        } else if (ph.type == kPtInterp) {
            if (!parsed->interpreter.empty()) {
                SetError(error, ParseErrorCode::MultipleInterpreters, "ELF contains more than one PT_INTERP segment");
                return false;
            }
            if (ph.file_size < 2 || ph.file_size > kMaximumInterpreterSize ||
                !RangeFits(ph.offset, ph.file_size, bytes.size())) {
                SetError(error, ParseErrorCode::InvalidInterpreter, "PT_INTERP has an invalid size or file range");
                return false;
            }
            const std::size_t begin = static_cast<std::size_t>(ph.offset);
            const std::size_t length = static_cast<std::size_t>(ph.file_size);
            if (bytes[begin + length - 1] != 0) {
                SetError(error, ParseErrorCode::InvalidInterpreter, "PT_INTERP is not null-terminated");
                return false;
            }
            parsed->interpreter.assign(
                reinterpret_cast<const char*>(bytes.data() + begin), length - 1);
            if (parsed->interpreter.empty()) {
                SetError(error, ParseErrorCode::InvalidInterpreter, "PT_INTERP path is empty");
                return false;
            }
        } else if (ph.file_size != 0 && !RangeFits(ph.offset, ph.file_size, bytes.size())) {
            SetError(error, ParseErrorCode::InvalidSegment, "Program segment extends beyond the file");
            return false;
        }

        parsed->program_headers.push_back(ph);
    }
    return true;
}

} // namespace

bool ElfParser::Parse(const std::vector<std::uint8_t>& bytes,
                      ParsedElf* output, ParseError* error) {
    if (error != nullptr) *error = {};
    if (output == nullptr) {
        SetError(error, ParseErrorCode::NullOutput, "Output pointer is null");
        return false;
    }

    ParsedElf parsed;
    if (!ParseHeader(bytes, &parsed.header, error) ||
        !ParseProgramHeaders(bytes, &parsed, error) ||
        !ValidateExecutable(parsed, error)) {
        return false;
    }

    *output = std::move(parsed);
    return true;
}

const char* ParseErrorCodeName(ParseErrorCode code) noexcept {
    switch (code) {
        case ParseErrorCode::None: return "none";
        case ParseErrorCode::NullOutput: return "null_output";
        case ParseErrorCode::FileTooSmall: return "file_too_small";
        case ParseErrorCode::BadMagic: return "bad_magic";
        case ParseErrorCode::UnsupportedClass: return "unsupported_class";
        case ParseErrorCode::UnsupportedByteOrder: return "unsupported_byte_order";
        case ParseErrorCode::UnsupportedIdentificationVersion: return "unsupported_identification_version";
        case ParseErrorCode::UnsupportedFileType: return "unsupported_file_type";
        case ParseErrorCode::UnsupportedMachine: return "unsupported_machine";
        case ParseErrorCode::InvalidHeader: return "invalid_header";
        case ParseErrorCode::InvalidProgramHeaderTable: return "invalid_program_header_table";
        case ParseErrorCode::TooManyProgramHeaders: return "too_many_program_headers";
        case ParseErrorCode::InvalidSegment: return "invalid_segment";
        case ParseErrorCode::InvalidAlignment: return "invalid_alignment";
        case ParseErrorCode::MultipleInterpreters: return "multiple_interpreters";
        case ParseErrorCode::InvalidInterpreter: return "invalid_interpreter";
        case ParseErrorCode::MissingLoadSegment: return "missing_load_segment";
        case ParseErrorCode::InvalidEntryPoint: return "invalid_entry_point";
    }
    return "unknown";
}

} // namespace neutron::elf
