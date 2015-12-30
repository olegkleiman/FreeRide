package com.labs.okey.freeride.gcm;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.labs.okey.freeride.DriverRoleActivity;
import com.labs.okey.freeride.MainActivity;
import com.labs.okey.freeride.R;
import com.labs.okey.freeride.model.PassengerFace;
import com.labs.okey.freeride.model.User;
import com.labs.okey.freeride.utils.Globals;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.MobileServiceList;
import com.microsoft.windowsazure.mobileservices.notifications.MobileServicePush;
import com.microsoft.windowsazure.mobileservices.notifications.Registration;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import java.net.MalformedURLException;
import java.util.Set;
import java.util.concurrent.ExecutionException;


/**
 * Created by Oleg Kleiman on 11-Apr-15.
 */
public class GCMHandler extends  com.microsoft.windowsazure.notifications.NotificationsHandler{

    private static final String LOG_TAG = "FR.GCMHandler";

    Context ctx;
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATIONS_HANDLER_CLASS = "WAMS_NotificationsHandlerClass";

    @Override
    public void onRegistered(final Context context,  final String gcmRegistrationId) {
        super.onRegistered(context, gcmRegistrationId);

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String userID = sharedPrefs.getString(Globals.USERIDPREF, "");

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {

                try {
                    String[] tags = {userID};

//                    MobileServiceClient wamsClient = new MobileServiceClient(
//                                                                Globals.WAMS_URL,
//                                                                Globals.WAMS_API_KEY,
//                                                                context);

                    // Better use WAMS SDK v2 like:
                    final MobileServicePush msp = MainActivity.wamsClient.getPush();
                    ListenableFuture<Registration> lf = msp.register(gcmRegistrationId, tags);

                    Futures.addCallback(lf, new FutureCallback<Registration>() {
                        @Override
                        public void onSuccess(Registration result) {
                            Log.d(LOG_TAG, "Azure Notification Hub registration succeeded");
                        }

                        @Override
                        public void onFailure(Throwable t) {

                            try {
                                verifyStorageVersion(context);

                                //msp.unregister().get();
                                //msp.unregisterAll(gcmRegistrationId).get();
                            } catch (Exception ex) {
                                Log.e(LOG_TAG, ex.getLocalizedMessage());
                            }
                        }
                    });

                    Registration _reg = lf.get();

                } catch (Exception e) {

                    if( Crashlytics.getInstance() != null)
                        Crashlytics.logException(e);

                    String msg = e.getLocalizedMessage();
                    Log.e(LOG_TAG, "Registration error: " + msg);

                }

                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onUnregistered(Context context, String gcmRegistrationId) {
        super.onUnregistered(context, gcmRegistrationId);
    }

    @Override
    public void onReceive(Context context, Bundle bundle) {

        ctx = context;
        boolean bSend = false;

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        String userRegistrationId = sharedPrefs.getString(Globals.USERIDPREF, "");

        String extras = bundle.getString("extras");
        if( extras == null || extras.isEmpty() ) {
            if( Crashlytics.getInstance() != null)
                Crashlytics.logException(new Throwable(ctx.getString(R.string.no_extra)));
            return;
        }

        boolean _bUserSelfPictured = false;
        String[] tokens = extras.split(";");
        final String userId = tokens[0];

        // Prevent to receive the notifications from myself
        if( userId != null
                && userId.equalsIgnoreCase(userRegistrationId))
            return;

        if( tokens.length > 1) { // FaceID is embedded

            _bUserSelfPictured = true;

            PassengerFace pf = new PassengerFace();
            String faceID = tokens[1];
            pf.setFaceId(faceID);

            if( tokens.length > 2 ) {
                String pictureURL = tokens[2];
                pf.setPictureUrl(pictureURL);
            }

            Globals.add_PassengerFace(pf);
        }

        final boolean bUserSelfPictured = _bUserSelfPictured;

        if( (userId != null  && !Globals.isPassengerIdJoined(userId) )
                || bUserSelfPictured ) {

            Globals.addMyPassengerId(userId);

            try {

                final MobileServiceTable<User> usersTable =
                        new MobileServiceClient(
                                Globals.WAMS_URL,
                                Globals.WAMS_API_KEY,
                                ctx)
                                .getTable("users", User.class);

                new AsyncTask<Void, Void, Void>() {

                    @Override
                    protected Void doInBackground(Void... voids) {

                        try {

                            MobileServiceList<User> users =
                                    usersTable.where().field("registration_id").eq(userId).execute().get();
                            if (users.size() > 0) {
                                User passenger = users.get(0);
                                passenger.setSelfPictured(bUserSelfPictured);
                                Globals.addMyPassenger(passenger);
                            }
                        } catch(ExecutionException | InterruptedException ex ){
                            if( Crashlytics.getInstance() != null)
                                Crashlytics.logException(ex);

                            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
                        }

                        return null;
                    }
                }.execute();


            } catch(MalformedURLException ex ) {
                if( Crashlytics.getInstance() != null)
                    Crashlytics.logException(ex);

                Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
            }

        }
//        String faceId  = bundle.getString("extras");
//
//        // TODO: review this limitation for pictures
//        if( Globals.passengerFaces.size() <= 4 ) {
//
//            PassengerFace pf = new PassengerFace(faceId);
//
//            int nIndex = Globals.passengerFaces.indexOf(pf);
//            Globals.passengerFaces.add(pf);
//
//            faceapiUtils.Analyze(ctx);
//        }

        String message = bundle.getString("message");
        tokens = message.split(";");
        if( tokens.length > 1) {
            message = tokens[1];

            int flag = Integer.parseInt(tokens[0]);
            // Message flag (first token) means only by 4 bit: X000 where X=1 means display message, X=0 - not display
            bSend = ( flag >> 3 == 0) ? false : true;
        }

        if( bSend ) {
            String title = context.getResources().getString(R.string.app_label);
            sendNotification(message, title);
        }
    }

    private void sendNotification(String msg, String title) {
        NotificationManager mNotificationManager = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);

        // TODO: Define to which activity the notification should be delivered
        //      (Optionally) Use TaskStackBuilder if this activity is deep
        Intent launchIntent = new Intent(ctx, DriverRoleActivity.class);
        Bundle b = new Bundle();
        launchIntent.putExtras(b);

        PendingIntent contentIntent =
                PendingIntent.getActivity(ctx, 0,
                        launchIntent, PendingIntent.FLAG_CANCEL_CURRENT);

        // Use TaskStackBuilder to build the back stack and get the PendingIntent
//        PendingIntent pendingIntent =
//                TaskStackBuilder.create(ctx)
//                        // add all of DetailsActivity's parents to the stack,
//                        // followed by DetailsActivity itself
//                        .addNextIntentWithParentStack(launchIntent)
//                        .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(ctx)
                        .setSmallIcon(R.mipmap.ic_launcher2)
                        .setVibrate(new long[]{500, 500})
                        .setContentTitle(title)
                        .setAutoCancel(true)
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(msg))
                        .setContentText(msg);

        mBuilder.setContentIntent(contentIntent);
        //mBuilder.setContentIntent(pendingIntent);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private static final String STORAGE_PREFIX = "__NH_";
    private static final String STORAGE_VERSION_KEY = "STORAGE_VERSION";
    private static final String STORAGE_VERSION = "1.0.0";

    private void verifyStorageVersion(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        String currentStorageVersion = preferences.getString(STORAGE_PREFIX + STORAGE_VERSION_KEY, "");

        //if (!currentStorageVersion.equals(STORAGE_VERSION)) {

            SharedPreferences.Editor editor = preferences.edit();

            Set<String> keys = preferences.getAll().keySet();

            for (String key : keys) {
                if (key.startsWith(STORAGE_PREFIX)) {
                    editor.remove(key);
                }
            }

            editor.commit();

        //}

    }

}
