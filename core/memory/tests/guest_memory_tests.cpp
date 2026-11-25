#include "neutron/memory/guest_memory.hpp"

#include <array>
#include <cassert>
#include <cstdint>

using neutron::memory::kMemoryExecute;
using neutron::memory::kMemoryRead;
using neutron::memory::kMemoryWrite;
using neutron::memory::SparseGuestMemory;

int main() {
    SparseGuestMemory memory(8192);
    assert(memory.Map(0x1000, 8192, kMemoryRead | kMemoryWrite));
    assert(memory.MappedBytes() == 8192);

    const std::array<std::uint8_t, 8> input{
        0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80
    };
    std::array<std::uint8_t, 8> output{};
    assert(memory.Write(0x1ffc, input.data(), input.size()));
    assert(memory.Read(0x1ffc, output.data(), output.size()));
    assert(output == input);

    assert(memory.Zero(0x1ffc, input.size()));
    output.fill(0xff);
    assert(memory.Read(0x1ffc, output.data(), output.size()));
    for (const std::uint8_t value : output) assert(value == 0);

    // A mapping collision must not replace existing guest pages.
    assert(!memory.Map(0x2000, 4096, kMemoryRead));

    // Read-only pages reject guest writes; executable pages allow instruction fetch.
    assert(memory.Protect(0x1000, 4096, kMemoryRead));
    assert(memory.Protect(0x2000, 4096, kMemoryRead | kMemoryExecute));
    const std::uint8_t byte = 0x90;
    assert(!memory.Write(0x1000, &byte, 1));
    assert(!memory.Fetch(0x1000, output.data(), 1));
    assert(memory.Fetch(0x2000, output.data(), 1));

    memory.Unmap(0x1000, 8192);
    assert(memory.MappedBytes() == 0);
    assert(!memory.Read(0x1000, output.data(), 1));

    // Unknown permission bits must never enter the guest page table.
    assert(!memory.Map(0x8000, 4096, 0x80000000u));
    assert(memory.MappedBytes() == 0);

    SparseGuestMemory limited(4096);
    assert(!limited.Map(0x4000, 8192, kMemoryRead | kMemoryWrite));
    assert(limited.MappedBytes() == 0);
    assert(limited.Map(0x4000, 4096, kMemoryRead | kMemoryWrite));
    assert(limited.MappedBytes() == 4096);
    return 0;
}
