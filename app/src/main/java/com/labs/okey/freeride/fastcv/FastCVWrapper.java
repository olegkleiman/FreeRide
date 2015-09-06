package com.labs.okey.freeride.fastcv;

/**
 * Created by Oleg on 22-Aug-15.
 */
public class FastCVWrapper {

    public String pathToFaceCascade;
    public String pathToEyesCascade;
    private long mNativeObj = 0;

    public FastCVWrapper(String faceCascadeFilePath,
                         String eyesCascadeFilePath){

        pathToFaceCascade = faceCascadeFilePath; // TODO: remove this member varibale
        pathToEyesCascade = eyesCascadeFilePath;
        //mNativeObj = nativeCreateObject(cascadeFilePath);
    }

    public native int DetectFaces(long matAddrRgba, String face_cascade_name);
    public native boolean FindTemplate(long matAddrRgba, String face_cascade_name, String eye_cascade_name);
    public native int MatchTemplate(long matAddrRgba);

}
