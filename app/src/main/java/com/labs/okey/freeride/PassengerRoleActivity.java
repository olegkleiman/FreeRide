package com.labs.okey.freeride;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.annotation.CallSuper;
import android.support.annotation.ColorInt;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.design.widget.Snackbar;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.InputType;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.afollestad.materialdialogs.AlertDialogWrapper;
import com.afollestad.materialdialogs.MaterialDialog;
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
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.WiFiUtil;
import com.microsoft.windowsazure.mobileservices.MobileServiceException;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;
import com.skyfishjy.library.RippleBackground;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PassengerRoleActivity extends BaseActivityWithGeofences
    implements ITrace,
        Handler.Callback,
        IRecyclerClickListener,
        IRefreshable,
        WiFiUtil.IPeersChangedListener,
        BLEUtil.IDeviceDiscoveredListener,
        WifiP2pManager.ConnectionInfoListener,
        WAMSVersionTable.IVersionMismatchListener{

    private static final String LOG_TAG = "FR.Passenger";

    String mUserID;

    final int MAKE_PICTURE_REQUEST = 1;
    // handled  in onActivityResult

    Boolean mDriversShown;
    TextView mTxtMonitorStatus;

    MobileServiceTable<Join> joinsTable;

    WiFiUtil mWiFiUtil;
    BLEUtil mBLEUtil;
    WiFiPeersAdapter2 mDriversAdapter;
    public List<WifiP2pDeviceUser> drivers = new ArrayList<>();

    private Handler handler = new Handler(this);
    public Handler getHandler() {
        return handler;
    }

    @Override
    @CallSuper
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passenger);

        setupUI(getString(R.string.title_activity_passenger_role), "");
        wamsInit(false); // without auto-update for this acivity

        joinsTable = getMobileServiceClient().getTable("joins", Join.class);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mUserID = sharedPrefs.getString(Globals.USERIDPREF, "");

        new Thread() {
            @Override
            public void run(){

                try{

                    while (true) {

                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {

                                String message = Globals.isInGeofenceArea() ?
                                        Globals.getMonitorStatus() :
                                        getString(R.string.geofence_outside);

                                Log.i(LOG_TAG, message);
//                                mTxtMonitorStatus.setText(message);

                            }
                        });

                        Thread.sleep(1000);
                    }
                }
                catch(InterruptedException ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }

            }
        }.start();

        if( Globals.getRideCode() == null
            || Globals.getRideCode().isEmpty() ) {
            // Start WiFi-Direct serviceDiscovery
            // for (hopefully) already published service
            mWiFiUtil = new WiFiUtil(this);

            // Start BLE advertising
            mBLEUtil = new BLEUtil(this);

//            RippleBackground rippleBackground=(RippleBackground)findViewById(R.id.ripple_background_content);
//            ImageView imageView = (ImageView)findViewById(R.id.centerImage);
//            rippleBackground.startRippleAnimation();
//
//            startAdvertise();

            refresh();
        }
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
                                    drivers);
        driversRecycler.setAdapter(mDriversAdapter);

        mDriversShown = false;

        mTxtMonitorStatus = (TextView)findViewById(R.id.status_monitor);
        Globals.setMonitorStatus(getString(R.string.geofence_outside_title));

    }

    @Override
    @CallSuper
    public void onResume() {
        super.onResume();

        if( mWiFiUtil != null)
            mWiFiUtil.registerReceiver(this);
    }

    @Override
    @CallSuper
    public void onPause() {
        super.onPause();

        if( mWiFiUtil != null ) {
            mWiFiUtil.unregisterReceiver();
            mWiFiUtil.stopDiscovery();
        }

        if( mBLEUtil != null )
            mBLEUtil.unregisterReceiver();
    }

    @Override
    @CallSuper
    protected void onStop() {
        if( mWiFiUtil != null )
            mWiFiUtil.removeGroup();

        // Ride Code will be re-newed on next activity's launch
        Globals.setRideCode("");

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_passenger_role, menu);
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
        } else if ( id == R.id.action_camera_cv) {
            onCameraCV(null);
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if( requestCode == MAKE_PICTURE_REQUEST) {
            if( resultCode == RESULT_OK ) {
            // How to distinguish between successful connection
            // and just pressing back from there?
            //wamsInit(true);
            }
        }
    }

    @UiThread
    private void showRideCodePane(@StringRes int contentStringResId,
                                  @ColorInt int contentColor){

        try {
            String dialogContent = getString(contentStringResId);

            new MaterialDialog.Builder(this)
                    .callback(new MaterialDialog.ButtonCallback(){
                        @Override
                        public void onNegative(MaterialDialog dialog){
                            refresh();
                        }
                    })
                    .title(R.string.ride_code_title)
                    .content(dialogContent)
                    .positiveText(R.string.ok)
                    .negativeText(R.string.code_retry_action)
                    .contentColor(contentColor)
                    .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_NUMBER)
                    .inputMaxLength(Globals.RIDE_CODE_INPUT_LENGTH)
                    .input(R.string.ride_code_hint,
                            R.string.ride_code_refill,
                            new MaterialDialog.InputCallback() {
                                @Override
                                public void onInput(MaterialDialog dialog, CharSequence input) {
                                    String rideCode = input.toString();
                                    onSubmitCode(rideCode);
                                }
                            }

                    ).show();
        } catch( Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    public void onCameraCV(View view) {

        //String rideCode = ((TextView)findViewById(R.id.txtRideCode)).getText().toString();

        Intent intent = new Intent(this, CameraCVActivity.class);
        //intent.putExtra("rideCode", rideCode);
        startActivityForResult(intent, MAKE_PICTURE_REQUEST);
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
        WifiP2pDeviceUser driverDevice = drivers.get(position);

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

            final String rideCode = driverDevice.getRideCode();

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialogInterface, int which) {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        onSubmitCode(rideCode);
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

    public void onSubmitCode(final String rideCode){
        final String android_id = Settings.Secure.getString(this.getContentResolver(),
                Settings.Secure.ANDROID_ID);
        final View v = findViewById(R.id.passenger_internal_layout);

        new AsyncTask<Void, Void, Void>() {

            Exception mEx;
            MaterialDialog progress;

            @Override
            protected void onPreExecute() {
                progress = new MaterialDialog.Builder(PassengerRoleActivity.this)
                        .title(R.string.processing)
                        .content(R.string.please_wait)
                        .progress(true, 0)
                        .show();
            }

            @Override
            protected void onPostExecute(Void result){

                progress.dismiss();

                if( mEx != null ) {

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
                                // However, ride code was successfully validated,
                                // so remember it for the future pictures
                                Globals.setRideCode(rideCode);
                                onCameraCV(null);
                                break;

                            case 404: // HTTP 'Not found' means 'no such ride code'
                                      // i.e.
                                // try again with appropriate message
                                showRideCodePane(R.string.ride_code_wrong,
                                                 Color.RED);
                                break;

                            case 503: // HTTP 'Service Unavailable' interpreted as 'Connection Lost'
                                // Try again
                                showRideCodePane(R.string.connection_lost,
                                                 Color.RED);
                                break;
                        }
                    } catch( Exception e) {
                        Log.e(LOG_TAG, e.getMessage());
                    }

                }
            }

            @Override
            protected Void doInBackground(Void... voids) {

                try{
                    Join _join  = new Join();
                    _join.setWhenJoined(new Date());
                    _join.setRideCode(rideCode);
                    _join.setDeviceId(android_id);

                    joinsTable.insert(_join).get();

                } catch( ExecutionException | InterruptedException ex ) {

                    mEx = ex;
                    Log.e(LOG_TAG, ex.getMessage());
                }

                return null;
            }
        }.execute();
    }

    private void showSubmitCodeDialog() {
        new MaterialDialog.Builder(this)
                .title(R.string.ride_code_title)
                .content(R.string.ride_code_dialog_content)
                .inputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_CLASS_NUMBER)
                .inputMaxLength(5)
                .input(R.string.ride_code_hint,
                        R.string.ride_code_refill,
                        new MaterialDialog.InputCallback() {
                            @Override
                            public void onInput(MaterialDialog dialog, CharSequence input) {
                                String rideCode = input.toString();
                                onSubmitCode(rideCode);
                            }
                        }

                ).show();
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

        mTxtMonitorStatus.setText(R.string.passenger_adv_description);

    }


    private void switchLayouts() {
        findViewById(R.id.ripple_background_content).setVisibility(View.GONE);
        findViewById(R.id.passenger_internal_layout).setVisibility(View.VISIBLE);
    }

    //
    // Implementation of IRefreshable
    //
    @Override
    @UiThread
    public void refresh() {
        drivers.clear();
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
                    .autoDismiss(false)
                    .progress(false, Globals.PASSENGER_DISCOVERY_PERIOD, showMinMax)
                    .show();

            new CountDownTimer(Globals.PASSENGER_DISCOVERY_PERIOD * 1000, 1000) {

                public void onTick(long millisUntilFinished) {

                    Log.d(LOG_TAG,
                            String.format("CountDown tick. Remains %d Drivers size: %d",
                                        millisUntilFinished, drivers.size()));


                    if (drivers.size() == 0)
                        dialog.incrementProgress(1);
                    else {
                        this.cancel();
                        dialog.dismiss();
                        Log.d(LOG_TAG, "Cancelling timer");
                    }
                }

                public void onFinish() {
                    if (drivers.size() == 0)
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
