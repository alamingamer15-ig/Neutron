#include <jni.h>

#include "neutron/zydis_decoder.h"

#include <cstdint>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronNative_decodeInstruction(
    JNIEnv* env,
    jclass,
    jbyteArray byte_array,
    jlong runtime_address
) {
    if (byte_array == nullptr) {
        return env->NewStringUTF("decode error: null byte array");
    }

    const jsize length = env->GetArrayLength(byte_array);
    if (length <= 0) {
        return env->NewStringUTF("decode error: empty input");
    }

    std::vector<jbyte> input(static_cast<std::size_t>(length));
    env->GetByteArrayRegion(byte_array, 0, length, input.data());
    if (env->ExceptionCheck()) {
        return nullptr;
    }

    const auto* bytes = reinterpret_cast<const std::uint8_t*>(input.data());
    const auto result = neutron::decoder::DecodeOne(
        bytes,
        input.size(),
        static_cast<std::uint64_t>(runtime_address)
    );

    return env->NewStringUTF(result.c_str());
}
