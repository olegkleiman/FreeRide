package com.labs.okey.freeride;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.UiThread;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.labs.okey.freeride.adapters.DrawerAccountAdapter;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.labs.okey.freeride.utils.WAMSVersionTable;
import com.labs.okey.freeride.utils.wamsUtils;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;

import java.lang.ref.WeakReference;
import java.net.MalformedURLException;
import java.util.StringTokenizer;

/**
 * Created by Oleg on 22-Aug-15.
 */
public class BaseActivity extends AppCompatActivity
        implements GoogleApiClient.OnConnectionFailedListener{

    private final String    LOG_TAG = getClass().getSimpleName();
    static final int REQUEST_CODE_RECOVER_PLAY_SERVICES = 1001;

    protected String[] mDrawerTitles;
    protected int DRAWER_ICONS[] = {
            R.drawable.ic_action_start,
            R.drawable.ic_action_myrides,
            R.drawable.ic_action_settings,
            R.drawable.ic_action_tutorial
    };

    RecyclerView mDrawerRecyclerView;

    private User mUser;
    public User getUser() { return mUser; }

    private GoogleApiClient mGoogleApiClient;
    public GoogleApiClient getGoogleApiClient() { return mGoogleApiClient; }

    private MobileServiceClient wamsClient;
    public Boolean isWamsInitialized() { return wamsClient != null; }
    public MobileServiceClient getMobileServiceClient() { return wamsClient; }

    private WAMSVersionTable wamsVersionTable;

    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;

    MediaPlayer beepSuccess;
    MediaPlayer beepError;

    int mNotificationId = 777;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        buildGoogleApiClient();

        // Connect the Google API client.
        if( mGoogleApiClient != null )
            mGoogleApiClient.connect();


        beepSuccess = MediaPlayer.create(this, R.raw.success);
        beepError = MediaPlayer.create(this, R.raw.error);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (mDrawerToggle != null)
            mDrawerToggle.syncState();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerToggle != null && mDrawerToggle.onOptionsItemSelected(item))
            return true;

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(),
                                this, REQUEST_CODE_RECOVER_PLAY_SERVICES).show();
    }

    @Override
    protected void onDestroy() {
        // Disconnecting the Google API client invalidates it.
        if( mGoogleApiClient != null )
            mGoogleApiClient.disconnect();

        super.onDestroy();

        unbindDrawables(findViewById(R.id.content_frame));
        System.gc();
    }

    private void unbindDrawables(View view) {
        if( view == null )
            return;

        if (view.getBackground() != null) {
            view.getBackground().setCallback(null);
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                unbindDrawables(((ViewGroup) view).getChildAt(i));
            }
            ((ViewGroup) view).removeAllViews();
        }
    }

    protected synchronized void buildGoogleApiClient() {
        try {

            GoogleApiClient.OnConnectionFailedListener connectionFailedListener = this;

            GoogleApiClient.Builder builder =
                    new GoogleApiClient.Builder(this)
                            .addApi(LocationServices.API);

            if( this instanceof GoogleApiClient.ConnectionCallbacks) {
                GoogleApiClient.ConnectionCallbacks callbacksImplementer =
                        (GoogleApiClient.ConnectionCallbacks)this;
                builder.addConnectionCallbacks(callbacksImplementer);
            }

            builder.addOnConnectionFailedListener(connectionFailedListener);

            mGoogleApiClient = builder.build();

        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getMessage());
        }

    }

    public boolean isConnectedToNetwork() {
        ConnectivityManager connManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connManager.getActiveNetworkInfo();
        if( info == null )
            return false;
        else
            return info.isConnectedOrConnecting();
    }

    public MobileServiceAuthenticationProvider getTokenProvider() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String accessTokenProvider = sharedPrefs.getString(Globals.REG_PROVIDER_PREF, "");

        if( accessTokenProvider.equals(Globals.FB_PROVIDER))
            return MobileServiceAuthenticationProvider.Facebook;
        else if( accessTokenProvider.equals(Globals.TWITTER_PROVIDER) ||
                accessTokenProvider.equals(Globals.DIGITS_PROVIDER) )
            return MobileServiceAuthenticationProvider.Twitter;
        else if( accessTokenProvider.equals(Globals.MICROSOFT_PROVIDER))
            return MobileServiceAuthenticationProvider.MicrosoftAccount;
        else if( accessTokenProvider.equals(Globals.GOOGLE_PROVIDER ) )
            return MobileServiceAuthenticationProvider.Google;
        else
            return null;
    }

    public void loadBitmap(int resId, ImageView imageView) {
        BitmapWorkerTask task = new BitmapWorkerTask(imageView);
        task.execute(resId);
    }

    class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap>{

        private final WeakReference<ImageView> imageViewReference;
        private int data;
        int  width, height;

        public BitmapWorkerTask(ImageView imageView){
            imageViewReference = new WeakReference<>(imageView);
            width = imageView.getWidth();
            height = imageView.getHeight();
        }

        // Decode image in background
        @Override
        protected Bitmap doInBackground(Integer... params){
            data = params[0];
            return decodeSampledBitmapFromResource(getResources(), data, width, height);
        }

        // Once completed, see if ImageView is still around and set bitmap
        @Override
        protected void onPostExecute(Bitmap bitmap){
            if( bitmap != null ) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null)
                    imageView.setImageBitmap(bitmap);
            }
        }
    }

    private Bitmap decodeSampledBitmapFromResource(Resources res, int resId,
                                                   int reqWidth, int reqHeight){
        // First decode with inJustDecodeBounds=true to check dimensions
        // Calculated dimensions are stored in BitmapFactory.Options
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    public int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    @UiThread
    protected void setupUI(String title, String subTitle) {
        Toolbar toolbar = (Toolbar) findViewById(R.id.fastride_toolbar);
        if( toolbar != null ) {
            setSupportActionBar(toolbar);

            ActionBar actionBar = getSupportActionBar();
            if( actionBar != null ) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setHomeButtonEnabled(true);
                actionBar.setDisplayShowTitleEnabled(true);

                actionBar.setTitle(title);
                actionBar.setSubtitle(subTitle);
            }
        }

        mDrawerRecyclerView = (RecyclerView) findViewById(R.id.left_drawer);
        mDrawerRecyclerView.setHasFixedSize(true);
        mDrawerRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mDrawerRecyclerView.setItemAnimator(new DefaultItemAnimator());

        mUser = User.load(this);

        mDrawerTitles = getResources().getStringArray(R.array.drawers_array_drawer);
        String pictureURL = mUser.getPictureURL();
        if( !mUser.getPictureURL().contains("https") )
            pictureURL = mUser.getPictureURL().replace("http", "https");
        DrawerAccountAdapter drawerRecyclerAdapter =
                new DrawerAccountAdapter(this,
                        mDrawerTitles,
                        DRAWER_ICONS,
                        mUser.getFirstName() + " " + mUser.getLastName(),
                        mUser.getEmail(),
                        pictureURL);
        mDrawerRecyclerView.setAdapter(drawerRecyclerAdapter);

        final Context ctx = this;

        LinearLayout aboutLayout = (LinearLayout) findViewById(R.id.about_row);
        aboutLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(ctx, AboutActivity.class);
                ctx.startActivity(intent);
            }
        });

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // set a custom shadow that overlays the main content when the drawer opens
            mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        }

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout,
                R.string.drawer_open, R.string.drawer_close);

        mDrawerToggle.setDrawerIndicatorEnabled(true);
        mDrawerLayout.setDrawerListener(mDrawerToggle);

    }

    public void wamsInit(Boolean withAutoUpdate) {

        try {
            wamsClient = wamsUtils.init(this);

            if (withAutoUpdate) {
                startAutoUpdate();
            }
        } catch(MalformedURLException ex ) {
            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
        }

//        try {
//            wamsClient = new MobileServiceClient(
//                    Globals.WAMS_URL,
//                    Globals.WAMS_API_KEY,
//                    this);
//
//            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
//            String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
//            MobileServiceUser wamsUser = new MobileServiceUser(userID);
//
//            String token = sharedPrefs.getString(Globals.WAMSTOKENPREF, "");
//            // According to this article (http://www.thejoyofcode.com/Setting_the_auth_token_in_the_Mobile_Services_client_and_caching_the_user_rsquo_s_identity_Day_10_.aspx)
//            // this should be JWT token, so use WAMS_TOKEN
//            wamsUser.setAuthenticationToken(token);
//
//            wamsClient.setCurrentUser(wamsUser);
//
//            if( withAutoUpdate ) {
//                startAutoUpdate();
//            }
//
//        } catch(MalformedURLException ex ) {
//            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
//        }

    }

    private void startAutoUpdate() {
        try {

            WAMSVersionTable.IVersionMismatchListener listener = null;
            if (this instanceof WAMSVersionTable.IVersionMismatchListener) {
                listener = (WAMSVersionTable.IVersionMismatchListener) this;
            }
            wamsVersionTable = new WAMSVersionTable(this, listener);
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            String packageVersionName = info.versionName;
            if (!packageVersionName.isEmpty()) {

                StringTokenizer tokens = new StringTokenizer(packageVersionName, ".");
                if (tokens.countTokens() > 0) {
                    int majorPackageVersion = Integer.parseInt(tokens.nextToken());
                    int minorPackageVersion = Integer.parseInt(tokens.nextToken());
                    wamsVersionTable.compare(majorPackageVersion, minorPackageVersion);
                }
            }

        } catch (PackageManager.NameNotFoundException ex) {

            Log.e(LOG_TAG, ex.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void checkCameraAndStoragePermissions() throws SecurityException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if ( (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED )
                    || (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) )
                throw new SecurityException();
        }
    }

    public void cancelNotification() {
        // Get an instance of the Notification manager
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        notificationManager.cancel(mNotificationId);
    }

    /**
     * Posts a notification in the notification bar when a transition is detected.
     * If the user clicks the notification, control goes to the MainActivity.
     */
    public void sendNotification(String notificationDetails, Class<?> cls) {
//        // Create an explicit content Intent that starts the main Activity.
//        Intent notificationIntent = new Intent(getApplicationContext(), cls);
//
//        int notificationId = new Random().nextInt();
//        notificationIntent.putExtra(Globals.NOTIFICATION_ID_EXTRA, notificationId);
//        notificationIntent.setAction(Globals.ACTION_CONFIRM);
//
//        // This stack builder object will contain an artificial back stack for the passed Activity
//        // This ensures that navigating backward from it leads out of the application to the Start screen.
//        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
//
//        // Add the main Activity to the task stack as the parent.
//        stackBuilder.addParentStack(MainActivity.class);
//
//        // Push the content Intent onto the stack.
//        stackBuilder.addNextIntent(notificationIntent);

//        // Get a PendingIntent containing the entire back stack.
//        PendingIntent notificationPendingIntent =
//                stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent confirmIntent = new Intent(this, cls);
        confirmIntent.setAction(Globals.ACTION_CONFIRM);
        confirmIntent.putExtra(Globals.NOTIFICATION_ID_EXTRA, mNotificationId);
        PendingIntent piConfirm = PendingIntent.getActivity(this,
                111, // request code
                confirmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);


        // Get a notification builder that's compatible with platform versions >= 4
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);

        String title = getString(R.string.app_label);
        // Define the notification settings.
        builder.setSmallIcon(R.mipmap.ic_launcher2)
                // In a real app, you may want to use a library like Volley
                // to decode the Bitmap.
                .setLargeIcon(BitmapFactory.decodeResource(getResources(),
                        R.mipmap.ic_launcher2))
                .setColor(Color.RED)
                .setContentTitle(title)
                .setAutoCancel(true) // Dismiss notification once the user touches it.
                .setContentText(notificationDetails)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(piConfirm);

//                .addAction(R.drawable.confirm,
//                        "confirm", piConfirm)
//                .addAction(R.drawable.cancel,
//                        "cancel", piConfirm);


        // Get an instance of the Notification manager
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // Issue the notification
        notificationManager.notify(mNotificationId, builder.build());

    }


}
