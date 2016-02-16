package com.labs.okey.freeride.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.http.NextServiceFilterCallback;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilter;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterRequest;
import com.microsoft.windowsazure.mobileservices.http.ServiceFilterResponse;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncContext;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.ColumnDataType;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.MobileServiceLocalStoreException;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.SQLiteLocalStore;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Oleg on 09-Jun-15.
 */
public class wamsUtils {

    private static final String LOG_TAG = "FR.WAMS";

    static public void sync(MobileServiceClient wamsClient, String... tables) {

        try {

            MobileServiceSyncContext syncContext = wamsClient.getSyncContext();

            if (!syncContext.isInitialized()) {

               for(String table : tables) {

                   Map<String, ColumnDataType> tableDefinition = new HashMap<>();
                   SQLiteLocalStore localStore  = new SQLiteLocalStore(wamsClient.getContext(),
                                                                        table, null, 1);

                   switch( table ) {
                       case "rides": {
                           tableDefinition.put("id", ColumnDataType.String);
                           tableDefinition.put("ridecode", ColumnDataType.String);
                           tableDefinition.put("driverid", ColumnDataType.String);
                           tableDefinition.put("drivername", ColumnDataType.String);
                           tableDefinition.put("created", ColumnDataType.Date);
                           tableDefinition.put("carnumber", ColumnDataType.String);
                           tableDefinition.put("picture_url", ColumnDataType.String);
                           tableDefinition.put("approved", ColumnDataType.Integer);
                           tableDefinition.put("ispicturerequired", ColumnDataType.Boolean);
                           tableDefinition.put("ispicturerequired_bydriver", ColumnDataType.Boolean);
                           tableDefinition.put("gfencename", ColumnDataType.String);
                           tableDefinition.put("__deleted", ColumnDataType.Boolean);
                           tableDefinition.put("__version", ColumnDataType.String);
                       }
                       break;

                       case "gfences": {
                           tableDefinition.put("id", ColumnDataType.String);
                           tableDefinition.put("lat", ColumnDataType.Real);
                           tableDefinition.put("lon", ColumnDataType.Real);
                           tableDefinition.put("when_updated", ColumnDataType.Date);
                           tableDefinition.put("label", ColumnDataType.String);
                           tableDefinition.put("isactive", ColumnDataType.Boolean);
                           tableDefinition.put("__deleted", ColumnDataType.Boolean);
                           tableDefinition.put("__version", ColumnDataType.String);
                       }
                       break;

                       case "geofences": {
                           tableDefinition.put("id", ColumnDataType.String);
                           tableDefinition.put("lat", ColumnDataType.Real);
                           tableDefinition.put("lon", ColumnDataType.Real);
                           tableDefinition.put("label", ColumnDataType.String);
                           tableDefinition.put("radius", ColumnDataType.Integer);
                           tableDefinition.put("isactive", ColumnDataType.Boolean);
                           tableDefinition.put("route_code", ColumnDataType.String);
                           tableDefinition.put("__deleted", ColumnDataType.Boolean);
                           tableDefinition.put("__version", ColumnDataType.String);
                       }
                       break;
                   }

                   localStore.defineTable(table, tableDefinition);
                   syncContext.initialize(localStore, null).get();
                }

            }

        } catch(MobileServiceLocalStoreException | InterruptedException | ExecutionException ex) {
            if( Crashlytics.getInstance() != null)
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage() + " Cause: " + ex.getCause());
        }
    }

    static public boolean loadUserTokenCache(MobileServiceClient wamsClient, Context context){

        try {
            SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
            String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
            if( userID.isEmpty() )
                return false;
            String jwtToken = sharedPrefs.getString(Globals.WAMSTOKENPREF, "");
            if( jwtToken.isEmpty() )
                return false;

            // Check if the token was expired
            if( isJWTTokenExpired(jwtToken) )
                return false;

            MobileServiceUser wamsUser = new MobileServiceUser(userID);
            wamsUser.setAuthenticationToken(jwtToken);
            wamsClient.setCurrentUser(wamsUser);

            return true;

        } catch(Exception ex) {

            if( Crashlytics.getInstance() != null)
                Crashlytics.logException(ex);

            Log.e(LOG_TAG, ex.getMessage());

            return false;
        }
    }

    static private String doUrlEncoding(String input) {
        input = input.replace("+", "-");
        input = input.replace("/", "_");
        input.replace("=", "");

        return input;
    }

    static private String undoUrlEncoding(String input) {
        input = input.replace("-", "+");
        input = input.replace("_", "/");

        switch ( input.length() % 4 ) {
            case 0:
                break;

            case 2:
                input += "==";
                break;

            case 3:
                input += "=";
                break;

            default:
                return "";

        }

        return input;
    }

    static public boolean isJWTTokenExpired(String jwtToken) throws Exception{
        StringTokenizer jwtTokens = new StringTokenizer(jwtToken, ".");

        // JWT (http://self-issued.info/docs/draft-ietf-oauth-json-web-token-25.html) is a concatenation of
        // 1) a JSON Object Signing and Encryption (JOSE) header,
        // 2) a JWT claim set,
        // 3) and a signature over the two.
        // Totally, it must contain exactly 3 tokens
        if( jwtTokens.countTokens() != 3 )
            return false;

        jwtTokens.nextToken(); // skip JOSE header
        String jwtClaims = jwtTokens.nextToken(); // find JWT claims

        // JWT claims is converted to base64 and made URL friendly by decoding

        jwtClaims = undoUrlEncoding(jwtClaims);
        if( jwtClaims.isEmpty() ) {
            Log.e(LOG_TAG, "JWT token is invalid");
            return false;
        }

        // decode base64 & extract expiration date
        try {
            byte[] jwtData = Base64.decode(jwtClaims, Base64.DEFAULT);
            String jsonString = new String(jwtData, "UTF-8");
            JsonObject jsonObj = (new JsonParser()).parse(jsonString).getAsJsonObject();

            String exp = jsonObj.get("exp").getAsString();
            // 'exp' in JWT represents the number of seconds since Jan 1, 1970 UTC
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(Long.parseLong(exp) * 1000);
            Date expiryDate = calendar.getTime();
            if( expiryDate.before(new Date()) ) {
                Log.d(LOG_TAG, "WAMS token expired");
                return true;
            } else {
                Log.d(LOG_TAG, "WAMS token is valid");
                return false;
            }
            //return !expiryDate.before(new Date());

        } catch(UnsupportedEncodingException ex) {
            throw new Exception(ex);
        }

    }

    static public MobileServiceClient init(Context context) throws MalformedURLException {

        MobileServiceClient wamsClient = new MobileServiceClient(
                    Globals.WAMS_URL,
                    Globals.WAMS_API_KEY,
                    context);
//                .withFilter(new ProgressFilter())
//                .withFilter(new RefreshTokenCacheFilter());

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
        MobileServiceUser wamsUser = new MobileServiceUser(userID);

        String token = sharedPrefs.getString(Globals.WAMSTOKENPREF, "");
        // According to this article (http://www.thejoyofcode.com/Setting_the_auth_token_in_the_Mobile_Services_client_and_caching_the_user_rsquo_s_identity_Day_10_.aspx)
        // this should be JWT token, so use WAMS_TOKEN
        wamsUser.setAuthenticationToken(token);

        wamsClient.setCurrentUser(wamsUser);

        return  wamsClient;

    }

    /**
     * The RefreshTokenCacheFilter class filters responses for HTTP status code 401.
     * When 401 is encountered, the filter calls the authenticate method on the
     * UI thread. Outgoing requests and retries are blocked during authentication.
     * Once authentication is complete, the token cache is updated and
     * any blocked request will receive the X-ZUMO-AUTH header added or updated to
     * that request.
     */
    static public class RefreshTokenCacheFilter implements ServiceFilter {

        AtomicBoolean mAtomicAuthenticatingFlag = new AtomicBoolean();

        /**
         * Detects if authentication is in progress and waits for it to complete.
         * Returns true if authentication was detected as in progress. False otherwise.
         */
        public boolean detectAndWaitForAuthentication() {
            return false;
        }


        /**
         * Waits for authentication to complete then adds or updates the token
         * in the X-ZUMO-AUTH request header.
         *
         * @param request
         *            The request that receives the updated token.
         */
        private void waitAndUpdateRequestToken(ServiceFilterRequest request) {
//        user = mClient.getCurrentUser();
//        if (user != null)
//        {
//            request.removeHeader("X-ZUMO-AUTH");
//            request.addHeader("X-ZUMO-AUTH", user.getAuthenticationToken());
//        }
        }

        @Override
        public ListenableFuture<ServiceFilterResponse> handleRequest(ServiceFilterRequest request,
                                        NextServiceFilterCallback nextServiceFilterCallback) {

            ListenableFuture<ServiceFilterResponse> future = null;
            ServiceFilterResponse response = null;
            int responseCode = 401;

            // Send the request down the filter chain
            // retrying up to 5 times on 401 response codes.
            //for (int i = 0; (i < 5 ) && (responseCode == 401); i++) {

                future = nextServiceFilterCallback.onNext(request);

                try {
                    response = future.get();
                    responseCode = response.getStatus().getStatusCode();
                } catch (Exception ex) {
                    Log.e(LOG_TAG, ex.getMessage());
                }

            //}

            return null;
        }
    }

    /**
     * The ProgressFilter class renders a progress bar on the screen during the time the App is waiting
     * for the response of a previous request.
     * the filter shows the progress bar on the beginning of the request, and hides it when the response arrived.
     */
    static public class ProgressFilter implements ServiceFilter {
        @Override
        public ListenableFuture<ServiceFilterResponse> handleRequest(ServiceFilterRequest request,
                                                                     NextServiceFilterCallback nextServiceFilterCallback) {

            final SettableFuture<ServiceFilterResponse> resultFuture = SettableFuture.create();

//            runOnUiThread(new Runnable() {
//
//                @Override
//                public void run() {
//                    if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.VISIBLE);
//                }
//            });

            ListenableFuture<ServiceFilterResponse> future = nextServiceFilterCallback.onNext(request);


            Futures.addCallback(future, new FutureCallback<ServiceFilterResponse>() {
                @Override
                public void onFailure(Throwable e) {
                    resultFuture.setException(e);
                }

                @Override
                public void onSuccess(ServiceFilterResponse response) {
//                    runOnUiThread(new Runnable() {
//
//                        @Override
//                        public void run() {
//                            if (mProgressBar != null) mProgressBar.setVisibility(ProgressBar.GONE);
//                        }
//                    });

                    resultFuture.set(response);
                }
            });

            return resultFuture;
        }
    }

}
