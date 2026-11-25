#pragma once

#include <cstdint>
#include <string>

namespace neutron::elf {

struct LoadedImage {
    std::uint64_t entry_point = 0;
    std::uint64_t load_bias = 0;
    std::uint64_t image_base = 0;
    std::uint64_t image_size = 0;
    std::uint64_t program_header_address = 0;
    std::uint16_t program_header_count = 0;
    std::string interpreter_path;
};

} // namespace neutron::elf
