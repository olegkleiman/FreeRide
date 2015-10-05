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

    public boolean findTemplate(long matAddrGray, long matAddrTemplate) {
        return FindTemplate(mNativeObj, matAddrGray, matAddrTemplate);
    }
    public int matchTemplate(long matAddrRgba) {
        return MatchTemplate(matAddrRgba);
    }

    // Internal native methods

    private native long nativeCreateObject(String faceCascadeFileName, String eyesCascadeFileName);
    private native boolean FindTemplate(long thiz, long matAddrGrey, long matAddrTemplate);
    private native int MatchTemplate(long matAddrRgba);
    private native int DetectFaces(long matAddrRgba, String face_cascade_name);

}
