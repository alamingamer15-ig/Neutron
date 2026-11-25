#define _POSIX_C_SOURCE 200809L
#include <jni.h>

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
    append_format(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                  &output_length, "  command: %s\n  rootfs: %s\n  home: %s\n",
                  command, rootfs, home);
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
                size_t remaining = TERMINAL_OUTPUT_LIMIT - output_length;
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
