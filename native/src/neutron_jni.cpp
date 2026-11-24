#include <jni.h>

#include "logging/logger.h"
#include "neutron/zydis_decoder.h"

#include <cstdint>
#include <string>
#include <vector>

namespace {

neutron::logging::Level ToLogLevel(jint level) {
    if (level < static_cast<jint>(neutron::logging::Level::Trace) ||
        level > static_cast<jint>(neutron::logging::Level::Fatal)) {
        return neutron::logging::Level::Info;
    }
    return static_cast<neutron::logging::Level>(level);
}

bool ReadUtfString(JNIEnv* env, jstring value, std::string* output) {
    if (value == nullptr) {
        return false;
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return false;
    }
    output->assign(chars);
    env->ReleaseStringUTFChars(value, chars);
    return true;
}

} // namespace

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

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronLog_nativeInit(
    JNIEnv* env,
    jclass,
    jstring directory,
    jlong max_file_bytes,
    jint backup_count,
    jint minimum_level
) {
    std::string path;
    if (!ReadUtfString(env, directory, &path) ||
        max_file_bytes <= 0 || backup_count < 0 || backup_count > 10 ||
        minimum_level < 0 || minimum_level > 5) {
        return JNI_FALSE;
    }

    neutron::logging::Config config;
    config.directory = std::move(path);
    config.max_file_bytes = static_cast<std::size_t>(max_file_bytes);
    config.backup_count = static_cast<unsigned int>(backup_count);
    config.minimum_level = ToLogLevel(minimum_level);
    return neutron::logging::Logger::Initialize(config) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronLog_nativeLog(
    JNIEnv* env,
    jclass,
    jint level,
    jstring tag,
    jstring message
) {
    std::string native_tag;
    std::string native_message;
    if (!ReadUtfString(env, tag, &native_tag) ||
        !ReadUtfString(env, message, &native_message)) {
        return;
    }

    if (level < 0 || level > 5) {
        return;
    }

    neutron::logging::Logger::Write(
        ToLogLevel(level),
        native_tag,
        native_message
    );
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronLog_nativeSetMinimumLevel(
    JNIEnv*,
    jclass,
    jint level
) {
    if (level >= 0 && level <= 5) {
        neutron::logging::Logger::SetMinimumLevel(ToLogLevel(level));
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronLog_nativeFlush(
    JNIEnv*,
    jclass
) {
    neutron::logging::Logger::Flush();
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronLog_nativeShutdown(
    JNIEnv*,
    jclass
) {
    neutron::logging::Logger::Shutdown();
}
