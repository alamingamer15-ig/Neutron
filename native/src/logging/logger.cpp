#include "logging/logger.h"

#include <algorithm>
#include <ctime>
#include <fstream>
#include <iomanip>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>
#include <utility>

#include <cerrno>
#include <cstdio>
#include <sys/stat.h>

namespace neutron::logging {
namespace {

std::mutex g_mutex;
std::ofstream g_stream;
std::string g_directory;
std::string g_log_path;
std::size_t g_max_file_bytes = 1024 * 1024;
unsigned int g_backup_count = 3;
Level g_minimum_level = Level::Trace;
bool g_initialized = false;

const char* LevelName(Level level) {
    switch (level) {
        case Level::Trace: return "TRACE";
        case Level::Debug: return "DEBUG";
        case Level::Info: return "INFO";
        case Level::Warn: return "WARN";
        case Level::Error: return "ERROR";
        case Level::Fatal: return "FATAL";
    }
    return "UNKNOWN";
}

bool FileSize(const std::string& path, std::size_t* size) {
    struct stat info {};
    if (stat(path.c_str(), &info) != 0) {
        if (errno == ENOENT) {
            *size = 0;
            return true;
        }
        return false;
    }
    *size = info.st_size < 0 ? 0 : static_cast<std::size_t>(info.st_size);
    return true;
}

bool RotateFiles() {
    if (g_stream.is_open()) {
        g_stream.flush();
        g_stream.close();
    }

    if (g_backup_count > 0) {
        const std::string oldest = g_log_path + "." + std::to_string(g_backup_count);
        std::remove(oldest.c_str());

        for (unsigned int index = g_backup_count; index > 1; --index) {
            const std::string from = g_log_path + "." + std::to_string(index - 1);
            const std::string to = g_log_path + "." + std::to_string(index);
            if (std::rename(from.c_str(), to.c_str()) != 0 && errno != ENOENT) {
                return false;
            }
        }

        const std::string first_backup = g_log_path + ".1";
        if (std::rename(g_log_path.c_str(), first_backup.c_str()) != 0 && errno != ENOENT) {
            return false;
        }
    } else {
        std::remove(g_log_path.c_str());
    }

    g_stream.open(g_log_path, std::ios::out | std::ios::app);
    return g_stream.is_open();
}

std::string FormatLine(Level level, std::string_view tag, std::string_view message) {
    const std::time_t now = std::time(nullptr);
    std::tm local_time {};
    localtime_r(&now, &local_time);

    std::ostringstream output;
    output << std::put_time(&local_time, "%Y-%m-%d %H:%M:%S")
           << " [" << LevelName(level) << "]"
           << " [tid:" << std::this_thread::get_id() << "]"
           << " [" << tag << "] " << message << '\n';
    return output.str();
}

} // namespace

bool Logger::Initialize(const Config& config) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_initialized = false;

    if (g_stream.is_open()) {
        g_stream.flush();
        g_stream.close();
    }

    if (config.directory.empty() || config.max_file_bytes == 0) {
        return false;
    }

    g_directory = config.directory;
    while (!g_directory.empty() && g_directory.back() == '/') {
        g_directory.pop_back();
    }
    if (g_directory.empty()) {
        return false;
    }

    g_log_path = g_directory + "/neutron.log";
    g_max_file_bytes = config.max_file_bytes;
    g_backup_count = std::min(config.backup_count, 10U);
    g_minimum_level = config.minimum_level;

    g_stream.open(g_log_path, std::ios::out | std::ios::app);
    g_initialized = g_stream.is_open();
    return g_initialized;
}

void Logger::Write(Level level, std::string_view tag, std::string_view message) {
    std::lock_guard<std::mutex> lock(g_mutex);

    if (!g_initialized || static_cast<int>(level) < static_cast<int>(g_minimum_level)) {
        return;
    }

    std::size_t current_size = 0;
    if (!FileSize(g_log_path, &current_size)) {
        return;
    }

    const std::string line = FormatLine(level, tag, message);
    if (current_size > 0 && current_size + line.size() > g_max_file_bytes) {
        if (!RotateFiles()) {
            g_initialized = false;
            return;
        }
    }

    g_stream << line;
    if (!g_stream) {
        g_initialized = false;
        return;
    }

    // Flush each record so normal app termination does not discard buffered logs.
    g_stream.flush();
    if (!g_stream) {
        g_initialized = false;
    }
}

void Logger::SetMinimumLevel(Level level) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_minimum_level = level;
}

void Logger::Flush() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_stream.is_open()) {
        g_stream.flush();
        if (!g_stream) {
            g_initialized = false;
        }
    }
}

void Logger::Shutdown() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_stream.is_open()) {
        g_stream.flush();
        g_stream.close();
    }
    g_initialized = false;
}

} // namespace neutron::logging
