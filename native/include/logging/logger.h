#pragma once

#include <cstddef>
#include <string>
#include <string_view>

namespace neutron::logging {

enum class Level : int {
    Trace = 0,
    Debug = 1,
    Info = 2,
    Warn = 3,
    Error = 4,
    Fatal = 5
};

struct Config {
    std::string directory;
    std::size_t max_file_bytes = 1024 * 1024;
    unsigned int backup_count = 3;
    Level minimum_level = Level::Trace;
};

class Logger final {
public:
    // Initializes the process-wide logger. The directory must already exist.
    // Returns false if the log file cannot be opened.
    static bool Initialize(const Config& config);

    static void Write(Level level, std::string_view tag, std::string_view message);
    static void SetMinimumLevel(Level level);
    static void Flush();
    static void Shutdown();

    Logger() = delete;
};

} // namespace neutron::logging
