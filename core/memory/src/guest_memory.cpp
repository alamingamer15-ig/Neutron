#include "neutron/memory/guest_memory.hpp"

#include <cstring>
#include <limits>
#include <new>
#include <vector>

namespace neutron::memory {
namespace {

constexpr std::uint32_t kKnownPermissions =
    kMemoryRead | kMemoryWrite | kMemoryExecute;
constexpr std::uint64_t kPageMask = SparseGuestMemory::kPageSize - 1;

} // namespace

SparseGuestMemory::SparseGuestMemory(std::uint64_t maximum_mapped_bytes)
    : maximum_mapped_bytes_(maximum_mapped_bytes) {}

bool SparseGuestMemory::IsValidRange(std::uint64_t address,
                                     std::uint64_t size) noexcept {
    return size != 0 &&
           (address & kPageMask) == 0 &&
           (size & kPageMask) == 0 &&
           size <= std::numeric_limits<std::uint64_t>::max() - address;
}

bool SparseGuestMemory::Map(std::uint64_t guest_address, std::uint64_t size,
                            std::uint32_t permissions) {
    if (!IsValidRange(guest_address, size) ||
        (permissions & ~kKnownPermissions) != 0) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    if (size > maximum_mapped_bytes_ - mapped_bytes_) return false;

    const std::uint64_t page_count = size / kPageSize;
    for (std::uint64_t i = 0; i < page_count; ++i) {
        const std::uint64_t address = guest_address + i * kPageSize;
        if (pages_.find(address) != pages_.end()) return false;
    }

    std::vector<std::uint64_t> inserted;
    try {
        inserted.reserve(static_cast<std::size_t>(page_count));
        for (std::uint64_t i = 0; i < page_count; ++i) {
            const std::uint64_t address = guest_address + i * kPageSize;
            std::unique_ptr<std::uint8_t[]> bytes(
                new (std::nothrow) std::uint8_t[kPageSize]());
            if (!bytes) {
                for (const std::uint64_t mapped : inserted) pages_.erase(mapped);
                return false;
            }

            Page page;
            page.bytes = std::move(bytes);
            page.permissions = permissions;
            pages_.emplace(address, std::move(page));
            inserted.push_back(address);
        }
    } catch (...) {
        for (const std::uint64_t mapped : inserted) pages_.erase(mapped);
        return false;
    }

    mapped_bytes_ += size;
    return true;
}

bool SparseGuestMemory::Read(std::uint64_t guest_address, void* destination,
                             std::size_t size) const {
    if (size == 0) return true;
    if (destination == nullptr ||
        size > std::numeric_limits<std::uint64_t>::max() - guest_address) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    const std::uint64_t end = guest_address + size;
    for (std::uint64_t cursor = guest_address; cursor < end;) {
        const std::uint64_t page_address = cursor & ~kPageMask;
        const auto page = pages_.find(page_address);
        if (page == pages_.end() ||
            (page->second.permissions & kMemoryRead) == 0) {
            return false;
        }
        const std::uint64_t page_end = page_address + kPageSize;
        cursor = page_end < end ? page_end : end;
    }

    auto* output = static_cast<std::uint8_t*>(destination);
    std::uint64_t cursor = guest_address;
    std::size_t copied = 0;
    while (copied < size) {
        const std::uint64_t page_address = cursor & ~kPageMask;
        const std::size_t offset = static_cast<std::size_t>(cursor & kPageMask);
        const std::size_t chunk =
            (size - copied < kPageSize - offset)
                ? size - copied
                : static_cast<std::size_t>(kPageSize - offset);
        const auto page = pages_.find(page_address);
        std::memcpy(output + copied, page->second.bytes.get() + offset, chunk);
        copied += chunk;
        cursor += chunk;
    }
    return true;
}

bool SparseGuestMemory::Write(std::uint64_t guest_address, const void* data,
                              std::size_t size) {
    if (size == 0) return true;
    if (data == nullptr ||
        size > std::numeric_limits<std::uint64_t>::max() - guest_address) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    const std::uint64_t end = guest_address + size;
    for (std::uint64_t cursor = guest_address; cursor < end;) {
        const std::uint64_t page_address = cursor & ~kPageMask;
        const auto page = pages_.find(page_address);
        if (page == pages_.end() ||
            (page->second.permissions & kMemoryWrite) == 0) {
            return false;
        }
        const std::uint64_t page_end = page_address + kPageSize;
        cursor = page_end < end ? page_end : end;
    }

    const auto* input = static_cast<const std::uint8_t*>(data);
    std::uint64_t cursor = guest_address;
    std::size_t copied = 0;
    while (copied < size) {
        const std::uint64_t page_address = cursor & ~kPageMask;
        const std::size_t offset = static_cast<std::size_t>(cursor & kPageMask);
        const std::size_t chunk =
            (size - copied < kPageSize - offset)
                ? size - copied
                : static_cast<std::size_t>(kPageSize - offset);
        const auto page = pages_.find(page_address);
        std::memcpy(page->second.bytes.get() + offset, input + copied, chunk);
        copied += chunk;
        cursor += chunk;
    }
    return true;
}

bool SparseGuestMemory::Zero(std::uint64_t guest_address,
                             std::uint64_t size) {
    if (size == 0) return true;
    if (size > std::numeric_limits<std::uint64_t>::max() - guest_address) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    const std::uint64_t end = guest_address + size;
    for (std::uint64_t cursor = guest_address; cursor < end;) {
        const std::uint64_t page_address = cursor & ~kPageMask;
        const auto page = pages_.find(page_address);
        if (page == pages_.end() ||
            (page->second.permissions & kMemoryWrite) == 0) {
            return false;
        }
        const std::uint64_t page_end = page_address + kPageSize;
        cursor = page_end < end ? page_end : end;
    }

    std::uint64_t cursor = guest_address;
    while (cursor < end) {
        const std::uint64_t page_address = cursor & ~kPageMask;
        const std::size_t offset = static_cast<std::size_t>(cursor & kPageMask);
        const std::uint64_t page_end = page_address + kPageSize;
        const std::size_t chunk = static_cast<std::size_t>(
            (end < page_end ? end : page_end) - cursor);
        const auto page = pages_.find(page_address);
        std::memset(page->second.bytes.get() + offset, 0, chunk);
        cursor += chunk;
    }
    return true;
}

bool SparseGuestMemory::Protect(std::uint64_t guest_address,
                                std::uint64_t size,
                                std::uint32_t permissions) {
    if (!IsValidRange(guest_address, size) ||
        (permissions & ~kKnownPermissions) != 0) {
        return false;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    const std::uint64_t page_count = size / kPageSize;
    for (std::uint64_t i = 0; i < page_count; ++i) {
        if (pages_.find(guest_address + i * kPageSize) == pages_.end()) {
            return false;
        }
    }
    for (std::uint64_t i = 0; i < page_count; ++i) {
        pages_.find(guest_address + i * kPageSize)->second.permissions = permissions;
    }
    return true;
}

void SparseGuestMemory::Unmap(std::uint64_t guest_address,
                              std::uint64_t size) noexcept {
    if (!IsValidRange(guest_address, size)) return;

    std::lock_guard<std::mutex> lock(mutex_);
    const std::uint64_t page_count = size / kPageSize;
    for (std::uint64_t i = 0; i < page_count; ++i) {
        const auto page = pages_.find(guest_address + i * kPageSize);
        if (page != pages_.end()) {
            pages_.erase(page);
            mapped_bytes_ -= kPageSize;
        }
    }
}

std::uint64_t SparseGuestMemory::MappedBytes() const noexcept {
    std::lock_guard<std::mutex> lock(mutex_);
    return mapped_bytes_;
}

} // namespace neutron::memory
