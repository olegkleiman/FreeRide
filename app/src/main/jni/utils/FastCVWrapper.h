//
// Created by Oleg Kleiman on 22-Aug-15.
//
#include <jni.h>

#ifndef FREERIDE_FASTCVWRAPPER_H
#define FREERIDE_FASTCVWRAPPER_H

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jlong JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_nativeCreateObject
        (JNIEnv *env, jclass jc,
         jstring faceCascadeFileName,
         jstring eyesCascadeFileName);

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_detectFace
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jlong addrFace,
         jint rotation);

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_detectEye
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jlong addrEye,
         jint rotation);

#ifdef __cplusplus
}
#endif

#endif //FREERIDE_FASTCVWRAPPER_H
