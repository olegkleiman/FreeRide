package com.labs.okey.freeride.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.live.LiveAuthClient;
import com.microsoft.live.LiveAuthException;
import com.microsoft.live.LiveAuthListener;
import com.microsoft.live.LiveConnectSession;
import com.microsoft.live.LiveStatus;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.table.sync.MobileServiceSyncContext;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.ColumnDataType;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.MobileServiceLocalStoreException;
import com.microsoft.windowsazure.mobileservices.table.sync.localstore.SQLiteLocalStore;
import com.twitter.sdk.android.core.TwitterCore;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutionException;

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

    static public void logOff(Context context) {
        MobileServiceAuthenticationProvider tokenProvider = getTokenProvider(context);

        if( tokenProvider == MobileServiceAuthenticationProvider.Facebook ) {
            com.facebook.login.LoginManager.getInstance().logOut();
        } else if( tokenProvider == MobileServiceAuthenticationProvider.Google) {

            if( !Globals.googleApiClient.isConnected())
                Globals.googleApiClient.connect();

//            try {
//                Auth.GoogleSignInApi.signOut(Globals.googleApiClient).setResultCallback(
//                        new ResultCallback<Status>() {
//                            @Override
//                            public void onResult(Status status) {
//
//                            }
//                        }
//                );
//            } catch(Exception ex) {
//                Log.e(LOG_TAG, ex.getMessage());
//            }
        } else if( tokenProvider == MobileServiceAuthenticationProvider.MicrosoftAccount ) {
            if( Globals.liveAuthClient == null )
                Globals.liveAuthClient = new LiveAuthClient(context,
                        Globals.MICROSOFT_CLIENT_ID);

            Globals.liveAuthClient.logout(new LiveAuthListener() {
                @Override
                public void onAuthComplete(LiveStatus status, LiveConnectSession session, Object userState) {

                }

                @Override
                public void onAuthError(LiveAuthException exception, Object userState) {

                }
            });

        } else if( tokenProvider == MobileServiceAuthenticationProvider.Twitter ) {
//            TwitterAuthConfig authConfig =
//                    new TwitterAuthConfig(Globals.TWITTER_CONSUMER_KEY,
//                                          Globals.TWITTER_CONSUMER_SECRET);
//            Fabric.with(this, new Twitter(authConfig), new Digits());

            TwitterCore.getInstance().logOut();
        }

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = sharedPrefs.edit();

        editor.remove(Globals.REG_PROVIDER_PREF);
        editor.remove(Globals.USERIDPREF);
        editor.remove(Globals.TOKENPREF);
        editor.remove(Globals.TOKENSECRETPREF);

        editor.apply();
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

            String issuer = jsonObj.get("iss").getAsString();
            Log.d(LOG_TAG, "JWT issuer: " + issuer);

            String exp = jsonObj.get("exp").getAsString();
            // 'exp' in JWT represents the number of seconds since Jan 1, 1970 UTC
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(Long.parseLong(exp) * 1000);
            Date expiryDate = calendar.getTime();
            if( expiryDate.before(new Date()) ) {
                Log.d(LOG_TAG, "Token expired");
                return true;
            } else {
                Log.d(LOG_TAG, "Token is valid");
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

    public static MobileServiceAuthenticationProvider getTokenProvider(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        String accessTokenProvider = sharedPrefs.getString(Globals.REG_PROVIDER_PREF, "");

        if( accessTokenProvider.equals(Globals.FB_PROVIDER))
            return MobileServiceAuthenticationProvider.Facebook;
        else if( accessTokenProvider.equals(Globals.GOOGLE_PROVIDER))
            return MobileServiceAuthenticationProvider.Google;
        else if( accessTokenProvider.equals(Globals.TWITTER_PROVIDER) ||
                accessTokenProvider.equals(Globals.DIGITS_PROVIDER) )
            return MobileServiceAuthenticationProvider.Twitter;
        else if( accessTokenProvider.equals(Globals.MICROSOFT_PROVIDER))
            return MobileServiceAuthenticationProvider.MicrosoftAccount;
        else
            return null;
    }







}
