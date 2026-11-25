#pragma once
#include <oboe/Oboe.h>
#include <atomic>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <vector>
namespace neutron::audio {
class AudioOutput final : public oboe::AudioStreamDataCallback {
public:
    static constexpr std::uint32_t kOutputChannels = 2;
    static constexpr std::size_t kCapacityFrames = 65536;
    AudioOutput();
    ~AudioOutput() override;
    AudioOutput(const AudioOutput&) = delete;
    AudioOutput& operator=(const AudioOutput&) = delete;
    bool Start(std::int32_t sample_rate);
    void Stop() noexcept;
    bool IsRunning() const noexcept;
    std::size_t WritePcm16(const std::int16_t* samples, std::size_t frames,
                           std::uint32_t channels) noexcept;
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                          void* audio_data,
                                          std::int32_t num_frames) override;
private:
    void ClearQueue() noexcept;
    std::vector<float> ring_;
    alignas(64) std::atomic<std::uint64_t> write_frame_{0};
    alignas(64) std::atomic<std::uint64_t> read_frame_{0};
    std::shared_ptr<oboe::AudioStream> stream_;
    std::atomic<bool> running_{false};
};
} // namespace neutron::audio
