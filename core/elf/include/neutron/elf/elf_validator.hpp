#pragma once

#include "neutron/elf/elf_parser.hpp"

namespace neutron::elf {

// Performs semantic checks on a successfully parsed executable.
bool ValidateExecutable(const ParsedElf& elf, ParseError* error = nullptr);

} // namespace neutron::elf
