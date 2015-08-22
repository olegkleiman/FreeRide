package com.labs.okey.freeride;

import android.app.Activity;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.labs.okey.freeride.fastcv.FastCVCameraView;
import com.labs.okey.freeride.fastcv.FastCVWrapper;
import com.labs.okey.freeride.utils.Globals;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class CameraCVActivity extends Activity implements CameraBridgeViewBase.CvCameraViewListener2{

    private static final String LOG_TAG = "FR.CVCameraActivity";

    private Mat                    mRgba;
    private Mat                    mIntermediateMat;
    private Mat                    mGray;

    FastCVWrapper mCVWrapper;

    private FastCVCameraView mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(LOG_TAG, "OpenCV loaded successfully");

                    System.loadLibrary("fastcvUtils");

                    try {
                        InputStream is = getResources().openRawResource(R.raw.haarcascade_smile);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        File cascadeFile = new File(cascadeDir, "haarcascade_smile.xml");

                        FileOutputStream os = new FileOutputStream(cascadeFile);
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while( (bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }

                        os.close();
                        is.close();

                        //cascadeDir.delete();

                        mCVWrapper = new FastCVWrapper(cascadeFile.getAbsolutePath());

                    } catch(IOException ex) {
                        Log.e(LOG_TAG, ex.getMessage());
                    }

                    if( mOpenCvCameraView != null) {
                        mOpenCvCameraView.enableView();
                    }

                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_camera_cv);

        mOpenCvCameraView = (FastCVCameraView) findViewById(R.id.java_surface_view);
        if( mOpenCvCameraView != null ) {
            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
            mOpenCvCameraView.setCvCameraViewListener(this);

            mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_camera_cv, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();

        if( !OpenCVLoader.initDebug() ) {
            // Roughly, it's an analog of System.loadLibrary('opencv_java3') - meaning .so library
            // In our case it is supposed to always return false, because we aare statically linked with opencv_java3.so
            // (in jniLbs/<platform> folder.
            //
            // Such way of linking allowed for running without OpenCV Manager (https://play.google.com/store/apps/details?id=org.opencv.engine&hl=en)
            Log.d(LOG_TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");

            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0,
                    this,
                    mLoaderCallback);
        } else {
            Log.d(LOG_TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

        @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mIntermediateMat = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mIntermediateMat.release();
    }

    long mExecutionTime = 0;
    long mFramesReceived = 0;

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        // input frame has RGBA format
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        long start = System.currentTimeMillis();

        int nFaces = 0;
        try {
            nFaces = mCVWrapper.DetectFaces(mGray.getNativeObjAddr(),
                                            mCVWrapper.pathToCascade);
        }
        catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

        mExecutionTime += (System.currentTimeMillis() - start);
        String msg = String.format("Executed for %d ms.", mExecutionTime / ++mFramesReceived);
        Log.d(LOG_TAG, msg);

        return mGray;
    }
}
