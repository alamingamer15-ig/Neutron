#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

namespace neutron::decoder {

// Decodes exactly one x86-64 instruction from the beginning of the buffer.
// The returned text is Intel-syntax disassembly, not executable code.
std::string DecodeOne(
    const std::uint8_t* bytes,
    std::size_t length,
    std::uint64_t runtime_address
);

} // namespace neutron::decoder
