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

#include "FastCVWrapper.h"

#ifdef __ANDROID__

#include <android/log.h>

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
const int NO_EYE_PRESENT_COUNTER = 2;

Scalar FACE_RECT_COLOR = Scalar(33, 150, 243);
Scalar EYE_RECT_COLOR = Scalar(255, 64, 129);

int nNotFoundFacesForMatch = 0;
const int NO_FACES_FOR_MATCH = 5;

int nFoundMatchCounter = 0;
const int CONSECUTIVE_MATCH_COUNTER = 10;

int xShift = 0;
int yShift = 0;

//
// Helper functions
//
void normalMatch(Mat &src, Mat& _template, Mat& result, int compare_method);
void fastMatch(Mat &src, Mat& _template, Mat& result, int maxlevel, int compare_method);

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

void throwJavaException(JNIEnv *env, const char *msg){

    jclass je = env->FindClass("org/opencv/core/CvException");

    if (!je)
        je = env->FindClass("java/lang/Exception");
    env->ThrowNew(je, msg);
}

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


//
// Return: -1 - reset (no faces for 5 consecutive frames)
//          0 - no match
//          1 - match
//

JNIEXPORT int JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_MatchTemplate
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrGray,
         jint rotation)
{
    Mat &mRgbaChannel = *(Mat *)addrRgba;
    Mat &mGrayChannel = *(Mat *) addrGray;

    flip(mRgbaChannel, mRgbaChannel, 1); // flip around y-axis: mirror
    flip(mGrayChannel, mGrayChannel, 1);

    Mat tmpMat = mGrayChannel.clone(); // work-area matrix
    equalizeHist(tmpMat, tmpMat);

    try {
        // Load face cascade
        Ptr<CascadeClassifier> faceClassifier = ((CascadeAggregator *) thiz)->FaceClassifier;
        if (faceClassifier == NULL)
            return 0;

        // Detect face
        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH; // See more these values
                                                                            // in FindTemplate

        int height = tmpMat.rows;
        int facesSize = 200;// cvRound(height * 0.4f);

        vector<Rect> faces;
        faceClassifier->detectMultiScale(tmpMat,
                                         faces,
                                         1.1, // How many different sizes of eye to look for
                                                // 1.1 is for good detection
                                                // 1.2 for faster detection
                                         3, // Neighbors : how sure the detector should be that has detected face.
                                                // Set to higher than 3 (default) if you want more reliable eyes
                                                // even if many faces are not included
                                         flags,
                                         Size(facesSize, facesSize));

        if( faces.size() > 0) { // only one region supposed to be found - see flags passed to detectMultiScale()

            DPRINTF("Face detected");

            Rect _faceRect = faces[0];

            rectangle(mRgbaChannel, _faceRect,
                      Scalar(0, 255, 0), 2, LINE_8);

            Mat lRoiFace;
            tmpMat(_faceRect).copyTo(lRoiFace);

            Mat result;
            int compare_method = CV_TM_CCOEFF_NORMED; //TM_SQDIFF;
//            int result_cols = mGrayChannel.cols + roiTemplate.cols + 1;
//            int result_rows = mGrayChannel.rows + roiTemplate.rows + 1;
//            result.create(result_rows, result_cols, CV_32FC1);
//
//            normalMatch(lRoiFace, roiTemplate, result, compare_method);
            fastMatch(lRoiFace, roiTemplate, result, 2, compare_method);

            double minValue, maxValue;
            Point minLoc, maxLoc;
            Point matchLoc;
            minMaxLoc(result, &minValue, &maxValue, &minLoc, &maxLoc, Mat());
            if( compare_method == CV_TM_SQDIFF || compare_method == CV_TM_CCOEFF_NORMED)
                matchLoc = minLoc;
            else // e.g. TM_CCOEFF_NORMED
                matchLoc = maxLoc;

            rectangle(mRgbaChannel,
                      Point(matchLoc.x + _faceRect.x,
                            matchLoc.y + _faceRect.y),
                      Point(matchLoc.x + _faceRect.x + roiTemplate.cols,
                            matchLoc.y + _faceRect.y + roiTemplate.rows),
                      Scalar::all(255), 2, LINE_8);

        } else { // No eye found
            if( ++nFoundTemplateCounter >= NO_EYE_PRESENT_COUNTER ) {
                return 1;
            }
        }

        tmpMat.release();

    } catch(Exception ex) {

        const char *msg = ex.what();
        DPRINTF(msg);
        throwJavaException(env, msg);

    }

    return 0;
}


void normalMatch(Mat &src, Mat& _template, Mat& result, int compare_method){

    matchTemplate(src, _template, result, compare_method);
    normalize(result, result, 0, 1, NORM_MINMAX, -1, Mat());
}

void fastMatch(Mat &src,        //
               Mat& _template,
               Mat& result,
               int maxlevel,
               int compare_method){

    vector<Mat> refs, tpls, results;

    // Build Gaussian pyramid
    buildPyramid(src, refs, maxlevel); // recursively apply pyrDown()
    buildPyramid(_template, tpls, maxlevel);

    Mat ref, tpl, res;
    for(int level = maxlevel; level >= 0; level--) {
        ref = refs[level];
        tpl = tpls[level];
        res = Mat::zeros(ref.size() + Size(1,1) - tpl.size(), CV_32FC1);

        if( level == maxlevel ) {
            matchTemplate(ref, tpl, res, compare_method);
        } else {

            // On the next layers, template matching is performed on pre-defined
            // ROI areas. We define ROI using the template matching result
            // from the previous layer.

            Mat mask;
            pyrUp(results.back(), mask);

            Mat mask8u;
            mask.convertTo(mask8u, CV_8U);

            // Find matches from previous layer
            vector<vector<Point>> contours;
            findContours(mask8u, contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);

            // Use the contours to define ROI and
            // perform template matching on the areas.
            for(int i = 0; i < contours.size(); i++) {
                Rect r = boundingRect(contours[i]);
                matchTemplate(
                        ref(r + (tpl.size() - Size(1,1))),
                        tpl,
                        res(r),
                        compare_method);
            }
        }

        // Only keep good matches
        threshold(res, res, 0.94, 1., CV_THRESH_TOZERO);
        results.push_back(res);

    }

    res.copyTo(result);
}

JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_detectFace
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba, // assume it is flipped around y-axis
         jlong addrGray,
         jlong addrFace,
         jint rotation)
{
    Mat &mRgbaChannel = *(Mat *)addrRgba;
    Mat &mGrayChannel = *(Mat *)addrGray;
    Mat &faceMat = *(Mat *)addrFace;

    try {
        // Load face cascade
        Ptr<CascadeClassifier> faceClassifier = ((CascadeAggregator *) thiz)->FaceClassifier;
        if (faceClassifier == NULL) {
            EPRINTF("Can not load face cascade");
            return false;
        }

        // RGB matrix is used only for drawing found features' rectangles: faces, eyes
        // Gray matrix is used for processing: it is transposed initially, if needed,
        // and passed to cascade classifiers as input parameter.

        //flip(mRgbaChannel, mRgbaChannel, 1); // flip around y-axis: mirror
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

        int height = mGrayChannel.rows;
        int facesSize = cvRound(height * 0.4f);

        // Now detect faces
        vector<Rect> faces;
        faceClassifier->detectMultiScale(tmpMat, faces,
                                         1.2, // How many different sizes of eye to look for
                                                // 1.1 is for good detection
                                                // 1.2 for faster detection
                                         3, // Neighbors : how sure the detector should be that has detected eye.
                                            // Set to higher than 3 (default) if you want more reliable faces
                                            // even if many faces are not included
                                         flags,
                                         Size(facesSize, facesSize));
        if( faces.size() > 0 ) {
            DPRINTF("Face detected");

            Rect _faceRect = faces[0];

            Point tl;
            Point br;

            if( rotation == 1) { // Configuration.ORIENTATION_PORTRAIT
                // Reverse transpose & flip because the rectangle
                // be shown on original (mRgbaChannel) matrix
                tl.x = _faceRect.tl().y; // y --> x
                tl.y = _faceRect.tl().x; // x --> y

                br.x = _faceRect.br().y; // y --> x
                br.y = _faceRect.br().x; // x --> y

                xShift = _faceRect.x;
                yShift = _faceRect.y;

                Rect faceRect = Rect(tl, br);

                //faceRect.width = faceRect.width /2;

                rectangle(mRgbaChannel, faceRect,
                          FACE_RECT_COLOR, 2);

                tmpMat(_faceRect).copyTo(faceMat);

            } else {
                tl = _faceRect.tl();
                br = _faceRect.br();

                xShift = _faceRect.x;
                yShift = _faceRect.y;

                Rect faceRect = Rect(tl, br);

                //faceRect.height = faceRect.height /2;

                rectangle(mRgbaChannel, faceRect,
                          FACE_RECT_COLOR, 2);

                tmpMat(faceRect).copyTo(faceMat);
            }

            return true;
        }

    } catch(Exception ex) {
        const char *msg = ex.what();
        DPRINTF(msg);
        throwJavaException(env, msg);
    }


    return false;
}


JNIEXPORT bool JNICALL Java_com_labs_okey_freeride_fastcv_FastCVWrapper_detectEye
        (JNIEnv *env, jclass jc,
         jlong thiz,
         jlong addrRgba,
         jlong addrFace,
         jlong addrEye,
         jint rotation)
{
    Mat &mRgbaChannel = *(Mat *)addrRgba;
    Mat &mFaceChannel = *(Mat *)addrFace;
    Mat &eyeMat = *(Mat *)addrEye;


    try {
        // Load eye cascade
        Ptr<CascadeClassifier> eyesCascade = ((CascadeAggregator *)thiz)->EyesClassifier;
        if( eyesCascade == NULL ) {
            EPRINTF("Can not load eye cascade");
            return false;
        }

        // RGB matrix is used only for drawing found features' rectangles: faces, eyes
        // Gray matrix is used for processing: it is transposed initially, if needed,
        // and passed to cascade classifiers as input parameter.

        //flip(mRgbaChannel, mRgbaChannel, 1); // flip around y-axis: mirror
        flip(mFaceChannel, mFaceChannel, 1);
        //equalizeHist(mGrayChannel, mGrayChannel);
        Mat tmpMat = mFaceChannel.clone();

        int flags = CASCADE_FIND_BIGGEST_OBJECT | CASCADE_DO_ROUGH_SEARCH;
        // CASCADE_FIND_BIGGEST_OBJECT tells OpenCV to return only the largest object found
        // Hence the number of objects returned will be either one ore none.
        // CASCADE_DO_ROUGH_SEARCH is used only with CASCADE_FIND_BIGGEST_OBJECT.
        // This flag is used to terminate the search at whatever scale the first candidate is found.

        // Now detect open eyes
        vector<Rect> eyes;
        eyesCascade->detectMultiScale(tmpMat, eyes,
                                      1.1, // How many different sizes of eye to look for
                                            // 1.1 is for good detection
                                            // 1.2 for faster detection
                                      3, // Neighbors : how sure the detector should be that has detected eye.
                                            // Set to higher than 3 (default) if you want more reliable matches
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
                tl.x = yShift + _eyeRect.tl().y; // y --> x
                tl.y = xShift + _eyeRect.tl().x; // x --> y

                br.x = yShift + _eyeRect.br().y; // y --> x
                br.y = xShift + _eyeRect.br().x; // x --> y

            } else {

                tl.x = xShift + _eyeRect.x;
                tl.y = yShift + _eyeRect.y;

                br.x = xShift + _eyeRect.width + _eyeRect.x;
                br.y = yShift + _eyeRect.height + _eyeRect.y;
            }

            rectangle(mRgbaChannel, tl, br,
                      EYE_RECT_COLOR, 2);

            mFaceChannel(_eyeRect).copyTo(eyeMat);
            //mRgbaChannel(_eyeRect).copyTo(eyeMat);

            return true;
        }

    } catch(Exception ex) {
        const char *msg = ex.what();
        DPRINTF(msg);
        throwJavaException(env, msg);
    }

    return false;
}

