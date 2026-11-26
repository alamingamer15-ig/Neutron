#define _GNU_SOURCE
#define _POSIX_C_SOURCE 200809L

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <termios.h>
#include <unistd.h>

struct neutron_pty {
    int master_fd;
    pid_t child_pid;
    atomic_int closed;
};

static struct neutron_pty *from_handle(jlong handle) {
    return (struct neutron_pty *) (intptr_t) handle;
}

JNIEXPORT jlong JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronPty_nativeStart(
        JNIEnv *env, jclass clazz, jstring prefix_value,
        jstring home_value, jstring bin_value) {
    (void) clazz;
    if (prefix_value == NULL || home_value == NULL || bin_value == NULL) return 0;

    const char *prefix = (*env)->GetStringUTFChars(env, prefix_value, NULL);
    const char *home = (*env)->GetStringUTFChars(env, home_value, NULL);
    const char *bin = (*env)->GetStringUTFChars(env, bin_value, NULL);
    if (prefix == NULL || home == NULL || bin == NULL) {
        if (prefix != NULL) (*env)->ReleaseStringUTFChars(env, prefix_value, prefix);
        if (home != NULL) (*env)->ReleaseStringUTFChars(env, home_value, home);
        if (bin != NULL) (*env)->ReleaseStringUTFChars(env, bin_value, bin);
        return 0;
    }

    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) != 0 || unlockpt(master) != 0) {
        if (master >= 0) close(master);
        (*env)->ReleaseStringUTFChars(env, prefix_value, prefix);
        (*env)->ReleaseStringUTFChars(env, home_value, home);
        (*env)->ReleaseStringUTFChars(env, bin_value, bin);
        return 0;
    }

    char slave_name[256];
    if (ptsname_r(master, slave_name, sizeof(slave_name)) != 0) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, prefix_value, prefix);
        (*env)->ReleaseStringUTFChars(env, home_value, home);
        (*env)->ReleaseStringUTFChars(env, bin_value, bin);
        return 0;
    }

    struct neutron_pty *session = calloc(1, sizeof(*session));
    if (session == NULL) {
        close(master);
        (*env)->ReleaseStringUTFChars(env, prefix_value, prefix);
        (*env)->ReleaseStringUTFChars(env, home_value, home);
        (*env)->ReleaseStringUTFChars(env, bin_value, bin);
        return 0;
    }

    pid_t child = fork();
    if (child == 0) {
        close(master);
        if (setsid() < 0) _exit(126);

        int slave = open(slave_name, O_RDWR);
        if (slave < 0) _exit(126);
        (void) ioctl(slave, TIOCSCTTY, 0);
        (void) dup2(slave, STDIN_FILENO);
        (void) dup2(slave, STDOUT_FILENO);
        (void) dup2(slave, STDERR_FILENO);
        if (slave > STDERR_FILENO) close(slave);

        if (chdir(home) != 0) _exit(126);
        (void) setenv("HOME", home, 1);
        (void) setenv("PREFIX", prefix, 1);
        (void) setenv("PATH", bin, 1);
        (void) setenv("SHELL", "/system/bin/sh", 1);
        (void) setenv("TERM", "xterm-256color", 1);
        (void) setenv("LANG", "C.UTF-8", 1);
        (void) setenv("USER", "neutron", 1);
        (void) setenv("PS1", "neutron:\\w $ ", 1);

        char *const argv[] = {(char *) "sh", (char *) "-i", NULL};
        execv("/system/bin/sh", argv);
        _exit(127);
    }

    (*env)->ReleaseStringUTFChars(env, prefix_value, prefix);
    (*env)->ReleaseStringUTFChars(env, home_value, home);
    (*env)->ReleaseStringUTFChars(env, bin_value, bin);

    if (child < 0) {
        close(master);
        free(session);
        return 0;
    }

    session->master_fd = master;
    session->child_pid = child;
    atomic_init(&session->closed, 0);

    struct winsize window;
    memset(&window, 0, sizeof(window));
    window.ws_row = 24;
    window.ws_col = 80;
    (void) ioctl(master, TIOCSWINSZ, &window);

    return (jlong) (intptr_t) session;
}

JNIEXPORT jint JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronPty_nativeRead(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray output) {
    (void) clazz;
    struct neutron_pty *session = from_handle(handle);
    if (session == NULL || output == NULL ||
            atomic_load(&session->closed) != 0) return -1;

    jsize capacity = (*env)->GetArrayLength(env, output);
    if (capacity <= 0) return 0;
    jsize amount = capacity > 4096 ? 4096 : capacity;
    unsigned char buffer[4096];

    struct pollfd descriptor;
    descriptor.fd = session->master_fd;
    descriptor.events = POLLIN | POLLERR | POLLHUP;
    descriptor.revents = 0;
    int ready = poll(&descriptor, 1, 250);
    if (atomic_load(&session->closed) != 0) return -1;
    if (ready == 0) return 0;
    if (ready < 0) return errno == EINTR ? 0 : -1;

    ssize_t count = read(session->master_fd, buffer, (size_t) amount);
    if (count <= 0) return -1;
    (*env)->SetByteArrayRegion(env, output, 0, (jsize) count, (const jbyte *) buffer);
    return (jint) count;
}

JNIEXPORT jint JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronPty_nativeWrite(
        JNIEnv *env, jclass clazz, jlong handle, jbyteArray input) {
    (void) clazz;
    struct neutron_pty *session = from_handle(handle);
    if (session == NULL || input == NULL ||
            atomic_load(&session->closed) != 0) return -1;

    jsize length = (*env)->GetArrayLength(env, input);
    if (length <= 0) return 0;
    jbyte *bytes = (*env)->GetByteArrayElements(env, input, NULL);
    if (bytes == NULL) return -1;

    jsize offset = 0;
    while (offset < length && atomic_load(&session->closed) == 0) {
        ssize_t count = write(session->master_fd, bytes + offset,
                              (size_t) (length - offset));
        if (count < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (count == 0) break;
        offset += (jsize) count;
    }
    (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);
    return offset;
}

JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronPty_nativeResize(
        JNIEnv *env, jclass clazz, jlong handle, jint rows, jint columns) {
    (void) env;
    (void) clazz;
    struct neutron_pty *session = from_handle(handle);
    if (session == NULL || atomic_load(&session->closed) != 0) return;
    struct winsize window;
    memset(&window, 0, sizeof(window));
    window.ws_row = (unsigned short) (rows < 1 ? 24 : rows > 1000 ? 1000 : rows);
    window.ws_col = (unsigned short) (columns < 1 ? 80 : columns > 1000 ? 1000 : columns);
    (void) ioctl(session->master_fd, TIOCSWINSZ, &window);
    (void) kill(-session->child_pid, SIGWINCH);
}

JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronPty_nativeStop(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    struct neutron_pty *session = from_handle(handle);
    if (session == NULL) return;
    if (atomic_exchange(&session->closed, 1) == 0) {
        (void) kill(-session->child_pid, SIGHUP);
        (void) kill(-session->child_pid, SIGTERM);
    }
}

JNIEXPORT void JNICALL
Java_io_github_alamingamer15ig_neutron_NeutronPty_nativeDestroy(
        JNIEnv *env, jclass clazz, jlong handle) {
    (void) env;
    (void) clazz;
    struct neutron_pty *session = from_handle(handle);
    if (session == NULL) return;
    atomic_store(&session->closed, 1);
    (void) kill(-session->child_pid, SIGKILL);
    close(session->master_fd);

    int status = 0;
    pid_t result;
    do {
        result = waitpid(session->child_pid, &status, 0);
    } while (result < 0 && errno == EINTR);
    free(session);
}
