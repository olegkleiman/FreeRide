//
// Created by Oleg Kleiman on 22-Aug-15.
//
#include <jni.h>

#include <time.h>
#include <fstream>

#include <opencv2/core.hpp>
#include <opencv2/imgproc.hpp>
#include <opencv2/features2d.hpp>
#include <opencv2/objdetect.hpp>
#include <vector>

#include "FastCVWrapper.h"

#ifdef __ANDROID__

#include <android/log.h>
#include <android/bitmap.h>

#endif

using namespace std;
using namespace cv;

#define CVWRAPPER_LOG_TAG    "fastcvWrapper"
#ifdef _DEBUG

#define DPRINTF(...)  __android_log_print(ANDROID_LOG_DEBUG,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#else
#define DPRINTF(...)   //noop
#endif
#define IPRINTF(...)  __android_log_print(ANDROID_LOG_INFO,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#define EPRINTF(...)  __android_log_print(ANDROID_LOG_ERROR,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#define WPRINTF(...)  __android_log_print(ANDROID_LOG_WARN,CVWRAPPER_LOG_TAG,__VA_ARGS__)

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    DPRINTF("JNI_VERSION_1_4");
    return JNI_VERSION_1_4;
}

JNIEXPORT int JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_DetectFaces
        (JNIEnv *env, jclass jc, jlong addrGray, jstring face_cascade_name)
{
    try {

        CascadeClassifier face_cascade;
        vector<Rect> faces;

        const char *cascade_name = env->GetStringUTFChars(face_cascade_name, NULL);
        ifstream f(cascade_name);
        if( !f.good() ) {
            DPRINTF("Can not access cascade file");
            return 0;
        }

        if( !face_cascade.load(cascade_name) ) {
            DPRINTF("Can not load cascade");
            return 0;
        }
        env->ReleaseStringUTFChars(face_cascade_name, cascade_name);

        Mat &mGrayChannel = *(Mat *)addrGray;

        flip(mGrayChannel, mGrayChannel, 1);
        //equalizeHist(mGrayChannel, mGrayChannel);

        face_cascade.detectMultiScale(mGrayChannel, faces,
                                      1.1, // 1.1 is for good detection
                                      // 1.2 for faster detection
                                      2, // Neighbors
                                      0 | CV_HAAR_SCALE_IMAGE,
                                      Size(40, 40));

        int faces_size = faces.size();
        if( faces_size > 0 ) {
            DPRINTF("Detected %d faces", faces_size);

            for(int i = 0; i < faces_size; i++) {
                Rect _rect = faces[i];

                rectangle(mGrayChannel, _rect,
                          Scalar(255, 255, 255),
                          1, 8, 0);
            }
        }

        return faces_size;

        return 0;

    } catch(Exception& e) {
        jclass je = env->FindClass("org/opencv/core/CvException");

        if(!je)
            je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, e.what());
    } catch( ... ) {
        jclass je = env->FindClass("org/opencv/core/Exception");
        env->ThrowNew(je, "Unknown exception in JNI:DetectFaces");
    }

}
