#include "neutron/audio/audio_output.hpp"
#include <algorithm>
namespace neutron::audio {
namespace { constexpr float kPcm16Scale = 1.0F / 32768.0F; }
AudioOutput::AudioOutput() : ring_(kCapacityFrames * kOutputChannels, 0.0F) {}
AudioOutput::~AudioOutput() { Stop(); }

bool AudioOutput::Start(std::int32_t sample_rate) {
    if (sample_rate != 44100 && sample_rate != 48000) return false;
    Stop();
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Shared)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(static_cast<std::int32_t>(kOutputChannels))
        ->setSampleRate(sample_rate)
        ->setUsage(oboe::Usage::Game)
        ->setContentType(oboe::ContentType::Music)
        ->setDataCallback(this);
    const oboe::Result opened = builder.openStream(stream_);
    if (opened != oboe::Result::OK || !stream_) { stream_.reset(); return false; }
    if (stream_->requestStart() != oboe::Result::OK) {
        stream_->close(); stream_.reset(); return false;
    }
    running_.store(true, std::memory_order_release);
    return true;
}

void AudioOutput::Stop() noexcept {
    running_.store(false, std::memory_order_release);
    if (stream_) {
        stream_->requestStop();
        stream_->close();
        stream_.reset();
    }
    std::lock_guard<std::mutex> lock(producer_mutex_);
    ClearQueue();
}

bool AudioOutput::IsRunning() const noexcept {
    return running_.load(std::memory_order_acquire);
}

std::size_t AudioOutput::WritePcm16(const std::int16_t* samples,
                                    std::size_t frames,
                                    std::uint32_t channels) noexcept {
    if (!samples || (channels != 1 && channels != 2)) return 0;
    std::lock_guard<std::mutex> lock(producer_mutex_);
    if (!IsRunning()) return 0;
    const std::uint64_t write = write_frame_.load(std::memory_order_relaxed);
    const std::uint64_t read = read_frame_.load(std::memory_order_acquire);
    const std::size_t free_frames = kCapacityFrames -
        static_cast<std::size_t>(write - read);
    const std::size_t accepted = std::min(frames, free_frames);
    for (std::size_t i = 0; i < accepted; ++i) {
        const std::size_t src = i * channels;
        const std::size_t dst =
            static_cast<std::size_t>((write + i) % kCapacityFrames) * 2;
        const float left = static_cast<float>(samples[src]) * kPcm16Scale;
        ring_[dst] = left;
        ring_[dst + 1] = channels == 1 ? left
            : static_cast<float>(samples[src + 1]) * kPcm16Scale;
    }
    write_frame_.store(write + accepted, std::memory_order_release);
    return accepted;
}

oboe::DataCallbackResult AudioOutput::onAudioReady(oboe::AudioStream*,
                                                    void* audio_data,
                                                    std::int32_t num_frames) {
    auto* out = static_cast<float*>(audio_data);
    const std::size_t requested = static_cast<std::size_t>(num_frames);
    const std::uint64_t read = read_frame_.load(std::memory_order_relaxed);
    const std::uint64_t write = write_frame_.load(std::memory_order_acquire);
    const std::size_t copied = std::min(requested,
        static_cast<std::size_t>(write - read));
    for (std::size_t i = 0; i < copied; ++i) {
        const std::size_t src =
            static_cast<std::size_t>((read + i) % kCapacityFrames) * 2;
        out[i * 2] = ring_[src];
        out[i * 2 + 1] = ring_[src + 1];
    }
    std::fill(out + copied * 2, out + requested * 2, 0.0F);
    read_frame_.store(read + copied, std::memory_order_release);
    return oboe::DataCallbackResult::Continue;
}

void AudioOutput::ClearQueue() noexcept {
    read_frame_.store(0, std::memory_order_relaxed);
    write_frame_.store(0, std::memory_order_relaxed);
}
} // namespace neutron::audio
