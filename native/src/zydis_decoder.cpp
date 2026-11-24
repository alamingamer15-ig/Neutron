#include "neutron/zydis_decoder.h"

#include <Zydis/Zydis.h>

namespace neutron::decoder {

std::string DecodeOne(
    const std::uint8_t* bytes,
    std::size_t length,
    std::uint64_t runtime_address
) {
    if (bytes == nullptr || length == 0) {
        return "decode error: empty input";
    }

    ZydisDecoder decoder;
    const auto init_status = ZydisDecoderInit(
        &decoder,
        ZYDIS_MACHINE_MODE_LONG_64,
        ZYDIS_STACK_WIDTH_64
    );
    if (!ZYAN_SUCCESS(init_status)) {
        return "decode error: decoder initialization failed";
    }

    ZydisDecodedInstruction instruction{};
    ZydisDecodedOperand operands[ZYDIS_MAX_OPERAND_COUNT]{};

    const auto decode_status = ZydisDecoderDecodeFull(
        &decoder,
        bytes,
        length,
        &instruction,
        operands
    );
    if (!ZYAN_SUCCESS(decode_status)) {
        return "decode error: invalid or incomplete x86-64 instruction";
    }

    ZydisFormatter formatter;
    const auto formatter_status = ZydisFormatterInit(
        &formatter,
        ZYDIS_FORMATTER_STYLE_INTEL
    );
    if (!ZYAN_SUCCESS(formatter_status)) {
        return "decode error: formatter initialization failed";
    }

    char output[256]{};
    const auto format_status = ZydisFormatterFormatInstruction(
        &formatter,
        &instruction,
        operands,
        instruction.operand_count_visible,
        output,
        sizeof(output),
        runtime_address,
        nullptr
    );
    if (!ZYAN_SUCCESS(format_status)) {
        return "decode error: instruction formatting failed";
    }

    return std::string(output);
}

} // namespace neutron::decoder
