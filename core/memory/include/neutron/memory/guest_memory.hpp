#pragma once

#include <cstddef>
#include <cstdint>

namespace neutron::memory {

enum MemoryPermission : std::uint32_t {
    kMemoryNone = 0,
    kMemoryRead = 1u << 0,
    kMemoryWrite = 1u << 1,
    kMemoryExecute = 1u << 2
};

// Guest-address-space abstraction. Implementations must reject conflicting
// mappings and require page-aligned Map/Protect/Unmap ranges. Write and Zero
// are loader-facing operations and may initialize read-only guest pages.
class GuestMemory {
public:
    virtual ~GuestMemory() = default;

    virtual bool Map(std::uint64_t guest_address, std::uint64_t size,
                     std::uint32_t permissions) = 0;
    virtual bool Write(std::uint64_t guest_address, const void* data,
                       std::size_t size) = 0;
    virtual bool Zero(std::uint64_t guest_address, std::uint64_t size) = 0;
    virtual bool Protect(std::uint64_t guest_address, std::uint64_t size,
                         std::uint32_t permissions) = 0;
    virtual void Unmap(std::uint64_t guest_address,
                       std::uint64_t size) noexcept = 0;
};

} // namespace neutron::memory
