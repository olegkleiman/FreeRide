//
// Created by Oleg on 22-Aug-15.
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

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_FindTemplate
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jlong addrTemplate,
         jint rotation);

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_MatchTemplate
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jint rotation);

JNIEXPORT int JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_DetectFaces
        (JNIEnv *env, jclass jc,
         jlong addrRgba, jstring face_cascade_name);

#ifdef __cplusplus
}
#endif

#endif //FREERIDE_FASTCVWRAPPER_H
