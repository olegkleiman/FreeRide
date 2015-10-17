package com.labs.okey.freeride.fastcv;

/**
 * Created by Oleg Kleiman on 22-Aug-15.
 */
public class FastCVWrapper {

    private long mNativeObj = 0;

    // Public interface

    public FastCVWrapper(String faceCascadeFilePath,
                         String eyesCascadeFilePath){
        mNativeObj = nativeCreateObject(faceCascadeFilePath, eyesCascadeFilePath);
    }

    public boolean findTemplate(long matAddrRgba, long matAddrGray, long matAddrTemplate, int rotation) {
        return FindTemplate(mNativeObj, matAddrRgba, matAddrGray, matAddrTemplate, rotation);
    }
    public boolean matchTemplate(long matAddrRgba, long matAddrGray, int rotation) {
        return MatchTemplate(mNativeObj, matAddrRgba, matAddrGray, rotation);
    }

    // Internal native methods

    private native long nativeCreateObject(String faceCascadeFileName, String eyesCascadeFileName);
    private native boolean FindTemplate(long thiz, long matAddrRgba, long matAddrGrey, long matAddrTemplate, int rotation);
    private native boolean MatchTemplate(long thiz, long matAddrRgba, long matAddrGrey, int rotation);
    private native int DetectFaces(long matAddrRgba, String face_cascade_name);

}
