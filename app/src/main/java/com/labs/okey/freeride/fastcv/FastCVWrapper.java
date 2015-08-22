package com.labs.okey.freeride.fastcv;

/**
 * Created by Oleg on 22-Aug-15.
 */
public class FastCVWrapper {

    public String pathToCascade;
    private long mNativeObj = 0;

    public FastCVWrapper(String cascadeFilePath){

        pathToCascade = cascadeFilePath; // TODO: remove this member varibale
        //mNativeObj = nativeCreateObject(cascadeFilePath);
    }

    public native int DetectFaces(long matAddrRgba, String face_cascade_name);

}
