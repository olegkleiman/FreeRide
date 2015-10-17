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
//#include <opencv2/gpu/gpu.hpp>
#include <vector>

#include "FastCVWrapper.h"

#ifdef __ANDROID__

#include <android/log.h>
#include <android/bitmap.h>

#endif

using namespace std;
using namespace cv;
//using namespace cv::gpu;
using namespace std::chrono;

#define CVWRAPPER_LOG_TAG    "FR.CV"
#ifdef _DEBUG

#define DPRINTF(...)  __android_log_print(ANDROID_LOG_DEBUG,CVWRAPPER_LOG_TAG,__VA_ARGS__)

#else
#define DPRINTF(...)   //noop
#endif

#define IPRINTF(...)  __android_log_print(ANDROID_LOG_INFO,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#define EPRINTF(...)  __android_log_print(ANDROID_LOG_ERROR,CVWRAPPER_LOG_TAG,__VA_ARGS__)
#define WPRINTF(...)  __android_log_print(ANDROID_LOG_WARN,CVWRAPPER_LOG_TAG,__VA_ARGS__)

Mat roiTemplate;
Mat roiFace;
Rect _faceRect;
int nFoundTemplateCounter = 0;
const int CONSECUTIVE_TEMPLATE_COUNTER = 3;

int nFoundMatchCounter = 0;
const int CONSECUTIVE_MATCH_COUNTER = 10;

struct CascadeAggregator {
    Ptr<CascadeClassifier> FaceClassifier;
    Ptr<CascadeClassifier> EyesClassifier;

    CascadeAggregator(CascadeClassifier *faceClassifier,
                      CascadeClassifier *eyesClassifier,
                      const char *face_cascade_name,
                      const char *eyes_cascade_name)
            : FaceClassifier(faceClassifier), EyesClassifier(eyesClassifier) {

        CV_DbgAssert(FaceClassifier);
        CV_DbgAssert(EyesClassifier);

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

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_MatchTemplate
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jint rotation)
{

    Mat &mRgbaChannel = *(Mat *)addrRgba;
    Mat &mGrayChannel = *(Mat *) addrGray;

    try {

        // Load face cascade
        Ptr<CascadeClassifier> faceClassifier = ((CascadeAggregator *)thiz)->FaceClassifier;
        if( faceClassifier == NULL )
            return false;

        flip(mRgbaChannel, mRgbaChannel, 1); // flip around y-axis: mirror
        flip(mGrayChannel, mGrayChannel, 1);
        Mat tmpMat = mGrayChannel.clone();

        if( rotation == 1)  { // Configuration.ORIENTATION_PORTRAIT
            // In portrait mode, matrix comes inverted relative to top-left corner.
            // So we need to transpose the already flipped mat.
            transpose(mGrayChannel, tmpMat);
            //flip(tmpMat, tmpMat, -1); //transpose+flip(-1)=180
        } else {
            // In landscape mode, matrix comes just flipped.
            // No additional processing is needed because it was flipped already
        }

        // Detect face
        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH; // See more these values
                                                                           // in FindTemplate

        int height = mGrayChannel.rows;
        int facesSize = cvRound(height * 0.4f);

        vector<Rect> faces;
        faceClassifier->detectMultiScale(tmpMat,
                                         faces,
                                         1.2, // How many different sizes of eye to look for
                                        // 1.1 is for good detection
                                        // 1.2 for faster detection
                                         3, // Neighbors : how sure the detector should be that has detected face.
                                        // Set to higher than 3 (default) if you want more reliable eyes
                                        // even if many faces are not included
                                         flags,
                                         Size(facesSize, facesSize));

        if( faces.size() > 0) { // only one region supposed to be found - see flags passed to detectMultiScale()

            Rect faceRect = faces[0];

            Point tl;
            Point br;

            if( rotation == 1) { // Configuration.ORIENTATION_PORTRAIT
                                 // Reverse transpose & flip
                tl.x = faceRect.tl().y; // y --> x
                tl.y = faceRect.tl().x; // x --> y

                br.x = faceRect.br().y; // y --> x
                br.y = faceRect.br().x; // x --> y
            } else {
                tl = faceRect.tl();
                br = faceRect.br();
            }

            rectangle(mRgbaChannel, tl, br,
                      Scalar(0, 255, 0),
                      2);

            faceRect.width = faceRect.width / 2;
            Mat lRoiFace;
            mGrayChannel(faceRect).copyTo(lRoiFace);

            Mat result;
            int result_cols = mGrayChannel.cols + roiTemplate.cols + 1;
            int result_rows = mGrayChannel.rows + roiTemplate.rows + 1;
            result.create(result_rows, result_cols, CV_32FC1);

            int compare_method = TM_CCOEFF_NORMED; //TM_SQDIFF;

            matchTemplate(lRoiFace, roiTemplate, result, compare_method);
            normalize(result, result, 0, 1, NORM_MINMAX, -1, Mat());

            //DPRINTF("Template match");

            double minValue, maxValue;
            Point minLoc, maxLoc;
            Point matchLoc;
            minMaxLoc(result, &minValue, &maxValue, &minLoc, &maxLoc, Mat());
            if( compare_method == TM_SQDIFF || compare_method == TM_SQDIFF_NORMED)
                matchLoc = minLoc;
            else // e.g. TM_CCOEFF_NORMED
                matchLoc = maxLoc;

            rectangle(mGrayChannel,
                      Point(matchLoc.x + faceRect.x,
                            matchLoc.y + faceRect.y ),
                      Point(matchLoc.x + roiTemplate.cols + faceRect.x,
                            matchLoc.y + roiTemplate.rows + faceRect.y),
                      Scalar::all(255), 2, 8, 0);

            if( ++nFoundMatchCounter > CONSECUTIVE_MATCH_COUNTER )
                return true;

        }

        return false;

    } catch(Exception ex) {
        jclass je = env->FindClass("org/opencv/core/CvException");

        if (!je)
            je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, ex.what());

        return false;
    }
}

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_FindTemplate
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jlong addrTemplate,
         jint rotation)
{
    Mat &mRgbaChannel = *(Mat *)addrRgba;
    Mat &mGrayChannel = *(Mat *)addrGray;
    Mat &templateMat = *(Mat *)addrTemplate;

    int rows = mGrayChannel.rows;
    int cols = mGrayChannel.cols;

    DPRINTF("FindTemplate called. Rotation: %d", rotation);

    try {
        // Load face cascade
        Ptr<CascadeClassifier> faceClassifier = ((CascadeAggregator *)thiz)->FaceClassifier;
        if( faceClassifier == NULL ) {
            EPRINTF("Can not load face cascade");
            return false;
        }

        // Load open eye cascade
        Ptr<CascadeClassifier> eyesCascade = ((CascadeAggregator *)thiz)->EyesClassifier;
        if( eyesCascade == NULL ) {
            EPRINTF("Can not load eye cascade");
            return false;
        }

        // RGB matrix is used only for drawing found features' rectangles: faces, eyes
        // Gray matrix is used for processing: it is transposed initially, if needed,
        // and passed to cascade classifiers as input parameter.

        flip(mRgbaChannel, mRgbaChannel, 1); // flip around y-axis: mirror
        flip(mGrayChannel, mGrayChannel, 1);
        //equalizeHist(mGrayChannel, mGrayChannel);
        Mat tmpMat = mGrayChannel.clone();

        // Rotation is a composition of a transpose and flip
        //
        // R(90) = F(x) * T
        // R(-90) = F(y) * T
        //
        if( rotation == 1)  { // Configuration.ORIENTATION_PORTRAIT
            // In portrait mode, matrix comes inverted relative to top-left corner.
            // So we need to transpose the already flipped mat.
            transpose(mGrayChannel, tmpMat);
            //flip(tmpMat, tmpMat, -1); //transpose+flip(-1)=180
        } else {
            // In landscape mode, matrix comes just flipped.
            // No additional processing is needed because it was flipped already
        }

        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH;
        // CASCADE_FIND_BIGGEST_OBJECT tells OpenCV to return only the largest object found
        // Hence the number of objects returned will be either one ore none.
        // CASCADE_DO_ROUGH_SEARCH is used only with CASCADE_FIND_BIGGEST_OBJECT.
        // This flag is used to terminate the search at whatever scale the first candidate is found.

        // Now detect open eyes
        vector<Rect> eyes;
        eyesCascade->detectMultiScale(tmpMat, eyes,
                                      1.2, // How many different sizes of eye to look for
                                            // 1.1 is for good detection
                                            // 1.2 for faster detection
                                      3, // Neighbors : how sure the detector should be that has detected eye.
                                        // Set to higher than 3 (default) if you want more reliable faces
                                        // even if many faces are not included
                                      flags,
                                      Size(140, 140));
        if( eyes.size() > 0 ) {

            //DPRINTF("Eye(s) detected. Cons. Frames: %d:", nFoundTemplateCounter);
            Rect _eyeRect = eyes[0];

            Point tl;
            Point br;

            if( rotation == 1) { // Configuration.ORIENTATION_PORTRAIT
                // Reverse transpose & flip
                tl.x = _eyeRect.tl().y; // y --> x
                tl.y = _eyeRect.tl().x; // x --> y

                br.x = _eyeRect.br().y; // y --> x
                br.y = _eyeRect.br().x; // x --> y
            } else {
                tl = _eyeRect.tl();
                br = _eyeRect.br();
            }

            rectangle(mRgbaChannel, tl, br,
                      Scalar(0, 255, 0),
                      2);

            if( ++nFoundTemplateCounter > CONSECUTIVE_TEMPLATE_COUNTER ) {

                //Rect eyeRect = Rect(tl, br);
                tmpMat(_eyeRect).copyTo(templateMat);
                //tmpMat(eyeRect).copyTo(templateMat);

                nFoundTemplateCounter = 0;
                return true;
            }


        }  else { // we are looking for consecutive frames
            nFoundTemplateCounter = 0;
            DPRINTF("Reset");
        }

        return false;
    } catch(Exception ex) {
        DPRINTF(ex.what());
        jclass je = env->FindClass("org/opencv/core/CvException");

         if (!je)
            je = env->FindClass("java/lang/Exception");
        env->ThrowNew(je, ex.what());
    }
}

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_FindTemplateX
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jlong addrTemplate,
         jint rotation)
{
    Mat &mRgbaChannel = *(Mat *)addrRgba;
    Mat &mGrayChannel = *(Mat *)addrGray;
    Mat &templateMat = *(Mat *)addrTemplate;

    int rows = mGrayChannel.rows;
    int cols = mGrayChannel.cols;

    DPRINTF("FindTemplate called. Rotation: %d", rotation);

    try {
        // Load face cascade
        Ptr<CascadeClassifier> faceClassifier = ((CascadeAggregator *)thiz)->FaceClassifier;
        if( faceClassifier == NULL ) {
            EPRINTF("Can not load face cascade");
            return false;
        }

        // Load open eye cascade
        Ptr<CascadeClassifier> eyesCascade = ((CascadeAggregator *)thiz)->EyesClassifier;
        if( eyesCascade == NULL ) {
            EPRINTF("Can not load eye cascade");
            return false;
        }

        // RGB matrix is used only for drawing found features' rectangles: faces, eyes
        // Gray matrix is used for processing: it is transposed initially, if needed,
        // and passed to cascade classifiers as input parameter.

        flip(mRgbaChannel, mRgbaChannel, 1); // flip around y-axis: mirror
        flip(mGrayChannel, mGrayChannel, 1);
        //equalizeHist(mGrayChannel, mGrayChannel);
        Mat tmpMat = mGrayChannel.clone();

        // Rotation is a composition of a transpose and flip
        //
        // R(90) = F(x) * T
        // R(-90) = F(y) * T
        //
        if( rotation == 1)  { // Configuration.ORIENTATION_PORTRAIT
            // In portrait mode, matrix comes inverted relative to top-left corner.
            // So we need to transpose the already flipped mat.
            transpose(mGrayChannel, tmpMat);
            //flip(tmpMat, tmpMat, -1); //transpose+flip(-1)=180
        } else {
            // In landscape mode, matrix comes just flipped.
            // No additional processing is needed because it was flipped already
         }

        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH;
        // CASCADE_FIND_BIGGEST_OBJECT tells OpenCV to return only the largest object found
        // Hence the number of objects returned will be either one ore none.
        // CASCADE_DO_ROUGH_SEARCH is used only with CASCADE_FIND_BIGGEST_OBJECT.
        // This flag is used to terminate the search at whatever scale the first candidate is found.

        // Now detect open eyes
//        vector<Rect> eyes;
//        eyesCascade->detectMultiScale(tmpMat, eyes,
//                                      1.2, // How many different sizes of eye to look for
//                                           // 1.1 is for good detection
//                                           // 1.2 for faster detection
//                                      3, // Neighbors : how sure the detector should be that has detected eye.
//                                         // Set to higher than 3 (default) if you want more reliable faces
//                                         // even if many faces are not included
//                                      flags,
//                                      Size(140, 140));
//            if( eyes.size() > 0 ) {
//                DPRINTF("Eye(s) detected");
////
////                if( ++nFoundTemplateCounter > CONSECUTIVE_TEMPLATE_COUNTER ) {
////
//                Rect _eyeRect = eyes[0];
//
//                Point tl;
//                Point br;
//
//                if( rotation == 1) { // Configuration.ORIENTATION_PORTRAIT
//                    // Reverse transpose & flip
//                    tl.x = _eyeRect.tl().y; // y --> x
//                    tl.y = _eyeRect.tl().x; // x --> y
//
//                    br.x = _eyeRect.br().y; // y --> x
//                    br.y = _eyeRect.br().x; // x --> y
//                } else {
//                    tl = _eyeRect.tl();
//                    br = _eyeRect.br();
//                }
//
//                //rectangle(mRgbaChannel, _eyeRect, Scalar::all(255), 1, 8, 0);
//                rectangle(mRgbaChannel, tl, br,
//                          //Scalar::all(255),
//                          Scalar(0, 255, 0),
//                          2);
////
////                    return true;
////                }
////
////            } else { // we are looking for consecutive frames
////                nFoundTemplateCounter = 0;
//            }
//
//        return false;

        // Detect face
        vector<Rect> faces;
        int faceSize = (int)round(rows * 0.3f);

        faceClassifier->detectMultiScale(tmpMat,
                                     faces,
                                     1.2, // How many different sizes of eye to look for
                                          // 1.1 is for good detection
                                          // 1.2 for faster detection
                                      3, // Neighbors : how sure the detector should be that has detected face.
                                         // Set to higher than 3 (default) if you want more reliable eyes
                                         // even if many faces are not included
                                      flags,
                                      Size(faceSize, faceSize));

        if( faces.size() > 0) { // only one region supposed to be found - see flags passed to detectMultiScale()

            _faceRect = faces[0];
            DPRINTF("Face region: x: %d y: %d height: %d width: %d",
                    _faceRect.x, _faceRect.y, _faceRect.height, _faceRect.width);

            Point tl;
            Point br;

            if( rotation == 1) { // Configuration.ORIENTATION_PORTRAIT
                // Reverse transpose & flip
                tl.x = _faceRect.tl().y; // y --> x
                tl.y = _faceRect.tl().x; // x --> y

                br.x = _faceRect.br().y; // y --> x
                br.y = _faceRect.br().x; // x --> y
            } else {
                tl = _faceRect.tl();
                br = _faceRect.br();
            }

            rectangle(mRgbaChannel,
                      tl, br,
                      Scalar::all(255), 2);

            mGrayChannel(_faceRect).copyTo(roiFace);
            equalizeHist(roiFace, roiFace);

            // Now detect open eyes
            vector<Rect> eyes;
            eyesCascade->detectMultiScale(roiFace, eyes,
                                        1.2,
                                        3,
                                        flags,
                                        Size(120, 120));
            if( eyes.size() > 0 ) {

                DPRINTF("Eye(s) detected");

                if( ++nFoundTemplateCounter > CONSECUTIVE_TEMPLATE_COUNTER ) {

                    Rect _eyeRect = eyes[0];
                    //DPRINTF("Eye region: x: %d y: %d height: %d width: %d",
                    //        _eyeRect.x, _eyeRect.y, _eyeRect.height, _eyeRect.width);
                    roiFace(_eyeRect).copyTo(roiTemplate);
                    roiFace(_eyeRect).copyTo(templateMat);

                    rectangle(mGrayChannel,
                              Point(_faceRect.x + _eyeRect.x, _faceRect.y + _eyeRect.y),
                              Point(_faceRect.x + _eyeRect.x + _eyeRect.width,
                                    _faceRect .y + _eyeRect.y + _eyeRect.height),
                              Scalar::all(240),
                              1, 8, 0);

                    return true;
               }

            } else { // we are looking for consecutive frames
                nFoundTemplateCounter = 0;
            }

        }

        tmpMat.release();

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
