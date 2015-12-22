package com.labs.okey.freeride;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.media.AudioManager;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextSwitcher;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.crashlytics.android.Crashlytics;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;
import com.labs.okey.freeride.adapters.WiFiPeersAdapter2;
import com.labs.okey.freeride.model.Join;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.BLEUtil;
import com.labs.okey.freeride.utils.ClientSocketHandler;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.GroupOwnerSocketHandler;
import com.labs.okey.freeride.utils.IRecyclerClickListener;
import com.labs.okey.freeride.utils.IRefreshable;
import com.labs.okey.freeride.utils.ITrace;
import com.labs.okey.freeride.utils.RoundedDrawable;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.WiFiUtil;
import com.microsoft.windowsazure.mobileservices.MobileServiceException;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import net.steamcrafted.loadtoast.LoadToast;

import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class PassengerRoleActivity extends BaseActivityWithGeofences
    implements ITrace,
        Handler.Callback,
        IRecyclerClickListener,
        IRefreshable,
        WiFiUtil.IPeersChangedListener,
        BLEUtil.IDeviceDiscoveredListener,
        WifiP2pManager.ConnectionInfoListener,
        WAMSVersionTable.IVersionMismatchListener,
        android.location.LocationListener
{

    private static final String LOG_TAG = "FR.Passenger";

    String mUserID;

    final int MAKE_PICTURE_REQUEST = 1;
    // handled  in onActivityResult

    Boolean                             mDriversShown;
    TextSwitcher                        mTextSwitcher;

    MobileServiceTable<Join>            joinsTable;

    WiFiUtil                            mWiFiUtil;
    WiFiPeersAdapter2                   mDriversAdapter;
    public ArrayList<WifiP2pDeviceUser> mDrivers = new ArrayList<>();

    private Handler handler = new Handler(this);
    public Handler getHandler() {
        return handler;
    }

    private String                      mRideCode;
    private URI                         mPictureURI;
    private UUID                        mFaceId;

    private Location                    mCurrentLocation;
    private long                        mLastLocationUpdateTime = System.currentTimeMillis();

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger);

        setupUI(getString(R.string.title_activity_passenger_role), "");

        try {
            startLocationUpdates(this);
        } catch( SecurityException sex) {

            new MaterialDialog.Builder(this)
                    .title(R.string.permission_lacked_title)
                    .content(R.string.location_permission_lacked)
                    .iconRes(R.drawable.ic_exclamation)
                    .positiveText(R.string.ok)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {

                        }
                    })
                    .show();
        }

        wamsInit(false); // without auto-update for this activity

        initGeofences();

        joinsTable = getMobileServiceClient().getTable("joins", Join.class);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mUserID = sharedPrefs.getString(Globals.USERIDPREF, "");

//        new Thread() {
//            @Override
//            public void run(){
//
//                try{
//
//                    while (true) {
//
//                        runOnUiThread(new Runnable() {
//
//                            @Override
//                            public void run() {
//
//                                String message = Globals.isInGeofenceArea() ?
//                                        Globals.getMonitorStatus() :
//                                        getString(R.string.geofence_outside_title);
//
////                                String currentGeoStatus = Globals.getMonitorStatus();
////                                if( !currentGeoStatus.equals(message) )
//                                    mTextSwitcher.setCurrentText(message);
//
//                            }
//                        });
//
//                        Thread.sleep(2000);
//                    }
//                }
//                catch(InterruptedException ex) {
//                    Log.e(LOG_TAG, ex.getMessage());
//                }
//
//            }
//        }.start();

        mWiFiUtil = new WiFiUtil(this);

        if( savedInstanceState != null ) {

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_RIDE_CODE) ) {
                mRideCode = savedInstanceState.getString(Globals.PARCELABLE_KEY_RIDE_CODE);
            }

            if( savedInstanceState.containsKey(Globals.PARCELABLE_KEY_DRIVERS) ) {
                ArrayList<WifiP2pDeviceUser> drivers = savedInstanceState.getParcelableArrayList(Globals.PARCELABLE_KEY_DRIVERS);
                if( drivers != null ) {

                    mDrivers.addAll(drivers);
                    mDriversAdapter.notifyDataSetChanged();
                }
            }
        } else {
            refresh();
        }

//        if( mRideCode == null
//            || mRideCode.isEmpty() ) {
//            // Start WiFi-Direct serviceDiscovery
//            // for (hopefully) already published service
//            mWiFiUtil = new WiFiUtil(this);
//
//            refresh();
//        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(Globals.PARCELABLE_KEY_RIDE_CODE, mRideCode);
        outState.putParcelableArrayList(Globals.PARCELABLE_KEY_DRIVERS, mDrivers);
    }

    @UiThread
    protected void setupUI(String title, String subTitle){
        super.setupUI(title, subTitle);

        RecyclerView driversRecycler = (RecyclerView)findViewById(R.id.recyclerViewDrivers);
        driversRecycler.setHasFixedSize(true);
        driversRecycler.setLayoutManager(new LinearLayoutManager(this));
        driversRecycler.setItemAnimator(new DefaultItemAnimator());

        mDriversAdapter = new WiFiPeersAdapter2(this,
                                    R.layout.drivers_header,
                                    R.layout.row_devices,
                                    mDrivers);
        driversRecycler.setAdapter(mDriversAdapter);

        mDriversShown = false;

        mTextSwitcher = (TextSwitcher) findViewById(R.id.passenger_monitor_text_switcher);
        Animation in = AnimationUtils.loadAnimation(this, R.anim.push_up_in);
        Animation out = AnimationUtils.loadAnimation(this, R.anim.push_up_out);
        mTextSwitcher.setInAnimation(in);
        mTextSwitcher.setOutAnimation(out);
        // Set the initial text without an animation
        String currentMonitorStatus = getString(R.string.geofence_outside_title);
        mTextSwitcher.setCurrentText(currentMonitorStatus);
        mTextSwitcher.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Intent intent = new Intent(PassengerRoleActivity.this,
                        GFActivity.class);
                startActivity(intent);

                return false;
            }
        });


        Globals.setMonitorStatus(currentMonitorStatus);
    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        startLocationUpdates(this);

        if( mWiFiUtil != null)
            mWiFiUtil.registerReceiver(this);
    }

    @Override
    @CallSuper
    public void onPause() {
        if( mWiFiUtil != null ) {
            mWiFiUtil.unregisterReceiver();
            mWiFiUtil.stopDiscovery();
        }

        Globals.clearMyPassengerIds();
        Globals.clearMyPassengers();

        stopLocationUpdates(this);

        super.onPause();
    }

    @Override
    @CallSuper
    protected void onStop() {
        if( mWiFiUtil != null )
            mWiFiUtil.removeGroup();

        // Ride Code will be re-newed on next activity's launch
        mRideCode = "";

        super.onStop();
    }

    //
    // Implementation of LocationListener
    //
    @Override
    public void onLocationChanged(Location location) {

        if( !isAccurate(location) )
            return;

        mCurrentLocation = location;
        // Global flag 'inGeofenceArea' is updated inside getGFenceForLocation()
        String msg = getGFenceForLocation(location);

        TextView textView = (TextView) mTextSwitcher.getCurrentView();
        String msgRepeat = "(R) "  + textView.getText().toString();

        if( Globals.isInGeofenceArea() ) {
            mLastLocationUpdateTime = System.currentTimeMillis();
        } else {
            long elapsed = System.currentTimeMillis() - mLastLocationUpdateTime;
            if( mLastLocationUpdateTime != 0 // for the first-time
                    && elapsed < Globals.GF_OUT_TOLERANCE ) {
                Globals.setInGeofenceArea(true);
                msg = msgRepeat;
            }

        }

        mTextSwitcher.setCurrentText(msg);
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if( requestCode == MAKE_PICTURE_REQUEST) {
            switch( resultCode ) {

                case RESULT_OK: {

                    if( data != null ) {
                        Bundle extras = data.getExtras();

                        FloatingActionButton passengerPicture = (FloatingActionButton)this.findViewById(R.id.join_ride_button);
                        passengerPicture.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.green)));

                        Bitmap bmp = extras.getParcelable(getString(R.string.detection_face_bitmap));
                        if( bmp != null) {
                            Drawable drawable = new BitmapDrawable(this.getResources(), bmp);

                            drawable = RoundedDrawable.fromDrawable(drawable);
                            ((RoundedDrawable) drawable)
                                    .setCornerRadius(Globals.PICTURE_CORNER_RADIUS)
                                    .setBorderColor(Color.WHITE)
                                    .setBorderWidth(0)
                                    .setOval(true);

                            passengerPicture.setImageDrawable(drawable);
                        }

                        mPictureURI = (URI)extras.getSerializable(getString(R.string.detection_face_uri));
                        mFaceId  = (UUID)extras.getSerializable(getString(R.string.detection_face_id));

                        mTextSwitcher.setText(getString(R.string.instruction_make_additional_selfies));
                    }


                }
                break;

                case RESULT_CANCELED: { // Distinguishing between successful connection
                                        // and just pressing back from there.
                    refresh();
                }
                break;

                case RESULT_FIRST_USER: { // Any exceptions were occurred inside CameraCV Activity

                    if( data != null ) {
                        Bundle extras = data.getExtras();
                        String message = extras.getString(getString(R.string.detection_exception));
                        if( message == null)
                            message = getString(R.string.detection_general_exception);

                        new MaterialDialog.Builder(this)
                                .content(message)
                                .title(R.string.detection_error)
                                .iconRes(R.drawable.ic_exclamation)
                                .positiveText(R.string.ok)
                                .show();
                    }
                }
                break;

            }
        }
    }

    @UiThread
    private void showRideCodePane(@StringRes int contentStringResId,
                                  @ColorInt int contentColor){

        try {
            String dialogContent = getString(contentStringResId);

            new MaterialDialog.Builder(this)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onNegative(MaterialDialog dialog) {
                            refresh();
                        }

                        @Override
                        public void onNeutral(MaterialDialog dialog) {

                        }
                    })
                    .title(R.string.ride_code_title)
                    .content(dialogContent)
                    .positiveText(R.string.ok)
                    .neutralText(R.string.code_try_later)
                    .negativeText(R.string.code_retry_action)
                    .contentColor(contentColor)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_NUMBER)
                    .inputMaxLength(Globals.RIDE_CODE_INPUT_LENGTH)
                    .input(R.string.ride_code_hint,
                            R.string.ride_code_refill,
                            new MaterialDialog.InputCallback() {
                                @Override
                                public void onInput(MaterialDialog dialog, CharSequence input) {
                                    mRideCode = input.toString();
                                    onSubmitCode();
                                }
                            }

                    ).show();
        } catch( Exception ex) {
            if( Crashlytics.getInstance() != null )
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    public void onCameraCV(View view) {

        // Only allow participation request from monitored areas
        if( !Globals.isInGeofenceArea() ) {
            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();

            return;
        }

        final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean bShowSelfieDescription = sharedPrefs.getBoolean(Globals.SHOW_SELFIE_DESC, true);

        if( bShowSelfieDescription ) {

            new MaterialDialog.Builder(this)
                    .title(getString(R.string.selfie))
                    .content(getString(R.string.selfie_content))
                    .iconRes(R.drawable.ic_smart_selfie)
                    .positiveText(R.string.ok)
                    .negativeText(R.string.selfie_desc_not_show_again)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {

                            Intent intent = new Intent(PassengerRoleActivity.this,
                                                        CameraCVActivity.class);
                            //intent.putExtra("RIDE_CODE", Globals.getRideCode());
                            startActivityForResult(intent, MAKE_PICTURE_REQUEST);
                        }

                        @Override
                        public void onNegative(MaterialDialog dialog) {

                            SharedPreferences.Editor editor = sharedPrefs.edit();
                            editor.putBoolean(Globals.SHOW_SELFIE_DESC, false);
                            editor.apply();

                            Intent intent = new Intent(PassengerRoleActivity.this,
                                                        CameraCVActivity.class);
                            startActivityForResult(intent, MAKE_PICTURE_REQUEST);
                        }
                    })
                    .show();
        } else {
            Intent intent = new Intent(this, CameraCVActivity.class);
            startActivityForResult(intent, MAKE_PICTURE_REQUEST);

        }


    }

    //
    // Implementation of IVersionMismatchListener
    //
    @Override
    public void mismatch(int majorLast, int minorLast, final String url) {
        try {
            new MaterialDialog.Builder(this)
                    .title(getString(R.string.new_version_title))
                    .content(getString(R.string.new_version_conent))
                    .positiveText(R.string.yes)
                    .negativeText(R.string.no)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(url));
                            //intent.setDataAndType(Uri.parse(url), "application/vnd.android.package-archive");
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                    })
                    .show();
        } catch (MaterialDialog.DialogException e) {
            // better that catch the exception here would be use handle to send events the activity
        }

    }

    @Override
    public void connectionFailure(Exception ex) {
        if( ex != null ) {

            View v = findViewById(R.id.drawer_layout);
            Snackbar.make(v, ex.getMessage(), Snackbar.LENGTH_LONG);
        }
    }

    @Override
    public void match() {

    }

    //
    // Implementation of IRecyclerClickListener
    //
    @Override
    public void clicked(View view, int position) {

        if( !Globals.isInGeofenceArea() ) {
            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();
        } else {

            WifiP2pDeviceUser driverDevice = mDrivers.get(position);

            mRideCode = driverDevice.getRideCode();

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {

                        //findViewById(R.id.join_ride_button).setVisibility(View.INVISIBLE);

                        FloatingActionButton passengerPicture = (FloatingActionButton)findViewById(R.id.join_ride_button);
                        passengerPicture.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.ColorAccent)));
                        Drawable drawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_action_camera, null);
                        passengerPicture.setImageDrawable(drawable);

                        onSubmitCode();
                    }
                }
            };

            StringBuilder sb = new StringBuilder(getString(R.string.passenger_confirm));
            sb.append(" ");
            sb.append(driverDevice.getUserName());
            sb.append("?");
            String message = sb.toString();

            new AlertDialogWrapper.Builder(this)
                    .setTitle(message)
                    .setNegativeButton(R.string.no, dialogClickListener)
                    .setPositiveButton(R.string.yes, dialogClickListener)
                    .show();
        }

    }

    private Runnable thanksRunnable = new Runnable() {
        @Override
        public void run() {
            new MaterialDialog.Builder(PassengerRoleActivity.this)
                    .title(R.string.thanks)
                    .content(R.string.confirmation_accepted)
                    .cancelable(false)
                    .positiveText(R.string.ok)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            //finish();
                        }
                    })
                    .show();
        }
    };

    public void onSubmitCode(){

        // Only allow participation request from monitored areas
        if( !Globals.isInGeofenceArea() ) {
            new MaterialDialog.Builder(this)
                    .title(R.string.geofence_outside_title)
                    .content(R.string.geofence_outside)
                    .positiveText(R.string.geofence_positive_answer)
                    .negativeText(R.string.geofence_negative_answer)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onPositive(MaterialDialog dialog) {
                            Globals.setRemindGeofenceEntrance();
                        }
                    })
                    .show();

            return;
        }

        final String android_id = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        final View v = findViewById(R.id.passenger_internal_layout);

        new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            LoadToast lt;

            @Override
            protected void onPreExecute() {

                lt = new LoadToast(PassengerRoleActivity.this);
                lt.setText(getString(R.string.processing));
                Display display = getWindow().getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);
                lt.setTranslationY(size.y / 2);
                lt.show();

            }

            @Override
            protected void onPostExecute(Void result){

                // Prepare to play sound loud :)
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                int sb2value = audioManager.getStreamMaxVolume(audioManager.STREAM_MUSIC);
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, sb2value/2, 0);

                CustomEvent confirmEvent = new CustomEvent(getString(R.string.passenger_confirmation_answer_name));
                confirmEvent.putCustomAttribute("User", getUser().getFullName());

                if( mEx != null ) {

                    confirmEvent.putCustomAttribute("Error", 0);

                    try{
                        MobileServiceException mse = (MobileServiceException)mEx.getCause();
                        int responseCode = 0;
                        if( mse.getCause() instanceof  UnknownHostException ) {
                            responseCode = 503; // Some artificially: usually 503 means
                                                // 'Service Unavailable'.
                                                // To this extent, we mean 'Connection lost'
                        } else {

                            responseCode = mse.getResponse().getStatus().getStatusCode();
                        }

                        switch( responseCode ) {
                            case 409: // HTTP 'Conflict'
                                      // picture required
                                // Ride code was successfully validated,
                                // but selfie is required

                                lt.success();

                                onCameraCV(null);

                                break;

                            case 404: // HTTP 'Not found' means 'no such ride code'
                                      // i.e.
                                // try again with appropriate message
                                showRideCodePane(R.string.ride_code_wrong,
                                                 Color.RED);
                                lt.error();
                                beepError.start();

                                break;

                            case 503: // HTTP 'Service Unavailable' interpreted as 'Connection Lost'
                                // Try again
                                showRideCodePane(R.string.connection_lost,
                                                 Color.RED);
                                lt.error();
                                beepError.start();

                                break;

                            default:
                                lt.error();
                                Toast.makeText(PassengerRoleActivity.this,
                                                mEx.getMessage(),
                                                Toast.LENGTH_LONG).show();
                                break;
                        }
                    } catch( Exception ex) {
                        if( Crashlytics.getInstance() != null )
                            Crashlytics.logException(ex);

                        if( !ex.getMessage().isEmpty() )
                            Log.e(LOG_TAG, ex.getMessage());
                    }

                }
                else {

                    confirmEvent.putCustomAttribute("Success", 1);

                    lt.success();
                    beepSuccess.start();

                    getHandler().postDelayed(thanksRunnable, 1500);

                }

                Answers.getInstance().logCustom(confirmEvent);

            }

            @Override
            protected Void doInBackground(Void... voids) {

                try{
                    Join _join  = new Join();
                    _join.setWhenJoined(new Date());
                    if( mPictureURI != null && !mPictureURI.toString().isEmpty() )
                        _join.setPictureURL(mPictureURI.toString());
                    if( mFaceId != null && !mFaceId.toString().isEmpty() )
                        _join.setFaceId(mFaceId.toString() );
                    _join.setRideCode(mRideCode);
                    String currentGeoFenceName = Globals.get_currentGeoFenceName();
                    _join.setGFenceName(currentGeoFenceName);
                    _join.setDeviceId(android_id);

                    try {
                        Location loc = getCurrentLocation();
                        if (loc != null) {
                            _join.setLat((float) loc.getLatitude());
                            _join.setLon((float) loc.getLongitude());
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, e.getMessage());
                    }
                    // The rest of params are set within WAMS insert script

                    joinsTable.insert(_join).get();

                } catch( ExecutionException | InterruptedException ex ) {

                    mEx = ex;
                    if(Crashlytics.getInstance() != null)
                        Crashlytics.logException(ex);

                    Log.e(LOG_TAG, ex.getMessage());
                }

                return null;
            }
        }.execute();
    }

    private void startAdvertise() {

        TextView txtCaption = (TextView) findViewById(R.id.drivers_caption);
        if( txtCaption != null )
            txtCaption.setText("Now adverising");

        mWiFiUtil.startRegistrationAndDiscovery(this,
                getUser().getRegistrationId(),
                getUser().getFullName(),
                // provide empty rideCode to distinguish
                // this broadcast from the driver's one
                "",
                getHandler(),
                1000);

        mTextSwitcher.setText(getString(R.string.passenger_adv_description));
    }


    //
    // Implementation of IRefreshable
    //
    @Override
    @UiThread
    public void refresh() {
        mDrivers.clear();
        mDriversAdapter.notifyDataSetChanged();

        final ImageButton btnRefresh = (ImageButton)findViewById(R.id.btnRefresh);
        if( btnRefresh != null ) // This may happens because
                                 // the button is actually created by adapter
            btnRefresh.setVisibility(View.GONE);
        final ProgressBar progress_refresh = (ProgressBar)findViewById(R.id.progress_refresh);
        if( progress_refresh != null )
            progress_refresh.setVisibility(View.VISIBLE);

        //mBLEUtil.startScan();

        mWiFiUtil.stopDiscovery();

        mWiFiUtil.startRegistrationAndDiscovery(this,
                getUser().getRegistrationId(),
                getUser().getFullName(),
                // provide empty rideCode to distinguish
                // this broadcast from the driver's one
                "",
                getHandler(),
                1000);

        getHandler().postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        if( btnRefresh != null )
                            btnRefresh.setVisibility(View.VISIBLE);
                        if( progress_refresh != null )
                            progress_refresh.setVisibility(View.GONE);
                    }
                },
                Globals.PASSENGER_DISCOVERY_PERIOD * 1000);

        try {
            boolean showMinMax = true;
            final MaterialDialog dialog = new MaterialDialog.Builder(this)
                    .title(R.string.passenger_progress_dialog)
                    .content(R.string.please_wait)
                    .iconRes(R.drawable.ic_wait)
                    .cancelable(false)
                    .autoDismiss(false)
                    .progress(false, Globals.PASSENGER_DISCOVERY_PERIOD, showMinMax)
                    .show();

            new CountDownTimer(Globals.PASSENGER_DISCOVERY_PERIOD * 1000, 1000) {

                public void onTick(long millisUntilFinished) {

                    Log.d(LOG_TAG,
                            String.format("CountDown tick. Remains %d Drivers size: %d",
                                        millisUntilFinished, mDrivers.size()));


                    if (mDrivers.size() == 0)
                        dialog.incrementProgress(1);
                    else {
                        this.cancel();
                        dialog.dismiss();
                        Log.d(LOG_TAG, "Cancelling timer");
                    }
                }

                public void onFinish() {
                    if (mDrivers.size() == 0)
                        showRideCodePane(R.string.ride_code_dialog_content,
                                Color.BLACK);

                    try {
                        dialog.dismiss();
                    } catch(IllegalArgumentException ex) {
                        // Safely dismiss when called due to
                        // 'Not attached to window manager'.
                        // In this case the activity just was passed by
                        // to some other activity
                    }
                }
            }.start();
        } catch( Exception ex) {
            if( Crashlytics.getInstance() != null )
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());
        }

    }

    @Override
    public boolean handleMessage(Message msg) {
        String strMessage;

        switch (msg.what) {
            case Globals.TRACE_MESSAGE:
                Bundle bundle = msg.getData();
                strMessage = bundle.getString("message");
                trace(strMessage);
                break;

            case Globals.MESSAGE_READ:
                byte[] buffer = (byte[] )msg.obj;
                strMessage = new String(buffer);
                trace(strMessage);
                break;
        }

        return true;

    }

    @Override
    public void trace(final String status) {

    }

    @Override
    public void alert(String message, final String actionIntent) {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int which) {
                if( which == DialogInterface.BUTTON_POSITIVE ) {
                    startActivity(new Intent(actionIntent));
                }
            }};

        new AlertDialogWrapper.Builder(this)
                .setTitle(message)
                .setNegativeButton(R.string.no, dialogClickListener)
                .setPositiveButton(R.string.yes, dialogClickListener)
                .show();
    }

    //
    // Implementations of WifiUtil.IPeersChangedListener
    //
    @Override
    public void add(final WifiP2pDeviceUser device) {

        if( device.getRideCode() == null )
            return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDriversAdapter.add(device);
                mDriversAdapter.notifyDataSetChanged();

                // remove 'type code' menu item
                mDriversShown = true;
                invalidateOptionsMenu();
            }
        });
    }

    //
    // Implementation of WifiP2pManager.ConnectionInfoListener
    //
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo p2pInfo) {

        Thread handler = null;

        if (p2pInfo.isGroupOwner) {

            try {
                handler = new GroupOwnerSocketHandler(this.getHandler());
                handler.start();
            } catch (IOException e){
                trace("Failed to create a server thread - " + e.getMessage());
            }

        } else {

            handler = new ClientSocketHandler(
                    this.getHandler(),
                    p2pInfo.groupOwnerAddress,
                    this,
                    "!!!Message from PASSENGER!!!");
            handler.start();
            trace("Client socket opened.");

//            android.os.Handler h = new android.os.Handler();
//
//            Runnable r = new Runnable() {
//                @Override
//                public void run() {
//
//                    new WiFiUtil.ClientAsyncTask(context, p2pInfo.groupOwnerAddress,
//                                        "From client").execute();
//                }
//            };
//
//            h.postDelayed(r, 2000); // let to server to open the socket in advance

        }

    }

    //
    // Implementation of BLEUtil.IDeviceDiscoveredListener
    //
    @Override
    public void discovered(final BluetoothDevice device) {
        String deviceName = device.getName();
        String deviceAddress = device.getAddress();
        int state = device.getBondState(); // != BluetoothDevice.BOND_BONDED)

        checkAndConnect(device);
    }

    @TargetApi(18)
    private void checkAndConnect(BluetoothDevice device) {
        if( device.getType() == BluetoothDevice.DEVICE_TYPE_LE ) {
            device.connectGatt(this, true, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt,
                                                    int status,
                                                    int newState) {
                    super.onConnectionStateChange(gatt, status, newState);

                    String intentAction;
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.i(LOG_TAG, "Connected to GATT server.");
                        Log.i(LOG_TAG, "Attempting to start service discovery:" +
                                gatt.discoverServices());
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.i(LOG_TAG, "Disconnected from GATT server.");
                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(LOG_TAG, "GATT_SUCCESS");
                    } else {
                        Log.d(LOG_TAG, "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt,
                                                 BluetoothGattCharacteristic characteristic,
                                                 int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        Log.d(LOG_TAG, "GATT_SUCCESS");
                    }

                }
            });
        }
    }

}
