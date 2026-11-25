#define _POSIX_C_SOURCE 200809L
#include <jni.h>
#include <android/sharedmem.h>

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdarg.h>
#include <limits.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/mman.h>
#include <time.h>
#include <sys/statvfs.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define TERMINAL_OUTPUT_LIMIT (1024U * 1024U)
#define TERMINAL_OUTPUT_EXTRA 4096U

static int append_bytes(char *buffer, size_t capacity, size_t *length,
                        const char *source, size_t count) {
    if (*length >= capacity) return 0;
    size_t available = capacity - *length;
    if (count > available) count = available;
    if (count == 0) return 0;
    memcpy(buffer + *length, source, count);
    *length += count;
    return 1;
}

static void append_format(char *buffer, size_t capacity, size_t *length,
                          const char *format, ...) {
    if (*length >= capacity) return;
    va_list args;
    va_start(args, format);
    int count = vsnprintf(buffer + *length, capacity - *length, format, args);
    va_end(args);
    if (count > 0) {
        size_t written = (size_t) count;
        size_t available = capacity - *length;
        *length += written < available ? written : available - 1U;
    }
}

static const char *signal_name(int signal_number) {
    switch (signal_number) {
        case SIGHUP: return "SIGHUP";
        case SIGINT: return "SIGINT";
        case SIGQUIT: return "SIGQUIT";
        case SIGILL: return "SIGILL";
        case SIGTRAP: return "SIGTRAP";
        case SIGABRT: return "SIGABRT";
        case SIGBUS: return "SIGBUS";
        case SIGFPE: return "SIGFPE";
        case SIGKILL: return "SIGKILL";
        case SIGSEGV: return "SIGSEGV";
        case SIGPIPE: return "SIGPIPE";
        case SIGALRM: return "SIGALRM";
        case SIGTERM: return "SIGTERM";
#ifdef SIGSYS
        case SIGSYS: return "SIGSYS";
#endif
        default: return "UNKNOWN";
    }
}

static void append_path_diagnostics(char *output, size_t capacity,
                                    size_t *length, const char *label,
                                    const char *path) {
    struct stat info;
    append_format(output, capacity, length, "[preflight] %s: %s\n", label, path);
    if (stat(path, &info) != 0) {
        int error_number = errno;
        append_format(output, capacity, length,
                      "  stat: FAILED errno=%d (%s)\n",
                      error_number, strerror(error_number));
        return;
    }

    append_format(output, capacity, length,
                  "  type=%s size=%lld mode=%04o uid=%lu gid=%lu\n",
                  S_ISREG(info.st_mode) ? "regular" :
                  S_ISDIR(info.st_mode) ? "directory" :
                  S_ISLNK(info.st_mode) ? "symlink" : "other",
                  (long long) info.st_size,
                  (unsigned int) (info.st_mode & 07777),
                  (unsigned long) info.st_uid,
                  (unsigned long) info.st_gid);
    append_format(output, capacity, length,
                  "  access: read=%s execute=%s\n",
                  access(path, R_OK) == 0 ? "yes" : "no",
                  access(path, X_OK) == 0 ? "yes" : "no");

    struct statvfs fs_info;
    if (statvfs(path, &fs_info) == 0) {
        append_format(output, capacity, length,
                      "  filesystem: flags=0x%lx noexec=%s readonly=%s\n",
                      (unsigned long) fs_info.f_flag,
#ifdef ST_NOEXEC
                      (fs_info.f_flag & ST_NOEXEC) != 0 ? "yes" : "no",
#else
                      "unknown",
#endif
#ifdef ST_RDONLY
                      (fs_info.f_flag & ST_RDONLY) != 0 ? "yes" : "no");
#else
                      "unknown");
#endif
    } else {
        int error_number = errno;
        append_format(output, capacity, length,
                      "  statvfs: FAILED errno=%d (%s)\n",
                      error_number, strerror(error_number));
    }
}

static jbyteArray make_result(JNIEnv *env, const char *buffer, size_t length) {
    if (length > (size_t) INT_MAX) return NULL;
    jbyteArray result = (*env)->NewByteArray(env, (jsize) length);
    if (result == NULL) return NULL;
    if (length != 0) {
        (*env)->SetByteArrayRegion(env, result, 0, (jsize) length,
                                   (const jbyte *) buffer);
    }
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronTerminal_nativeRunCommand(
        JNIEnv *env, jclass clazz, jstring loader_value, jstring busybox_value,
        jstring rootfs_value, jstring command_value) {
    (void) clazz;

    if (loader_value == NULL || busybox_value == NULL ||
        rootfs_value == NULL || command_value == NULL) {
        static const char message[] = "Neutron: invalid terminal arguments\n";
        return make_result(env, message, sizeof(message) - 1U);
    }

    const char *loader_utf = (*env)->GetStringUTFChars(env, loader_value, NULL);
    const char *busybox_utf = (*env)->GetStringUTFChars(env, busybox_value, NULL);
    const char *rootfs_utf = (*env)->GetStringUTFChars(env, rootfs_value, NULL);
    const char *command_utf = (*env)->GetStringUTFChars(env, command_value, NULL);
    if (loader_utf == NULL || busybox_utf == NULL ||
        rootfs_utf == NULL || command_utf == NULL) {
        if (loader_utf != NULL) (*env)->ReleaseStringUTFChars(env, loader_value, loader_utf);
        if (busybox_utf != NULL) (*env)->ReleaseStringUTFChars(env, busybox_value, busybox_utf);
        if (rootfs_utf != NULL) (*env)->ReleaseStringUTFChars(env, rootfs_value, rootfs_utf);
        if (command_utf != NULL) (*env)->ReleaseStringUTFChars(env, command_value, command_utf);
        return NULL;
    }

    char *loader = strdup(loader_utf);
    char *busybox = strdup(busybox_utf);
    char *rootfs = strdup(rootfs_utf);
    char *command = strdup(command_utf);

    (*env)->ReleaseStringUTFChars(env, loader_value, loader_utf);
    (*env)->ReleaseStringUTFChars(env, busybox_value, busybox_utf);
    (*env)->ReleaseStringUTFChars(env, rootfs_value, rootfs_utf);
    (*env)->ReleaseStringUTFChars(env, command_value, command_utf);

    if (loader == NULL || busybox == NULL || rootfs == NULL || command == NULL) {
        free(loader);
        free(busybox);
        free(rootfs);
        free(command);
        return NULL;
    }

    const size_t path_capacity = strlen(rootfs) * 6U + 128U;
    char *home = (char *) malloc(path_capacity);
    char *library_path = (char *) malloc(path_capacity);
    char *home_env = (char *) malloc(path_capacity + 8U);
    char *path_env = (char *) malloc(path_capacity + 8U);
    char *user_env = strdup("USER=neutron");
    char *term_env = strdup("TERM=xterm-256color");
    char *lang_env = strdup("LANG=C.UTF-8");
    char *output = (char *) malloc(TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA);
    if (home == NULL || library_path == NULL || home_env == NULL ||
        path_env == NULL || user_env == NULL || term_env == NULL ||
        lang_env == NULL || output == NULL) {
        free(loader); free(busybox); free(rootfs); free(command);
        free(home); free(library_path); free(home_env); free(path_env);
        free(user_env); free(term_env); free(lang_env); free(output);
        return NULL;
    }

    (void) snprintf(home, path_capacity, "%s/home", rootfs);
    (void) snprintf(library_path, path_capacity, "%s/lib:%s/usr/lib:%s/lib64",
                    rootfs, rootfs, rootfs);
    (void) snprintf(home_env, path_capacity + 8U, "HOME=%s", home);
    (void) snprintf(path_env, path_capacity + 8U,
                    "PATH=%s/usr/local/sbin:%s/usr/local/bin:%s/usr/sbin:%s/usr/bin:%s/sbin:%s/bin",
                    rootfs, rootfs, rootfs, rootfs, rootfs, rootfs);

    char *const child_argv[] = {
        loader, (char *) "--library-path", library_path,
        busybox, (char *) "sh", (char *) "-c", command, NULL
    };
    char *const child_envp[] = {
        home_env, user_env, term_env, lang_env, path_env, NULL
    };

    size_t output_length = 0;
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[Neutron execution diagnostics]\n");
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[stage 1/7] Arguments prepared\n");
    size_t command_preview_length = strlen(command);
    if (command_preview_length > 2048U) command_preview_length = 2048U;
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "  command (%lu bytes): %.*s%s\n  rootfs: %s\n  home: %s\n",
                  (unsigned long) strlen(command),
                  (int) command_preview_length, command,
                  strlen(command) > command_preview_length ? "..." : "",
                  rootfs, home);
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "  loader: %s\n  busybox: %s\n",
                  loader, busybox);
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "  library path: %s\n",
                  library_path);

    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[stage 2/7] Filesystem preflight\n");
    append_path_diagnostics(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, "rootfs", rootfs);
    append_path_diagnostics(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, "home", home);
    append_path_diagnostics(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, "loader", loader);
    append_path_diagnostics(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, "BusyBox", busybox);

    int pipe_fds[2];
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[stage 3/7] Creating stdout/stderr pipe\n");
    if (pipe(pipe_fds) != 0) {
        int error_number = errno;
        append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                      &output_length, "  FAILED: errno=%d (%s)\n",
                      error_number, strerror(error_number));
        jbyteArray result = make_result(env, output, output_length);
        free(loader); free(busybox); free(rootfs); free(command);
        free(home); free(library_path); free(home_env); free(path_env);
        free(user_env); free(term_env); free(lang_env); free(output);
        return result;
    }

    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[stage 4/7] fork()\n");
    pid_t child = fork();
    if (child == 0) {
        (void) close(pipe_fds[0]);
        if (dup2(pipe_fds[1], STDOUT_FILENO) < 0 ||
            dup2(pipe_fds[1], STDERR_FILENO) < 0) {
            static const char error_text[] =
                    "[child] dup2() failed while redirecting output\n";
            (void) write(STDERR_FILENO, error_text, sizeof(error_text) - 1U);
            _exit(126);
        }
        (void) close(pipe_fds[1]);

        static const char fork_message[] =
                "[child stage 1] Fork succeeded; configuring working directory\n";
        (void) write(STDOUT_FILENO, fork_message, sizeof(fork_message) - 1U);

        if (chdir(home) != 0) {
            int error_number = errno;
            char error_text[256];
            int count = snprintf(error_text, sizeof(error_text),
                                 "[child] chdir('%s') failed: errno=%d (%s)\n",
                                 home, error_number, strerror(error_number));
            if (count > 0) {
                size_t bytes = (size_t) count;
                if (bytes >= sizeof(error_text)) bytes = sizeof(error_text) - 1U;
                (void) write(STDOUT_FILENO, error_text, bytes);
            }
            _exit(126);
        }

        static const char exec_message[] =
                "[child stage 2] chdir() succeeded; calling execve() for ARM64 loader\n";
        (void) write(STDOUT_FILENO, exec_message, sizeof(exec_message) - 1U);
        execve(loader, child_argv, child_envp);

        int exec_error = errno;
        char error_text[PATH_MAX + 192];
        int error_length = snprintf(
                error_text, sizeof(error_text),
                "[child stage 3] execve() returned: errno=%d (%s), loader='%s'\n",
                exec_error, strerror(exec_error), loader);
        if (error_length > 0) {
            size_t bytes = (size_t) error_length;
            if (bytes >= sizeof(error_text)) bytes = sizeof(error_text) - 1U;
            (void) write(STDOUT_FILENO, error_text, bytes);
        }
        _exit(127);
    }

    (void) close(pipe_fds[1]);
    int truncated = 0;
    if (child < 0) {
        int error_number = errno;
        append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                      &output_length, "  FAILED: fork() errno=%d (%s)\n",
                      error_number, strerror(error_number));
        (void) close(pipe_fds[0]);
    } else {
        append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                      &output_length, "  OK: child pid=%ld\n[stage 5/7] Capturing child output\n",
                      (long) child);
        char chunk[8192];
        for (;;) {
            ssize_t count = read(pipe_fds[0], chunk, sizeof(chunk));
            if (count > 0) {
                size_t remaining = output_length < TERMINAL_OUTPUT_LIMIT
                        ? TERMINAL_OUTPUT_LIMIT - output_length : 0U;
                size_t copied = (size_t) count < remaining ? (size_t) count : remaining;
                if (copied != 0) {
                    (void) append_bytes(output, TERMINAL_OUTPUT_LIMIT,
                                        &output_length, chunk, copied);
                }
                if (copied < (size_t) count) truncated = 1;
                continue;
            }
            if (count == 0) break;
            if (errno == EINTR) continue;
            int error_number = errno;
            append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                          &output_length,
                          "\n[diagnostic] read() failed: errno=%d (%s)\n",
                          error_number, strerror(error_number));
            break;
        }
        (void) close(pipe_fds[0]);
    }

    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[stage 6/7] waitpid()\n");
    int wait_status = 0;
    int exit_code = 127;
    if (child > 0) {
        pid_t waited;
        do {
            waited = waitpid(child, &wait_status, 0);
        } while (waited < 0 && errno == EINTR);

        if (waited < 0) {
            int error_number = errno;
            append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                          &output_length,
                          "  FAILED: waitpid() errno=%d (%s)\n",
                          error_number, strerror(error_number));
        } else {
            append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                          &output_length, "  raw wait status: 0x%04x (%d)\n",
                          (unsigned int) wait_status, wait_status);
            if (WIFEXITED(wait_status)) {
                exit_code = WEXITSTATUS(wait_status);
                append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                              &output_length, "  termination: normal exit\n  exit status: %d\n",
                              exit_code);
            } else if (WIFSIGNALED(wait_status)) {
                int signal_number = WTERMSIG(wait_status);
                exit_code = 128 + signal_number;
                append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                              &output_length,
                              "  termination: signal\n  signal: %d (%s)\n  shell-style status: %d\n",
                              signal_number, signal_name(signal_number), exit_code);
#ifdef WCOREDUMP
                append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                              &output_length, "  core dumped: %s\n",
                              WCOREDUMP(wait_status) ? "yes" : "no");
#endif
#ifdef SIGSYS
                if (signal_number == SIGSYS) {
                    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                                  &output_length,
                                  "  SIGSYS means a system call was rejected or trapped.\n"
                                  "  waitpid() does not expose the offending syscall number.\n"
                                  "  Next step: add syscall-level tracing/instrumentation.\n");
                }
#endif
            } else {
                append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                              &output_length, "  termination: unclassified wait status\n");
            }
        }
    }

    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[stage 7/7] Command complete\n");
    if (truncated) {
        static const char notice[] = "\n[Neutron: child output truncated at 1 MiB]\n";
        (void) append_bytes(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, notice, sizeof(notice) - 1U);
    }
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "[exit %d]\n", exit_code);

    jbyteArray result = make_result(env, output, output_length);
    free(loader); free(busybox); free(rootfs); free(command);
    free(home); free(library_path); free(home_env); free(path_env);
    free(user_env); free(term_env); free(lang_env); free(output);
    return result;
}


/*
 * Platform probes used to distinguish app-private exec restrictions from
 * executable-memory mapping restrictions. This intentionally maps memory
 * executable but does not execute generated machine code.
 */
static void diagnostic_append(char *buffer, size_t capacity, size_t *length,
                              const char *format, ...) {
    if (*length >= capacity) return;
    va_list args;
    va_start(args, format);
    int count = vsnprintf(buffer + *length, capacity - *length, format, args);
    va_end(args);
    if (count > 0) {
        size_t available = capacity - *length;
        size_t written = (size_t) count;
        *length += written < available ? written : available - 1U;
    }
}

static void probe_app_private_exec(char *output, size_t capacity, size_t *length,
                                   const char *files_directory) {
    char template_path[PATH_MAX];
    struct timespec now;
    long nonce = 0;
    if (clock_gettime(CLOCK_REALTIME, &now) == 0) {
        nonce = (long) (now.tv_nsec % 1000000000L);
    }
    int path_count = snprintf(template_path, sizeof(template_path),
                              "%s/.neutron-exec-probe-%ld-%ld-XXXXXX",
                              files_directory, (long) getpid(), nonce);
    if (path_count < 0 || (size_t) path_count >= sizeof(template_path)) {
        diagnostic_append(output, capacity, length,
                          "[app-data exec] FAILED: probe path is too long\n");
        return;
    }

    int file_fd = mkstemp(template_path);
    if (file_fd < 0) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[app-data exec] create FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        return;
    }

    static const char script[] = "#!/system/bin/sh\nexit 37\n";
    size_t written = 0;
    while (written < sizeof(script) - 1U) {
        ssize_t count = write(file_fd, script + written,
                              sizeof(script) - 1U - written);
        if (count > 0) {
            written += (size_t) count;
            continue;
        }
        if (count < 0 && errno == EINTR) continue;
        int error_number = count < 0 ? errno : EIO;
        diagnostic_append(output, capacity, length,
                          "[app-data exec] write FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        (void) close(file_fd);
        (void) unlink(template_path);
        return;
    }

    if (fchmod(file_fd, 0700) != 0) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[app-data exec] chmod FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        (void) close(file_fd);
        (void) unlink(template_path);
        return;
    }
    (void) close(file_fd);

    diagnostic_append(output, capacity, length,
                      "[app-data exec] probe file: %s\n", template_path);
    struct statvfs fs_info;
    if (statvfs(files_directory, &fs_info) == 0) {
        diagnostic_append(output, capacity, length,
                          "  filesystem flags=0x%lx",
                          (unsigned long) fs_info.f_flag);
#ifdef ST_NOEXEC
        diagnostic_append(output, capacity, length, " noexec=%s",
                          (fs_info.f_flag & ST_NOEXEC) != 0 ? "yes" : "no");
#endif
#ifdef ST_RDONLY
        diagnostic_append(output, capacity, length, " readonly=%s",
                          (fs_info.f_flag & ST_RDONLY) != 0 ? "yes" : "no");
#endif
        diagnostic_append(output, capacity, length, "\n");
    } else {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "  statvfs FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
    }

    int error_pipe[2];
    if (pipe(error_pipe) != 0) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[app-data exec] pipe FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        (void) unlink(template_path);
        return;
    }
    int flags = fcntl(error_pipe[1], F_GETFD);
    if (flags >= 0) (void) fcntl(error_pipe[1], F_SETFD, flags | FD_CLOEXEC);

    pid_t child = fork();
    if (child == 0) {
        (void) close(error_pipe[0]);
        char *const argv[] = {template_path, NULL};
        char *const envp[] = {"PATH=/system/bin", NULL};
        execve(template_path, argv, envp);
        int error_number = errno;
        (void) write(error_pipe[1], &error_number, sizeof(error_number));
        _exit(127);
    }

    (void) close(error_pipe[1]);
    if (child < 0) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[app-data exec] fork FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        (void) close(error_pipe[0]);
        (void) unlink(template_path);
        return;
    }

    int child_exec_errno = 0;
    ssize_t error_bytes;
    do {
        error_bytes = read(error_pipe[0], &child_exec_errno,
                           sizeof(child_exec_errno));
    } while (error_bytes < 0 && errno == EINTR);
    (void) close(error_pipe[0]);

    int status = 0;
    pid_t waited;
    do {
        waited = waitpid(child, &status, 0);
    } while (waited < 0 && errno == EINTR);

    if (error_bytes == (ssize_t) sizeof(child_exec_errno)) {
        diagnostic_append(output, capacity, length,
                          "[app-data exec] execve FAILED: errno=%d (%s)\n",
                          child_exec_errno, strerror(child_exec_errno));
    } else if (error_bytes == 0 && waited == child && WIFEXITED(status)) {
        int code = WEXITSTATUS(status);
        diagnostic_append(output, capacity, length,
                          "[app-data exec] execve SUCCEEDED; script exit=%d (expected 37)\n",
                          code);
    } else if (waited == child && WIFSIGNALED(status)) {
        diagnostic_append(output, capacity, length,
                          "[app-data exec] child killed by signal %d (%s)\n",
                          WTERMSIG(status), signal_name(WTERMSIG(status)));
    } else {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[app-data exec] inconclusive: pipe_read=%ld waitpid=%ld status=0x%x errno=%d (%s)\n",
                          (long) error_bytes, (long) waited, status,
                          error_number, strerror(error_number));
    }

    (void) unlink(template_path);
}

static void probe_shared_memory(char *output, size_t capacity, size_t *length) {
    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;
    int fd = ASharedMemory_create("neutron-exec-probe", (size_t) page_size);
    if (fd < 0) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[SharedMemory] create FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        return;
    }

    size_t region_size = ASharedMemory_getSize(fd);
    diagnostic_append(output, capacity, length,
                      "[SharedMemory] created fd=%d size=%lu bytes\n",
                      fd, (unsigned long) region_size);
    if (region_size == 0) {
        diagnostic_append(output, capacity, length,
                          "[SharedMemory] invalid region size\n");
        (void) close(fd);
        return;
    }

    static const unsigned char marker[] = {
        'N', 'E', 'U', 'T', 'R', 'O', 'N', '-', 'S', 'H', 'M', '-', 'O', 'K'
    };
    void *rw = mmap(NULL, region_size, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (rw == MAP_FAILED) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[SharedMemory] RW mmap FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
        (void) close(fd);
        return;
    }

    memcpy(rw, marker, sizeof(marker));
    int write_verified = memcmp(rw, marker, sizeof(marker)) == 0;
    (void) munmap(rw, region_size);
    diagnostic_append(output, capacity, length,
                      "[SharedMemory] RW mapping: %s\n",
                      write_verified ? "PASS" : "FAIL");

    void *rx = mmap(NULL, region_size, PROT_READ | PROT_EXEC, MAP_SHARED, fd, 0);
    if (rx == MAP_FAILED) {
        int error_number = errno;
        diagnostic_append(output, capacity, length,
                          "[SharedMemory] RX mmap FAILED: errno=%d (%s)\n",
                          error_number, strerror(error_number));
    } else {
        int contents_verified = memcmp(rx, marker, sizeof(marker)) == 0;
        diagnostic_append(output, capacity, length,
                          "[SharedMemory] RX mapping: PASS; contents=%s\n",
                          contents_verified ? "verified" : "MISMATCH");
        (void) munmap(rx, region_size);
    }

    (void) close(fd);
}

JNIEXPORT jstring JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronTerminal_nativeRunPlatformDiagnostics(
        JNIEnv *env, jclass clazz, jstring files_directory_value) {
    (void) clazz;
    if (files_directory_value == NULL) {
        return (*env)->NewStringUTF(env, "Neutron: files directory argument is null\n");
    }

    const char *files_directory = (*env)->GetStringUTFChars(
            env, files_directory_value, NULL);
    if (files_directory == NULL) return NULL;

    char output[8192];
    size_t length = 0;
    diagnostic_append(output, sizeof(output), &length,
                      "[Neutron platform execution diagnostics]\n");
    diagnostic_append(output, sizeof(output), &length,
                      "  Android API target: 28 (configured in app/build.gradle)\n");
    diagnostic_append(output, sizeof(output), &length,
                      "  app files: %s\n", files_directory);
    probe_app_private_exec(output, sizeof(output), &length, files_directory);
    probe_shared_memory(output, sizeof(output), &length);
    diagnostic_append(output, sizeof(output), &length,
                      "[diagnostics complete]\n");

    (*env)->ReleaseStringUTFChars(env, files_directory_value, files_directory);
    return (*env)->NewStringUTF(env, output);
}
