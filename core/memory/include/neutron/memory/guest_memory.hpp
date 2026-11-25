#pragma once

#include <cstddef>
#include <cstdint>
#include <map>
#include <memory>
#include <mutex>

namespace neutron::memory {

enum MemoryPermission : std::uint32_t {
    kMemoryNone = 0,
    kMemoryRead = 1u << 0,
    kMemoryWrite = 1u << 1,
    kMemoryExecute = 1u << 2
};

// Guest-address-space abstraction. Implementations require page-aligned
// Map/Protect/Unmap ranges. Read and Write enforce guest page permissions.
class GuestMemory {
public:
    virtual ~GuestMemory() = default;

    virtual bool Map(std::uint64_t guest_address, std::uint64_t size,
                     std::uint32_t permissions) = 0;
    virtual bool Read(std::uint64_t guest_address, void* destination,
                      std::size_t size) const = 0;
    virtual bool Fetch(std::uint64_t guest_address, void* destination,
                       std::size_t size) const = 0;
    virtual bool Write(std::uint64_t guest_address, const void* data,
                       std::size_t size) = 0;
    virtual bool Zero(std::uint64_t guest_address, std::uint64_t size) = 0;
    virtual bool Protect(std::uint64_t guest_address, std::uint64_t size,
                         std::uint32_t permissions) = 0;
    virtual void Unmap(std::uint64_t guest_address,
                       std::uint64_t size) noexcept = 0;
};

// Sparse page-backed implementation. Guest virtual addresses are keys, not
// host pointers, so high x86-64 addresses do not require a huge host allocation.
class SparseGuestMemory final : public GuestMemory {
public:
    static constexpr std::uint64_t kPageSize = 4096;

    explicit SparseGuestMemory(std::uint64_t maximum_mapped_bytes =
                                   1024ULL * 1024ULL * 1024ULL);

    bool Map(std::uint64_t guest_address, std::uint64_t size,
             std::uint32_t permissions) override;
    bool Read(std::uint64_t guest_address, void* destination,
              std::size_t size) const override;
    bool Fetch(std::uint64_t guest_address, void* destination,
               std::size_t size) const override;
    bool Write(std::uint64_t guest_address, const void* data,
               std::size_t size) override;
    bool Zero(std::uint64_t guest_address, std::uint64_t size) override;
    bool Protect(std::uint64_t guest_address, std::uint64_t size,
                 std::uint32_t permissions) override;
    void Unmap(std::uint64_t guest_address,
               std::uint64_t size) noexcept override;

    std::uint64_t MappedBytes() const noexcept;

private:
    struct Page {
        std::unique_ptr<std::uint8_t[]> bytes;
        std::uint32_t permissions = kMemoryNone;
    };

    static bool IsValidRange(std::uint64_t address, std::uint64_t size) noexcept;
    static bool IsValidAccessRange(std::uint64_t address, std::uint64_t size) noexcept;
    bool ReadWithPermission(std::uint64_t guest_address, void* destination,
                            std::size_t size, std::uint32_t permission) const;

    const std::uint64_t maximum_mapped_bytes_;
    mutable std::mutex mutex_;
    std::map<std::uint64_t, Page> pages_;
    std::uint64_t mapped_bytes_ = 0;
};

} // namespace neutron::memory
