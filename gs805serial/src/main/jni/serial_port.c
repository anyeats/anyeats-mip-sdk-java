/*
 * JNI native implementation for UART serial port access.
 * Opens hardware UART ports (e.g., /dev/ttyS*) using termios for
 * embedded Android devices.
 */

#include <stdio.h>
#include <jni.h>
#include <termios.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <errno.h>

static speed_t getBaudRate(jint baudRate) {
    switch (baudRate) {
        case 9600:   return B9600;
        case 19200:  return B19200;
        case 38400:  return B38400;
        case 57600:  return B57600;
        case 115200: return B115200;
        default:     return B9600;
    }
}

JNIEXPORT jobject JNICALL
Java_kr_co_anyeats_gs805serial_serial_SerialPort_nativeOpen(
        JNIEnv *env,
        jobject thiz,
        jstring path,
        jint baudRate,
        jint dataBits,
        jint stopBits,
        jint parity) {

    const char *devicePath = (*env)->GetStringUTFChars(env, path, NULL);
    if (devicePath == NULL) {
        jclass ioEx = (*env)->FindClass(env, "java/io/IOException");
        (*env)->ThrowNew(env, ioEx, "Failed to get device path string");
        return NULL;
    }

    /* Open the serial port */
    int fd = open(devicePath, O_RDWR | O_NOCTTY | O_NONBLOCK);
    if (fd == -1) {
        char errMsg[256];
        snprintf(errMsg, sizeof(errMsg), "Failed to open %s: %s", devicePath, strerror(errno));
        (*env)->ReleaseStringUTFChars(env, path, devicePath);
        jclass ioEx = (*env)->FindClass(env, "java/io/IOException");
        (*env)->ThrowNew(env, ioEx, errMsg);
        return NULL;
    }

    (*env)->ReleaseStringUTFChars(env, path, devicePath);

    /* Configure the serial port with termios */
    struct termios cfg;
    if (tcgetattr(fd, &cfg) != 0) {
        close(fd);
        jclass ioEx = (*env)->FindClass(env, "java/io/IOException");
        (*env)->ThrowNew(env, ioEx, "tcgetattr() failed");
        return NULL;
    }

    /* Raw mode */
    cfmakeraw(&cfg);

    /* Baud rate */
    speed_t speed = getBaudRate(baudRate);
    cfsetispeed(&cfg, speed);
    cfsetospeed(&cfg, speed);

    /* Data bits */
    cfg.c_cflag &= ~CSIZE;
    switch (dataBits) {
        case 5: cfg.c_cflag |= CS5; break;
        case 6: cfg.c_cflag |= CS6; break;
        case 7: cfg.c_cflag |= CS7; break;
        case 8:
        default: cfg.c_cflag |= CS8; break;
    }

    /* Stop bits */
    if (stopBits == 2) {
        cfg.c_cflag |= CSTOPB;
    } else {
        cfg.c_cflag &= ~CSTOPB;
    }

    /* Parity */
    switch (parity) {
        case 0: /* None */
            cfg.c_cflag &= ~PARENB;
            break;
        case 1: /* Odd */
            cfg.c_cflag |= PARENB;
            cfg.c_cflag |= PARODD;
            break;
        case 2: /* Even */
            cfg.c_cflag |= PARENB;
            cfg.c_cflag &= ~PARODD;
            break;
        default:
            cfg.c_cflag &= ~PARENB;
            break;
    }

    /* Enable receiver, local mode */
    cfg.c_cflag |= (CLOCAL | CREAD);

    /* No hardware flow control */
    cfg.c_cflag &= ~CRTSCTS;

    /* No software flow control */
    cfg.c_iflag &= ~(IXON | IXOFF | IXANY);

    /* VMIN=1, VTIME=0: block until at least 1 byte is available */
    cfg.c_cc[VMIN] = 1;
    cfg.c_cc[VTIME] = 0;

    if (tcsetattr(fd, TCSANOW, &cfg) != 0) {
        close(fd);
        jclass ioEx = (*env)->FindClass(env, "java/io/IOException");
        (*env)->ThrowNew(env, ioEx, "tcsetattr() failed");
        return NULL;
    }

    /* Flush any pending I/O */
    tcflush(fd, TCIOFLUSH);

    /* Create a FileDescriptor object and set its 'fd' field */
    jclass fdClass = (*env)->FindClass(env, "java/io/FileDescriptor");
    jmethodID fdInit = (*env)->GetMethodID(env, fdClass, "<init>", "()V");
    jobject fileDescriptor = (*env)->NewObject(env, fdClass, fdInit);

    jfieldID fdField = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
    (*env)->SetIntField(env, fileDescriptor, fdField, fd);

    return fileDescriptor;
}

JNIEXPORT void JNICALL
Java_kr_co_anyeats_gs805serial_serial_SerialPort_nativeClose(
        JNIEnv *env,
        jobject thiz) {

    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fdFieldId = (*env)->GetFieldID(env, cls, "fileDescriptor", "Ljava/io/FileDescriptor;");
    jobject fileDescriptor = (*env)->GetObjectField(env, thiz, fdFieldId);

    if (fileDescriptor == NULL) {
        return;
    }

    jclass fdClass = (*env)->FindClass(env, "java/io/FileDescriptor");
    jfieldID descriptorField = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
    int fd = (*env)->GetIntField(env, fileDescriptor, descriptorField);

    if (fd != -1) {
        close(fd);
    }
}
