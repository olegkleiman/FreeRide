package com.labs.okey.freeride;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.labs.okey.freeride.fastcv.FastCVCameraView;
import com.labs.okey.freeride.fastcv.FastCVWrapper;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IPictureURLUpdater;
import com.labs.okey.freeride.utils.wamsBlobUpload;
import com.labs.okey.freeride.utils.wamsPictureURLUpdater;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.contract.Face;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class CameraCVActivity extends Activity
        implements CameraBridgeViewBase.CvCameraViewListener2,
                    Camera.PictureCallback,
                    IPictureURLUpdater{

    private static final String LOG_TAG = "FR.CVCameraActivity";

    private String mRideCode = "73373";
    private UUID mFaceID;

    private Mat                    mRgba;
    private Mat                    mIntermediateMat;
    private Mat                    mGray;

    Scalar mCameraFontColor = new Scalar(255, 255, 255);
    String mCameraDirective;
    String mCameraDirective2;

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
                        //InputStream is = getResources().openRawResource(R.raw.haarcascade_smile);
                        InputStream is = getResources().openRawResource(R.raw.lbpcascade_frontalface);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        File cascadeFile = new File(cascadeDir, "lbpcascade_frontalface.xml");

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

            PackageManager pm = getPackageManager();
            if( pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) )
                mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
            else
                mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        }

        mCameraDirective2 = getString(R.string.camera_directive_2);
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

//            CascadeClassifier facesCascade = new CascadeClassifier(mCVWrapper.pathToCascade);
//            //facesCascade.load(mCVWrapper.pathToCascade);
//
//            MatOfRect faces = new MatOfRect();
//
//            facesCascade.detectMultiScale(mGray, faces,
//                    1.2,
//                    3,
//                    2, // CV_HAAR_SCALE_IMAGE,
//                    new Size(20, 20),
//                    new Size(40, 40));
//
//            nFaces = faces.toArray().length;

            nFaces = mCVWrapper.DetectFaces(mGray.getNativeObjAddr(),
                                            mCVWrapper.pathToCascade);


        }
        catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

        mExecutionTime += (System.currentTimeMillis() - start);
        String msg = String.format("Executed for %d ms.", mExecutionTime / ++mFramesReceived);
        Log.d(LOG_TAG, msg);

        if( nFaces > 0 ) {

            String _s = String.format(mCameraDirective2, nFaces);

            Imgproc.putText(mGray, _s, new Point(100, 500),
                    3, 1, mCameraFontColor, 2);

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    makeFrame(null);
                }
            });

        }

        return mGray;
    }

    public void makeFrame(View view){

//        mOpenCvCameraView.stopPreview();
//
//        TextView txtStatus = (TextView)findViewById(R.id.detection_monitor);
//        txtStatus.setText(getString(R.string.detection_center_desc));
//
//        findViewById(R.id.detection_buttons_bar).setVisibility(View.VISIBLE);

    }

    public void sendToDetect(View view){

//        // Dismiss buttons
//        findViewById(R.id.detection_buttons_bar).setVisibility(View.GONE);
//
//        // Restore status text
//        TextView txtStatus = (TextView)findViewById(R.id.detection_monitor);
//        txtStatus.setText(getString(R.string.detection_freeze));
//
//        // Will be continued in onPictureTaken() callback
//        mOpenCvCameraView.takePicture(CameraCVActivity.this);
    }

    public void restoreFromSendToDetect(View view){

//        // Restore camera frames processing
//        mOpenCvCameraView.startPreview();
//
//        // Dismiss buttons
//        findViewById(R.id.detection_buttons_bar).setVisibility(View.GONE);
//
//        // Restore status text
//        TextView txtStatus = (TextView)findViewById(R.id.detection_monitor);
//        txtStatus.setText(getString(R.string.detection_freeze));
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inTempStorage = new byte[16 * 1024];

            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPictureSize();

            int height = size.height;
            int width = size.width;
            float mb = (width * height) / 1024000;

            if (mb > 4f)
                options.inSampleSize = 4;
            else if (mb > 3f)
                options.inSampleSize = 2;

            Bitmap _bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);

            processFrame(_bitmap);
        } catch(Exception ex) {
            String message = ex.getMessage();
            Log.e(LOG_TAG, message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    private void processFrame(final Bitmap sampleBitmap) {

        if( sampleBitmap == null )
            return;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        new AsyncTask<InputStream, String, Face[]>(){

            // Progress dialog popped up when communicating with server.
            ProgressDialog mProgressDialog;

            InputStream mInputStream;

            private String getTempFileName() {
                String timeStamp = new SimpleDateFormat("yyyyMMdd+HHmmss").format(new Date());
                return "FR_" + timeStamp;
            }

            @Override
            protected void onPreExecute() {
                mProgressDialog = ProgressDialog.show(CameraCVActivity.this,
                        getString(R.string.detection_send),
                        getString(R.string.detection_wait));
            }

            @Override
            protected void onPostExecute(Face[] result) {

                String strFormat = getString(R.string.detection_save);
                String msg = String.format(strFormat, result.length);
                Log.i(LOG_TAG, msg);

                mProgressDialog.dismiss();

                try {

                    if( mInputStream != null)
                        mInputStream.close();

                    if( result.length < 1) {
                        new MaterialDialog.Builder(CameraCVActivity.this)
                                .title(getString(R.string.detection_no_results))
                                .content(getString(R.string.try_again))
                                .positiveText(R.string.ok).callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        mOpenCvCameraView.startPreview();
                                    }
                                })
                                .show();
                    } else {

                        Face _face = result[0];
                        mFaceID = _face.faceId;

                        new MaterialDialog.Builder(CameraCVActivity.this)
                                .title(getString(R.string.detection_results))
                                .content(msg)
                                .positiveText(R.string.yes)
                                .negativeText(R.string.no)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {

                                        try {
                                            File outputDir = getApplicationContext().getCacheDir();
                                            String photoFileName = getTempFileName();

                                            File photoFile = File.createTempFile(photoFileName, ".jpg", outputDir);
                                            FileOutputStream fos = new FileOutputStream(photoFile);
                                            sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos);

                                            fos.flush();
                                            fos.close();

                                            MediaStore.Images.Media.insertImage(getContentResolver(),
                                                    photoFile.getAbsolutePath(),
                                                    photoFile.getName(),
                                                    photoFile.getName());

                                            new wamsBlobUpload(CameraCVActivity.this).execute(photoFile);

                                        } catch (IOException ex) {
                                            Log.e(LOG_TAG, ex.getMessage());
                                        }

                                    }

                                    @Override
                                    public void onNegative(MaterialDialog dialog) {
                                        mOpenCvCameraView.startPreview();
                                    }
                                })
                                .show();
                    }


                } catch(Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }
            }

            @Override
            protected void onProgressUpdate(String... progress) {
                mProgressDialog.setMessage(progress[0]);
            }

            @Override
            protected Face[] doInBackground(InputStream... params) {

                mInputStream = params[0];

                // Get an instance of face service client to detect faces in image.
                FaceServiceClient faceServiceClient = new FaceServiceClient(getString(R.string.oxford_subscription_key));

                publishProgress("Please wait..");

                // Start detection.
                try {
                    return faceServiceClient.detect(
                            mInputStream,  /* Input stream of image to detect */
                            true,       /* Whether to analyzes facial landmarks */
                            false,       /* Whether to analyzes age */
                            false,       /* Whether to analyzes gender */
                            true);      /* Whether to analyzes head pose */
                } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage());

                    publishProgress(e.getMessage());
                }

                return null;
            }

        }.execute(inputStream);
    }

    @Override
    public void update(String url) {
        new wamsPictureURLUpdater(this).execute(url, mRideCode, mFaceID.toString());
    }

    @Override
    public void finished(boolean success) {
        if( !success )
            restoreFromSendToDetect(null);
        else
            finish();
    }
}
