//
// Created by Oleg Kleiman on 22-Aug-15.
//
#include <jni.h>

#include <time.h>
#include <chrono>
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
using namespace std::chrono;

#define CVWRAPPER_LOG_TAG    "fastcvWrapper"
#ifdef _DEBUG

#define DPRINTF(...)  __android_log_print(ANDROID_LOG_DEBUG,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#else
#define DPRINTF(...)   //noop
#endif
#define IPRINTF(...)  __android_log_print(ANDROID_LOG_INFO,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#define EPRINTF(...)  __android_log_print(ANDROID_LOG_ERROR,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#define WPRINTF(...)  __android_log_print(ANDROID_LOG_WARN,CVWRAPPER_LOG_TAG,__VA_ARGS__)

Mat roiTemplate;
int nFoundTemplateCounter = 0;
const int CONSECUTIVE_TEMPLATE_COUNTER = 3;

struct CascadeAggregator {
    Ptr<CascadeClassifier> FaceClassifier;
    Ptr<CascadeClassifier> EyesClassifier;

    CascadeAggregator(CascadeClassifier *faceClassifier,
                      CascadeClassifier *eyesClassifier,
                      const char *face_cascade_name,
                      const char *eyes_cascade_name)
            : FaceClassifier(faceClassifier), EyesClassifier(eyesClassifier) {
        ifstream f(face_cascade_name);
        if( !f.good() ) {
            DPRINTF("Can not access face cascade file");
        }
        FaceClassifier->load(face_cascade_name);

        ifstream e(eyes_cascade_name);
        if( !e.good() ) {
            DPRINTF("Can not access eyes cascade file");
        }
        EyesClassifier->load(eyes_cascade_name);
    }
};

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    DPRINTF("JNI_VERSION_1_4");
    return JNI_VERSION_1_4;
}

JNIEXPORT jlong JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_nativeCreateObject
        (JNIEnv *env, jclass jc,
         jstring faceCascadeFileName,
         jstring eyesCascadeFileName)
{
    const char *face_cascade_file_name = env->GetStringUTFChars(faceCascadeFileName, NULL);
    const char *eyes_cascade_file_name = env->GetStringUTFChars(eyesCascadeFileName, NULL);

    CascadeClassifier *face_cascade_classifier = new CascadeClassifier();
    CascadeClassifier *eyes_cascade_classifier = new CascadeClassifier();

    CascadeAggregator *cAggregator =  new CascadeAggregator(face_cascade_classifier,
                          eyes_cascade_classifier,
                          face_cascade_file_name,
                          eyes_cascade_file_name);


    env->ReleaseStringUTFChars(faceCascadeFileName, face_cascade_file_name);
    env->ReleaseStringUTFChars(eyesCascadeFileName, eyes_cascade_file_name);

    return (jlong)cAggregator;

}

JNIEXPORT int JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_MatchTemplate
        (JNIEnv *env, jclass jc, jlong addrGray)
{

    Mat &frame = *(Mat *) addrGray;

    try {
        Mat result;
        int result_cols = frame.cols + roiTemplate.cols + 1;
        int result_rows = frame.rows + roiTemplate.rows + 1;
        result.create(result_rows, result_cols, CV_32FC1);

        matchTemplate(frame, roiTemplate, result, TM_CCOEFF_NORMED);
        normalize(result, result, 0, 1, NORM_MINMAX, -1, Mat());

        double minValue, maxValue;
        Point minLoc, maxLoc;
        Point matchLoc;
        minMaxLoc(result, &minValue, &maxValue, &minLoc, &maxLoc, Mat());
        matchLoc = maxLoc; // because of TM_CCOEFF_NORMED

        rectangle(frame, matchLoc,
                  Point(matchLoc.x + roiTemplate.cols, matchLoc.y + roiTemplate.rows),
                  Scalar::all(255), 2, 8, 0);

    } catch(Exception ex) {
        jclass je = env->FindClass("org/opencv/core/CvException");

        if (!je)
            je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, ex.what());
    }
}

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_FindTemplate
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrGray)
{
    Mat &mGrayChannel = *(Mat *)addrGray;

    try {
        // Load face cascade
        Ptr<CascadeClassifier> faceClassifier = ((CascadeAggregator *)thiz)->FaceClassifier;
        if( faceClassifier == NULL )
            return false;

        // Load open eye cascade
        Ptr<CascadeClassifier> eyesCascade = ((CascadeAggregator *)thiz)->EyesClassifier;
        if( eyesCascade == NULL )
            return false;

        flip(mGrayChannel, mGrayChannel, 1);
        return false;

        // Detect face
        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH;
        vector<Rect> faces;
        faceClassifier->detectMultiScale(mGrayChannel,
                                     faces,
                                     1.2, // How many different sizes of eye to look for
                                          // 1.1 is for good detection
                                          // 1.2 for faster detection
                                      2, // Neighbors : how sure the detector should be that has detected face.
                                         // Set to higher than 3 (default) if you want more reliable faces
                                         // even if many faces are not included
                                      flags,
                                      Size(40, 40));

        if( faces.size() > 0) { // only one region supposed to be found - see flags passed to detectMultiScale()

            Mat roiFace;

            Rect _rect = faces[0];
            rectangle(mGrayChannel, _rect,
                      Scalar::all(255),
                      1, 8, 0);

            mGrayChannel(_rect).copyTo(roiFace);
            equalizeHist(roiFace, roiFace);

            // Now detect open eyes
            vector<Rect> eyes;
            eyesCascade->detectMultiScale(roiFace, eyes,
                                        1.2, 2, flags,
                                        Size(20, 20));
            if( eyes.size() > 0 ) {

                if( ++nFoundTemplateCounter > CONSECUTIVE_TEMPLATE_COUNTER ) {

                    Rect _eyeRect = eyes[0];
                    roiFace(_eyeRect).copyTo(roiTemplate);

                    //rectangle(roiFace, _eyeRect, Scalar::all(255), 1, 8, 0);

                    return true;
                }

            } else { // we are looking for consecutive frames
                nFoundTemplateCounter = 0;
            }

        }

    } catch(Exception ex) {
        jclass je = env->FindClass("org/opencv/core/CvException");

        if (!je)
            je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, ex.what());
    }

    return false;
}

JNIEXPORT int JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_DetectFaces
        (JNIEnv *env, jclass jc, jlong addrGray, jstring face_cascade_name)
{
    try {

        milliseconds start = duration_cast<milliseconds>(
                system_clock::now().time_since_epoch());

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

        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH; //0 | CV_HAAR_SCALE_IMAGE

        face_cascade.detectMultiScale(mGrayChannel, faces,
                                      1.1, // 1.1 is for good detection
                                      // 1.2 for faster detection
                                      2, // Neighbors
                                      flags,
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

        milliseconds finish = duration_cast<milliseconds>(
                system_clock::now().time_since_epoch());

        long long int dur = duration_cast<milliseconds>(finish - start).count();
        DPRINTF("Frame processes for %llu", dur);

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
