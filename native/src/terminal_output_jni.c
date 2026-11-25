#include <jni.h>

#include <errno.h>
#include <limits.h>
#include <poll.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#define TERMINAL_OUTPUT_LIMIT (1024U * 1024U)
#define TERMINAL_OUTPUT_EXTRA 256U

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

    const size_t path_capacity = strlen(rootfs) * 3U + 64U;
    char *home = (char *) malloc(path_capacity);
    char *library_path = (char *) malloc(path_capacity);
    char *home_env = (char *) malloc(path_capacity + 8U);
    char *path_env = strdup("PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
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

    char *const child_argv[] = {
        loader, (char *) "--library-path", library_path,
        busybox, (char *) "sh", (char *) "-c", command, NULL
    };
    char *const child_envp[] = {
        home_env, user_env, term_env, lang_env, path_env, NULL
    };

    int pipe_fds[2];
    if (pipe(pipe_fds) != 0) {
        const char *error_text = "Neutron: could not create terminal output pipe\n";
        size_t length = strlen(error_text);
        memcpy(output, error_text, length);
        jbyteArray result = make_result(env, output, length);
        free(loader); free(busybox); free(rootfs); free(command);
        free(home); free(library_path); free(home_env); free(path_env);
        free(user_env); free(term_env); free(lang_env); free(output);
        return result;
    }

    pid_t child = fork();
    if (child == 0) {
        (void) close(pipe_fds[0]);
        if (dup2(pipe_fds[1], STDOUT_FILENO) < 0 ||
            dup2(pipe_fds[1], STDERR_FILENO) < 0) {
            _exit(126);
        }
        (void) close(pipe_fds[1]);
        if (chdir(home) != 0) {
            static const char error_text[] = "Neutron: cannot enter rootfs home directory\n";
            (void) write(STDOUT_FILENO, error_text, sizeof(error_text) - 1U);
            _exit(126);
        }
        execve(loader, child_argv, child_envp);
        {
            static const char error_text[] = "Neutron: execve failed for the ARM64 BusyBox loader\n";
            (void) write(STDOUT_FILENO, error_text, sizeof(error_text) - 1U);
        }
        _exit(127);
    }

    (void) close(pipe_fds[1]);
    size_t output_length = 0;
    int truncated = 0;
    if (child < 0) {
        static const char error_text[] = "Neutron: could not start BusyBox process\n";
        (void) append_bytes(output, TERMINAL_OUTPUT_LIMIT, &output_length,
                            error_text, sizeof(error_text) - 1U);
        (void) close(pipe_fds[0]);
    } else {
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
            static const char read_error[] = "\nNeutron: error reading command output\n";
            (void) append_bytes(output, TERMINAL_OUTPUT_LIMIT, &output_length,
                                read_error, sizeof(read_error) - 1U);
            break;
        }
        (void) close(pipe_fds[0]);
    }

    int wait_status = 0;
    int exit_code = 127;
    if (child > 0) {
        while (waitpid(child, &wait_status, 0) < 0) {
            if (errno == EINTR) continue;
            wait_status = -1;
            break;
        }
        if (wait_status >= 0) {
            if (WIFEXITED(wait_status)) exit_code = WEXITSTATUS(wait_status);
            else if (WIFSIGNALED(wait_status)) exit_code = 128 + WTERMSIG(wait_status);
        }
    }

    if (truncated) {
        static const char notice[] = "\n[Neutron: output truncated at 1 MiB]\n";
        (void) append_bytes(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, notice, sizeof(notice) - 1U);
    }
    char status_line[64];
    int status_length = snprintf(status_line, sizeof(status_line),
                                 "\n[exit %d]\n", exit_code);
    if (status_length > 0) {
        (void) append_bytes(output, TERMINAL_OUTPUT_LIMIT + TERMINAL_OUTPUT_EXTRA,
                            &output_length, status_line, (size_t) status_length);
    }

    jbyteArray result = make_result(env, output, output_length);
    free(loader); free(busybox); free(rootfs); free(command);
    free(home); free(library_path); free(home_env); free(path_env);
    free(user_env); free(term_env); free(lang_env); free(output);
    return result;
}
