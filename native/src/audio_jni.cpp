#include <jni.h>
#include "neutron/audio/audio_output.hpp"
#include <cstdint>
#include <memory>
#include <mutex>
#include <vector>
namespace {
std::mutex g_audio_mutex;
std::unique_ptr<neutron::audio::AudioOutput> g_audio;
neutron::audio::AudioOutput* GetAudio() {
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    return g_audio.get();
}
}
extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronAudio_nativeStart(
    JNIEnv*, jclass, jint sample_rate) {
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    if (!g_audio) g_audio = std::make_unique<neutron::audio::AudioOutput>();
    return g_audio->Start(sample_rate) ? JNI_TRUE : JNI_FALSE;
}
extern "C" JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronAudio_nativeStop(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> lock(g_audio_mutex);
    if (g_audio) g_audio->Stop();
}
extern "C" JNIEXPORT jint JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronAudio_nativeWritePcm16(
    JNIEnv* env, jclass, jbyteArray pcm, jint channels) {
    if (!pcm || (channels != 1 && channels != 2)) return 0;
    const jsize bytes = env->GetArrayLength(pcm);
    if (bytes <= 0 || (bytes % (channels * 2)) != 0) return 0;
    std::vector<std::int16_t> samples(static_cast<std::size_t>(bytes) / 2);
    env->GetByteArrayRegion(pcm, 0, bytes,
        reinterpret_cast<jbyte*>(samples.data()));
    if (env->ExceptionCheck()) return 0;
    auto* audio = GetAudio();
    if (!audio) return 0;
    const std::size_t frames = samples.size() / static_cast<std::size_t>(channels);
    return static_cast<jint>(audio->WritePcm16(samples.data(), frames,
        static_cast<std::uint32_t>(channels)));
}
