#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace neutron::elf {

constexpr std::uint16_t kEtExec = 2;
constexpr std::uint16_t kEtDyn = 3;
constexpr std::uint16_t kEmX86_64 = 62;

constexpr std::uint32_t kPtNull = 0;
constexpr std::uint32_t kPtLoad = 1;
constexpr std::uint32_t kPtDynamic = 2;
constexpr std::uint32_t kPtInterp = 3;
constexpr std::uint32_t kPtNote = 4;
constexpr std::uint32_t kPtPhdr = 6;
constexpr std::uint32_t kPtTls = 7;

constexpr std::uint32_t kPfExecute = 1;
constexpr std::uint32_t kPfWrite = 2;
constexpr std::uint32_t kPfRead = 4;

struct ProgramHeader {
    std::uint32_t type = 0;
    std::uint32_t flags = 0;
    std::uint64_t offset = 0;
    std::uint64_t virtual_address = 0;
    std::uint64_t physical_address = 0;
    std::uint64_t file_size = 0;
    std::uint64_t memory_size = 0;
    std::uint64_t alignment = 0;
};

struct Elf64Header {
    std::uint16_t type = 0;
    std::uint16_t machine = 0;
    std::uint32_t version = 0;
    std::uint64_t entry = 0;
    std::uint64_t program_header_offset = 0;
    std::uint64_t section_header_offset = 0;
    std::uint32_t flags = 0;
    std::uint16_t header_size = 0;
    std::uint16_t program_header_entry_size = 0;
    std::uint16_t program_header_count = 0;
    std::uint16_t section_header_entry_size = 0;
    std::uint16_t section_header_count = 0;
    std::uint16_t section_name_string_table_index = 0;
};

struct ParsedElf {
    Elf64Header header;
    std::vector<ProgramHeader> program_headers;
    std::vector<std::size_t> load_segment_indices;
    std::string interpreter;
};

} // namespace neutron::elf
