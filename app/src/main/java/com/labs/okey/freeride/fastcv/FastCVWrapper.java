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

    public boolean DetectFace(long matAddrRgba, long matAddrGray, long matAddrFace, int rotation){
        return detectFace(mNativeObj, matAddrRgba, matAddrGray, matAddrFace, rotation);
    }
    public boolean DetectEye(long matAddrRgba, long matAddrGray, long matAddrEye, int rotation){
        return detectEye(mNativeObj, matAddrRgba, matAddrGray, matAddrEye, rotation);
    }

    // Internal native methods

    private native long nativeCreateObject(String faceCascadeFileName, String eyesCascadeFileName);
    private native boolean detectFace(long thiz, long matAddrRgba, long matAddrGrey,
                                      long matAddrFace,
                                      int rotation);
    private native boolean detectEye(long thiz, long matAddrRgba, long matAddrGrey, long matAddrEye, int rotation);

}
