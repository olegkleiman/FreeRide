package com.labs.okey.freeride;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.OrientationEventListener;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.labs.okey.freeride.fastcv.FastCVCameraView;
import com.labs.okey.freeride.fastcv.FastCVWrapper;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.IUploader;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class CameraCVActivity extends Activity
        implements CameraBridgeViewBase.CvCameraViewListener2,
                    Camera.PictureCallback,
                    IUploader,
                    Handler.Callback {

    private static final String LOG_TAG = "FR.CV";

    private Handler handle = new Handler(this);
    public Handler getHandler() { return handle; }

    private Mat     mGray;
    private Mat     mRgba;
    private Mat     mMatTemplate;

    Scalar mCameraFontColor = new Scalar(255, 255, 255);
    String mCameraDirective;
    String mCameraDirective2;
    TextToSpeech mSpeechCommander;

    FastCVWrapper mCVWrapper;

    private FastCVCameraView mOpenCvCameraView;

    OrientationEventListener mOrientationEventListener;
    private int              mCurrentOrientation;

    private String createCascadeFile(int resourceId, String fileName) {

        try {
            InputStream is = getResources().openRawResource(resourceId);
            File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
            File cascadeFile = new File(cascadeDir, fileName);

            FileOutputStream os = new FileOutputStream(cascadeFile);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while( (bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            os.close();
            is.close();

            return cascadeFile.getAbsolutePath();

        } catch (IOException e) {
            if(Crashlytics.getInstance() != null )
                Crashlytics.logException(e);

            Log.e(LOG_TAG, e.getMessage());

            return "";
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(LOG_TAG, "OpenCV loaded successfully");

                    System.loadLibrary("fastcvUtils");

                    String faceCascadeFilePath = createCascadeFile(R.raw.haarcascade_frontalface_default,
                                                                "haarcascade_frontalface_default.xml");
                    String eyesCascadeFilePath = createCascadeFile(R.raw.haarcascade_eye,
                                                                "haarcascade_eye.xml");

                    mCVWrapper = new FastCVWrapper(faceCascadeFilePath,
                                                   eyesCascadeFilePath);

                    if( mOpenCvCameraView != null) {
                        mOpenCvCameraView.enableView();
                        mSpeechCommander = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener(){
                            @Override
                            public void onInit(int status) {
                                if(status == TextToSpeech.ERROR) {
                                    Log.e(LOG_TAG, "Can not init Speech Engine");
                                    //t1.setLanguage(Locale.UK);
                                }
                            }
                        });
                    }

                }
                break;

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

        try {
            checkCameraPermissions();

            initCamera();

        } catch(SecurityException sex) {

            // Returns true if app has requested this permission previously
            // and the user denied the request
            if( ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.CAMERA)) {

                Log.d(LOG_TAG, getString(R.string.permission_camera_denied));

            } else {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.CAMERA},
                        Globals.CAMERA_PERMISSION_REQUEST);
            }
        }

    }

    @TargetApi(Build.VERSION_CODES.M)
    private void checkCameraPermissions() throws SecurityException {
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED )
                throw new SecurityException();
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults){
        try {

            switch( requestCode ) {

                case Globals.CAMERA_PERMISSION_REQUEST: {

                    if(  grantResults.length > 0
                            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                        initCamera();

                    } else {
                        //finish();
                    }
                }
            }

        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());

        }
    }

    private void initCamera() {
        mOpenCvCameraView = (FastCVCameraView) findViewById(R.id.java_surface_view);
        if( mOpenCvCameraView != null ) {
            mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
            mOpenCvCameraView.setCvCameraViewListener(this);

            PackageManager pm = getPackageManager();
            if( pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) )
                mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_FRONT);
            else
                mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);

            mCurrentOrientation = OrientationEventListener.ORIENTATION_UNKNOWN;
            mOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
                @Override
                public void onOrientationChanged(int degrees) {
                    if( (degrees >= 45 && degrees <= 135)
                            || (degrees >= 225 && degrees <= 315) )
                        mCurrentOrientation = Configuration.ORIENTATION_LANDSCAPE;
                    else
                        mCurrentOrientation = Configuration.ORIENTATION_PORTRAIT;

                }
            };
            if( mOrientationEventListener.canDetectOrientation() ) {
                mOrientationEventListener.enable();
            }
        }

        mCameraDirective = getString(R.string.camera_directive_1);
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
    @CallSuper
    public void onStop(){

        if( isCheckerMatchTimerRunning() )
            mCheckMatchResultTimer.shutdown();

        super.onStop();
    }

    @Override
    @CallSuper
    public void onPause() {

        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        if( mSpeechCommander != null ) {
            mSpeechCommander.stop();
            mSpeechCommander.shutdown();
        }

        super.onPause();
    }


    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        if( !OpenCVLoader.initDebug() ) {
            // Roughly, it's an analog of System.loadLibrary('opencv_java3') - meaning .so library
            // In our case it is supposed to always return false, because we are statically linked with opencv_java3.so
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
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();

        if( mOrientationEventListener != null )
            mOrientationEventListener.disable();
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
        mMatTemplate = new Mat(height, width, CvType.CV_8UC1);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
        mGray.release();
        mMatTemplate.release();
    }

    private boolean             mSearchInitialized = false;

    ScheduledExecutorService    mCheckMatchResultTimer = Executors.newScheduledThreadPool(1);
    private Boolean             checkMatchRunning = false;
    boolean isCheckerMatchTimerRunning() {
        return checkMatchRunning;
    }

    private int                 mInitialEyesDetectedCounter = 0;
    private int                 INITIAL_EYES = 3;
    private int                 mMissedEyesCounter = 0;
    private int                 MISSED_EYES = 3;
    private Mat                 mFinalFaceMat;
    private int                 FRAMES_WITH_NOFACES = 5;
    private int                 nFramesWithNoFaces = 0;

    private boolean bUploadingFrame = false;

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        long executionTime = 0L;
        long start = System.currentTimeMillis();

        // input frame has RGBA format
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();

        Core.flip(mRgba, mRgba, 1); // flip around y-axis anyway: found face or not

        try {
            Mat faceMat = new Mat();
            if( mCVWrapper.DetectFace(mRgba.getNativeObjAddr(),
                    mGray.getNativeObjAddr(),
                    faceMat.getNativeObjAddr(),
                    mCurrentOrientation) ) {

                final Mat eyeMat = new Mat();
                boolean bEyeFound = mCVWrapper.DetectEye(mRgba.getNativeObjAddr(),
                                    faceMat.getNativeObjAddr(),
                                    eyeMat.getNativeObjAddr(),
                                    mCurrentOrientation);

                if( !mSearchInitialized) {
                    if( bEyeFound ) {

                        if( ++mInitialEyesDetectedCounter >= INITIAL_EYES) {
                            mSearchInitialized = true;

                            mFinalFaceMat = new Mat();
                            faceMat.copyTo(mFinalFaceMat);

                            //matToView(mFinalFaceMat, R.id.imageViewFace);

                            getHandler().post(new Runnable() {

                                @Override
                                public void run() {

                                    if( mCurrentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                                        Core.transpose(eyeMat, eyeMat);
                                    }

                                    matToView(eyeMat, R.id.imageViewTemplate);

                                }
                            });
                        }

                    }
                } else {
                    if( !bEyeFound ) {

                        if( ++mMissedEyesCounter >= MISSED_EYES ) {
                            mOpenCvCameraView.stopPreview();

                            if( !bUploadingFrame ) {

                                bUploadingFrame = true;

                                assert mFinalFaceMat != null;

                                final Bitmap faceBitmap = Bitmap.createBitmap(mFinalFaceMat.cols(),
                                                                              mFinalFaceMat.rows(),
                                                                              Bitmap.Config.ARGB_8888);
                                Utils.matToBitmap(mFinalFaceMat, faceBitmap);

                                getHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        uploadFrame(faceBitmap);
                                    }
                                });
                            }

                        }

                    }
                }
          } else { // no face was detected
                if(mSearchInitialized) {
                    if( ++nFramesWithNoFaces >= FRAMES_WITH_NOFACES ) {
                        mSearchInitialized = false;
                        mInitialEyesDetectedCounter = 0;
                        nFramesWithNoFaces = 0;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ImageView imgView = (ImageView) findViewById(R.id.imageViewTemplate);
                                imgView.setImageResource(R.drawable.ic_smart_selfie);
                            }
                        });
                    }

                }
          }
        } catch( Exception ex) {
            if(Crashlytics.getInstance() != null )
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());
        }


        executionTime += (System.currentTimeMillis() - start);
        String msg = String.format("Executed for %d ms.", executionTime);
        Log.d(LOG_TAG, msg);

        System.gc();
        return mRgba;
    }

    @UiThread
    private void matToView(final Mat mat, final int id){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = Bitmap.createBitmap(mat.cols(),
                        mat.rows(),
                        Bitmap.Config.ARGB_8888);
                Utils.matToBitmap(mat, bmp);

                ImageView imgView = (ImageView)findViewById(id);
                imgView.setImageBitmap(bmp);
            }
        });

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
//        try {
//            BitmapFactory.Options options = new BitmapFactory.Options();
//            options.inTempStorage = new byte[16 * 1024];
//
//            Camera.Parameters parameters = camera.getParameters();
//            Camera.Size size = parameters.getPictureSize();
//
//            int height = size.height;
//            int width = size.width;
//            float mb = (width * height) / 1024000;
//
//            if (mb > 4f)
//                options.inSampleSize = 4;
//            else if (mb > 3f)
//                options.inSampleSize = 2;
//
//            final Bitmap _bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
//
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//
//                    new MaterialDialog.Builder(CameraCVActivity.this)
//                            .title(getString(R.string.detection_success))
//                            .positiveText(R.string.ok)
//                            .callback(new MaterialDialog.ButtonCallback() {
//                                @Override
//                                public void onPositive(MaterialDialog dialog) {
//
//                                    reportAnswer(1);
//
//                                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                                    _bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
//                                    byte[] b = baos.toByteArray();
//
//                                    Intent intent = new Intent();
//                                    intent.putExtra("face", b);
//                                    intent.putExtra("faceid", "4444444");
//
//                                    setResult(RESULT_OK, intent);
//                                    finish();
//                                }
//                            })
//                            .show();
//
//                }
//            });
//
//            uploadFrame(_bitmap);
//
//        } catch(Exception ex) {
//
//            if(Crashlytics.getInstance() != null)
//                Crashlytics.logException(ex);
//
//            Log.e(LOG_TAG, ex.getMessage());
//
//        }
    }

    private void uploadFrame(final Bitmap sampleBitmap) {

        if( sampleBitmap == null )
            return;

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        new AsyncTask<InputStream, String, Face[]>(){

            // Progress popped up when communicating with server.
            ProgressDialog  mProgressDialog;
            Exception       mEx;
            InputStream     mInputStream;
            URI             blobPublishedUri;

            private String getTempFileName() {
                String timeStamp = new SimpleDateFormat("yyyyMMdd+HHmmss").format(new Date());
                return "FR_" + timeStamp;
            }

            @Override
            protected void onPreExecute() {

                mProgressDialog = ProgressDialog.show(CameraCVActivity.this, "", "");
            }

            @Override
            protected void onPostExecute(Face[] result) {

                bUploadingFrame = false;

                try {
                    mProgressDialog.dismiss();
                } catch(Exception ex) {

                    if(Crashlytics.getInstance() != null)
                        Crashlytics.logException(ex);

                    Log.e(LOG_TAG, ex.getMessage());
                }

                if( mEx != null) {

                    Intent intent = new Intent();
                    intent.putExtra(getString(R.string.detection_exception),
                                    mEx.getMessage());

                    setResult(RESULT_FIRST_USER, intent);
                    finish();
                }

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

                                        // Reset search results and restart camera preview

                                        mSearchInitialized = false;
                                        checkMatchRunning = false;
                                        mInitialEyesDetectedCounter = 0;
                                        mMissedEyesCounter = 0;
                                        nFramesWithNoFaces = 0;

                                        mOpenCvCameraView.startPreview();
                                    }
                                })
                                .show();
                    } else {

                        Face _face = result[0];
                        final UUID faceID = _face.faceId;

                        reportAnswer(1);

//                        int bmpSize = getBitmapSize(sampleBitmap);
//                        int bmpWidth = sampleBitmap.getWidth();
//                        int bmpHeight = sampleBitmap.getHeight();

                        Bitmap thumbFace = Bitmap.createScaledBitmap(sampleBitmap, 40, 40, false);
//                        bmpSize = getBitmapSize(thumbFace);
//                        bmpWidth = thumbFace.getWidth();
//                        bmpHeight = thumbFace.getHeight();

                        Intent intent = new Intent();
                        intent.putExtra(getString(R.string.detection_face_bitmap), thumbFace);
                        intent.putExtra(getString(R.string.detection_face_id), faceID);
                        intent.putExtra(getString(R.string.detection_face_uri), blobPublishedUri);

                        setResult(RESULT_OK, intent);
                        finish();
                    }


                } catch(Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }
            }

            @Override
            protected Face[] doInBackground(InputStream... params) {

                mInputStream = params[0];

                // Get an instance of face service client to detect faces in image.
                FaceServiceClient faceServiceClient = new FaceServiceRestClient(getString(R.string.oxford_subscription_key));

                // Start detection.
                try {

                    Face[] faces = faceServiceClient.detect(
                            mInputStream,  /* Input stream of image to detect */
                            true,       /* Whether to return face ID */
                            false,       /* Whether to return face landmarks */
                             /* Which face attributes to analyze, currently we support:
                            age,gender,headPose,smile,facialHair */
                            new FaceServiceClient.FaceAttributeType[] {
                                    FaceServiceClient.FaceAttributeType.Age,
                                    FaceServiceClient.FaceAttributeType.Gender,
                                    FaceServiceClient.FaceAttributeType.FacialHair,
                                    FaceServiceClient.FaceAttributeType.Smile,
                                    FaceServiceClient.FaceAttributeType.HeadPose
                            });

                    // Upload face image to blog (may be redundant, used here primarily for tests)
                    if( faces.length > 0 ) {
                        Face face = faces[0];

                        File outputDir = getApplicationContext().getCacheDir();
                        String photoFileName = face.faceId.toString();

                        File photoFile = File.createTempFile(photoFileName, ".jpg", outputDir);
                        FileOutputStream fos = new FileOutputStream(photoFile);
                        sampleBitmap.compress(Bitmap.CompressFormat.JPEG, 50, fos);

                        fos.flush();
                        fos.close();

                        MediaStore.Images.Media.insertImage(getContentResolver(),
                                photoFile.getAbsolutePath(),
                                photoFile.getName(),
                                photoFile.getName());

                        CloudStorageAccount storageAccount = CloudStorageAccount.parse(Globals.storageConnectionString);
                        CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
                        CloudBlobContainer container = blobClient.getContainerReference("faces");
                        String fileName = photoFile.getName();
                        CloudBlockBlob blob = container.getBlockBlobReference(fileName);

                        blob.upload(new FileInputStream(photoFile), photoFile.length());
                        blobPublishedUri = blob.getQualifiedUri();

                        photoFile.delete();
                    }

                    return faces;
                } catch (Exception e) {

                    if(Crashlytics.getInstance() != null )
                        Crashlytics.logException(e);

                    Log.e(LOG_TAG, e.getMessage());
                    mEx = e;

                }

                return null;
            }

        }.execute(inputStream);
    }

    private int getBitmapSize(Bitmap bmp) {
        if(Build.VERSION.SDK_INT< Build.VERSION_CODES.KITKAT){
            return bmp.getByteCount();
        } else
            return bmp.getAllocationByteCount();
    }

    private void reportAnswer(int status){

        CustomEvent confirmEvent = new CustomEvent(getString(R.string.passenger_confirmation_answer_name));
        // No user for this Answer
        //confirmEvent.putCustomAttribute("User", getUser().getFullName());
        confirmEvent.putCustomAttribute(getString(R.string.answer_success_attribute), status);
        Answers.getInstance().logCustom(confirmEvent);
    }

    //
    // Implementation of Handler.Callback
    //
    @Override
    public boolean handleMessage(Message msg) {
        return true;
    }

    //
    // Implementation of IPictureURLUpdater
    //
    @Override
    public void update(String url) {
        //new wamsPictureURLUpdater(this).execute(url, mRideCode, mFaceID.toString());
    }

    @Override
    public void finished(int task_tag, boolean success) {
        if( !success )
            restoreFromSendToDetect(null);
        else
            finish();
    }

}
