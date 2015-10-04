package com.labs.okey.freeride.fastcv;

/**
 * Created by Oleg Kleiman on 22-Aug-15.
 */
public class FastCVWrapper {

    private long mNativeObj = 0;

    public FastCVWrapper(String faceCascadeFilePath,
                         String eyesCascadeFilePath){
        mNativeObj = nativeCreateObject(faceCascadeFilePath, eyesCascadeFilePath);
    }

    private native long nativeCreateObject(String faceCascadeFileName, String eyesCascadeFileName);

    public native int DetectFaces(long matAddrRgba, String face_cascade_name);

    public boolean findTemplate(long matAddrRgba) {
        return FindTemplate(mNativeObj, matAddrRgba);
    }

    private native boolean FindTemplate(long thiz, long matAddrRgba);
    public native int MatchTemplate(long matAddrRgba);

}
