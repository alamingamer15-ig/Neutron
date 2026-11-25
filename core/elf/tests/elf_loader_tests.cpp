#include "neutron/elf/elf_loader.hpp"
#include "neutron/elf/elf_parser.hpp"
#include "neutron/memory/guest_memory.hpp"

#include <cassert>
#include <cstdint>
#include <vector>

namespace {

void Put16(std::vector<std::uint8_t>& bytes, std::size_t offset,
           std::uint16_t value) {
    bytes[offset] = static_cast<std::uint8_t>(value);
    bytes[offset + 1] = static_cast<std::uint8_t>(value >> 8);
}

void Put32(std::vector<std::uint8_t>& bytes, std::size_t offset,
           std::uint32_t value) {
    for (unsigned int i = 0; i < 4; ++i)
        bytes[offset + i] = static_cast<std::uint8_t>(value >> (i * 8));
}

void Put64(std::vector<std::uint8_t>& bytes, std::size_t offset,
           std::uint64_t value) {
    for (unsigned int i = 0; i < 8; ++i)
        bytes[offset + i] = static_cast<std::uint8_t>(value >> (i * 8));
}

struct SegmentSpec {
    std::uint64_t file_offset;
    std::uint64_t virtual_address;
    std::uint64_t file_size;
    std::uint64_t memory_size;
    std::uint32_t flags;
};

// Creates a minimal ELF64 x86-64 executable fixture. The entry code is:
//     mov eax, 60
//     xor edi, edi
//     syscall
// It is a real static Linux _exit(0) sequence, but the test only loads it;
// Neutron's x86-64 interpreter is not part of this test.
std::vector<std::uint8_t> MakeElf(
    std::uint16_t type, std::uint64_t entry,
    const std::vector<SegmentSpec>& segments,
    std::size_t file_size) {
    std::vector<std::uint8_t> bytes(file_size, 0);
    bytes[0] = 0x7f;
    bytes[1] = 'E';
    bytes[2] = 'L';
    bytes[3] = 'F';
    bytes[4] = 2; // ELFCLASS64
    bytes[5] = 1; // ELFDATA2LSB
    bytes[6] = 1; // EV_CURRENT

    Put16(bytes, 16, type);
    Put16(bytes, 18, neutron::elf::kEmX86_64);
    Put32(bytes, 20, 1);
    Put64(bytes, 24, entry);
    Put64(bytes, 32, 64); // e_phoff
    Put16(bytes, 52, 64); // e_ehsize
    Put16(bytes, 54, 56); // e_phentsize
    Put16(bytes, 56, static_cast<std::uint16_t>(segments.size()));

    for (std::size_t i = 0; i < segments.size(); ++i) {
        const SegmentSpec& segment = segments[i];
        const std::size_t ph = 64 + i * 56;
        Put32(bytes, ph, 1); // PT_LOAD
        Put32(bytes, ph + 4, segment.flags);
        Put64(bytes, ph + 8, segment.file_offset);
        Put64(bytes, ph + 16, segment.virtual_address);
        Put64(bytes, ph + 24, segment.virtual_address);
        Put64(bytes, ph + 32, segment.file_size);
        Put64(bytes, ph + 40, segment.memory_size);
        Put64(bytes, ph + 48, 4096);
    }

    // Put the tiny x86-64 exit sequence in the first load segment.
    const std::uint8_t exit_code[] = {
        0xb8, 0x3c, 0x00, 0x00, 0x00, // mov eax, 60
        0x31, 0xff,                   // xor edi, edi
        0x0f, 0x05                    // syscall
    };
    const SegmentSpec& first = segments.front();
    const std::size_t code_offset =
        static_cast<std::size_t>(first.file_offset + 0x100);
    for (std::size_t i = 0; i < sizeof(exit_code); ++i)
        bytes[code_offset + i] = exit_code[i];

    return bytes;
}

void TestStaticExecutableLoad() {
    using namespace neutron::elf;
    using namespace neutron::memory;

    const auto bytes = MakeElf(
        kEtExec, 0x400100,
        {{0, 0x400000, 0x1000, 0x2000, kPfRead | kPfExecute}},
        0x1000);

    ParsedElf parsed;
    ParseError parse_error;
    assert(ElfParser::Parse(bytes, &parsed, &parse_error));

    SparseGuestMemory memory(16 * 1024);
    LoadedImage image;
    LoadError load_error;
    assert(ElfLoader::Load(bytes, parsed, memory, &image, &load_error));

    assert(image.entry_point == 0x400100);
    assert(image.load_bias == 0);
    assert(image.image_base == 0x400000);
    assert(image.image_size == 0x2000);
    assert(image.program_header_count == 1);
    assert(image.program_header_address == 0x400040);
    assert(memory.MappedBytes() == 0x2000);

    const std::uint8_t expected[] = {
        0xb8, 0x3c, 0x00, 0x00, 0x00, 0x31, 0xff, 0x0f, 0x05
    };
    std::uint8_t actual[sizeof(expected)]{};
    assert(memory.Fetch(image.entry_point, actual, sizeof(actual)));
    for (std::size_t i = 0; i < sizeof(expected); ++i)
        assert(actual[i] == expected[i]);

    // p_memsz > p_filesz: the second page is BSS and must be zero-filled.
    std::uint8_t bss[16];
    for (std::uint8_t& byte : bss) byte = 0xff;
    assert(memory.Read(0x401000, bss, sizeof(bss)));
    for (const std::uint8_t byte : bss) assert(byte == 0);

    const std::uint8_t replacement = 0xcc;
    assert(!memory.Write(image.entry_point, &replacement, 1));
    assert(memory.Fetch(0x400000, actual, 1)); // RX segment is executable.
}

void TestPieLoadBias() {
    using namespace neutron::elf;
    using namespace neutron::memory;

    const auto bytes = MakeElf(
        kEtDyn, 0x100,
        {{0, 0, 0x1000, 0x1000, kPfRead | kPfExecute}},
        0x1000);

    ParsedElf parsed;
    ParseError parse_error;
    assert(ElfParser::Parse(bytes, &parsed, &parse_error));

    SparseGuestMemory memory(8192);
    LoadedImage image;
    LoadError load_error;
    assert(ElfLoader::Load(bytes, parsed, memory, &image, &load_error));

    assert(image.load_bias == 0x0000004000000000ULL);
    assert(image.image_base == 0x0000004000000000ULL);
    assert(image.entry_point == 0x0000004000000100ULL);
    assert(memory.MappedBytes() == 0x1000);
}

void TestRollbackOnMappingConflict() {
    using namespace neutron::elf;
    using namespace neutron::memory;

    const auto bytes = MakeElf(
        kEtExec, 0x400100,
        {
            {0, 0x400000, 0x1000, 0x1000, kPfRead | kPfExecute},
            {0x2000, 0x402000, 0x1000, 0x1000, kPfRead}
        },
        0x3000);

    ParsedElf parsed;
    ParseError parse_error;
    assert(ElfParser::Parse(bytes, &parsed, &parse_error));

    SparseGuestMemory memory(16 * 1024);
    // Occupy the second segment's address so the loader maps the first segment,
    // then encounters a conflict and must roll its own first mapping back.
    assert(memory.Map(0x402000, 0x1000, kMemoryRead));

    LoadedImage image;
    image.entry_point = 0xdeadbeef;
    LoadError load_error;
    assert(!ElfLoader::Load(bytes, parsed, memory, &image, &load_error));
    assert(load_error.code == LoadErrorCode::MappingFailure);
    assert(image.entry_point == 0xdeadbeef);
    assert(memory.MappedBytes() == 0x1000);

    std::uint8_t byte = 0;
    assert(!memory.Read(0x400000, &byte, 1));
    assert(memory.Read(0x402000, &byte, 1));
}

void TestRejectWritableExecutablePage() {
    using namespace neutron::elf;
    using namespace neutron::memory;

    const auto bytes = MakeElf(
        kEtExec, 0x400100,
        {{0, 0x400000, 0x1000, 0x1000,
          kPfRead | kPfWrite | kPfExecute}},
        0x1000);

    ParsedElf parsed;
    ParseError parse_error;
    assert(ElfParser::Parse(bytes, &parsed, &parse_error));

    SparseGuestMemory memory(8192);
    LoadedImage image;
    LoadError load_error;
    assert(!ElfLoader::Load(bytes, parsed, memory, &image, &load_error));
    assert(load_error.code == LoadErrorCode::PermissionFailure);
    assert(memory.MappedBytes() == 0);
}

} // namespace

int main() {
    TestStaticExecutableLoad();
    TestPieLoadBias();
    TestRollbackOnMappingConflict();
    TestRejectWritableExecutablePage();
    return 0;
}
