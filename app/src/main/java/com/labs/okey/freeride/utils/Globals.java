package com.labs.okey.freeride.utils;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.renderscript.Matrix4f;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.maps.model.LatLng;
import com.labs.okey.freeride.model.PassengerFace;
import com.labs.okey.freeride.model.User;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric.sdk.android.Fabric;

/**
 * Created by Oleg Kleiman on 22-Aug-15.
 */


public class Globals {

    public static int REQUIRED_PASSENGERS_NUMBER = 3;

//    @IntDef({RIDE_APPROVED, RIDE_NOT_APPROVED, RIDE_WAITING})
//    public static int RIDE_APPROVED = 0;
//    public static int RIDE_NOT_APPROVED = 1;
//    public static int RIDE_WAITING = 2;
//
//    public abstract int getRideStatus();

    public static String userID;
    public static boolean myrides_update_required = true;

    public enum RIDE_STATUS {
        WAIT, // = 0
        APPROVED, // = 1
        APPROVED_BY_SELFY, // = 2
        DENIED // = 3
    }; // use it as casted to int like : Globals.RIDE_STATUS.APPROVED.ordinal())

    public enum LayoutManagerType {
        GRID_LAYOUT_MANAGER,
        LINEAR_LAYOUT_MANAGER
    }

    public static int NUM_OF_EMOJIS = 7;

    private static class DManClassFactory {

        static DrawMan drawMan;

        static DrawMan getDrawMan(){
            if( drawMan == null )
                return new DrawMan();
            else
                return drawMan;
        }
    }
    public static final DrawMan drawMan = DManClassFactory.getDrawMan();

    private static Boolean _monitorInitialized = false;
    private static Boolean isMonitorInitialized() {
        return _monitorInitialized;
    }
    public static void initializeMonitor(Context ctx){

        if( isMonitorInitialized() )
            return;

        try {

            Fabric.with(ctx, new Crashlytics());

            User user = User.load(ctx);
            Crashlytics.setUserIdentifier(user.getRegistrationId());
            Crashlytics.setUserName(user.getFullName());
            Crashlytics.setUserEmail(user.getEmail());

            _monitorInitialized = true;



        } catch(Exception e) {
            String msg = e.getMessage();
            if( msg == null )
                msg = "Could not instantiate Crashlytics";
            Log.e("FR", msg);
        }
    }

    public static float PICTURE_CORNER_RADIUS = 20;
    public static float PICTURE_BORDER_WIDTH = 4;

    public static int RIDE_CODE_INPUT_LENGTH = 6;

    private static boolean _passengerListAlerted = false;
    public static boolean isPassengerListAlerted() {
        return _passengerListAlerted;
    }
    public static void setPassengerListAlerted(boolean value){
        _passengerListAlerted = value;
    }

    private static List<String> _passengersIds = new ArrayList<>();
    public static boolean isPassengerIdJoined(String passengerId) {

        for (String _id: _passengersIds) {

            if( _id.equalsIgnoreCase(passengerId))
                return true;

        }

        return false;
    }
    public static void addMyPassengerId(String _id) {
        _passengersIds.add(_id);
    }
    public static void clearMyPassengerIds() { _passengersIds.clear(); }

    private static final Object lockPassengers = new Object();
    private static List<User> _passengers = new ArrayList<>();
    public static List<User> getMyPassengers() {
        synchronized (lockPassengers) {
            return _passengers;
        }
    }
    public static void addMyPassenger(User passenger) {

        synchronized (lockPassengers) {

            if( passenger.wasSelfPictured() ) {// Clear the passengers that have not been pictured

                Iterator<User> iter = _passengers.iterator();

                while (iter.hasNext()) {

                    User _p = iter.next();

                    if( !_p.wasSelfPictured() ) {
                        iter.remove();

                        _passengerListAlerted = true; // redundant for each item
                    }
                }

                _passengers.add(passenger);

            } else { // Reject the passenger without a picture
                     // if there are already other passengers with pictures there
                boolean bReject = false;
                for(User p: _passengers) {
                    if( p.wasSelfPictured() ) {
                        bReject = true;
                        continue;
                    }
                }

                if( !bReject )
                    _passengers.add(passenger);

            }


        }
    }
    public static void clearMyPassengers() {
        synchronized (lockPassengers) {
            _passengers.clear();
        }
    }

    static final public int SERVER_PORT = 4545;
    static final public int SOCKET_TIMEOUT = 5000;
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String TXTRECORD_PROP_USERID = "userid";
    public static final String TXTRECORD_PROP_USERNAME = "username";
    public static final String TXTRECORD_PROP_RIDECODE = "ridecode";
    public static final String TXTRECORD_PROP_PORT = "port";
    public static final String SERVICE_INSTANCE = "_wififastride";
    public static final String SERVICE_REG_TYPE = "_presence._tcp";

    public static final String WAMS_URL = "https://fastride.azure-mobile.net/";
    public static final String WAMS_API_KEY = "omCudOMCUJgIGbOklMKYckSiGKajJU91";

    public static final String FB_USERNAME_PREF = "username";
    public static final String FB_LASTNAME_PREF = "lastUsername";
    public static final String REG_PROVIDER_PREF = "registrationProvider";

    public static final String FIRST_NAME_PREF = "firstname";
    public static final String LAST_NAME_PREF = "lastname";
    public static final String REG_ID_PREF = "regid";
    public static final String PICTURE_URL_PREF = "pictureurl";
    public static final String EMAIL_PREF = "email";
    public static final String PHONE_PREF = "phone";
    public static final String USE_PHONE_PFER = "usephone";

    public static final String FB_PROVIDER = "Facebook";
    public static final String FB_PROVIDER_FOR_STORE = "Facebook:";
    public static final String GOOGLE_PROVIDER_FOR_STORE = "Google:";
    public static final String MS_PROVIDER_FOR_STORE = "MS:";
    public static final String TWITTER_PROVIDER_FOR_STORE = "Twitter:";
    public static final String PLATFORM = "Android" + Build.VERSION.SDK_INT;

    // 'Project number' of project 'FastRide"
    // See Google Developer Console -> Billing & settings
    // https://console.developers.google.com/project/third-apex-91200/settings
    public static final String SENDER_ID = "1041824085053";

    // Names of shared preferences
    public static final String USERIDPREF = "userid";
    public static final String CARS_PREF = "cars";
    public static final String TOKENPREF = "accessToken";
    public static final String WAMSTOKENPREF = "wamsToken";
    public static final String SHOW_SELFIE_DESC = "selfieDesc";

    private static final Object lock2 = new Object();
    private static String MONITOR_STATUS;
    public static String getMonitorStatus() {
        synchronized (lock2) {
            return MONITOR_STATUS;
        }
    }
    public static void setMonitorStatus(String value) {
        synchronized (lock2) {
            MONITOR_STATUS = value;
        }
    }

    private static String _currentGeoFenceName;
    public static void set_CurrentGeoFenceName(String value){
        _currentGeoFenceName = value;
    }
    public static String get_currentGeoFenceName(){
        return _currentGeoFenceName;
    }

    // Driver/passenger 'chat' messages
    public static final int MESSAGE_READ = 0x400 + 1;
    public static final int MY_HANDLE = 0x400 + 2;
    public static final int TRACE_MESSAGE = 0x400 + 3;

    // Geofences
    public static final HashMap<String, LatLng> FWY_AREA_LANDMARKS = new HashMap<String, LatLng>();
    //    static {
//        FWY_AREA_LANDMARKS.put("GOOGLE", new LatLng(32.080341,34.780639));
//    }
    public static ArrayList<Geofence> GEOFENCES = new ArrayList<>();
    public static PendingIntent GeofencePendingIntent;

    public static final long GEOFENCE_EXPIRATION_IN_HOURS = 2;
    public static final long GEOFENCE_EXPIRATION_IN_MILLISECONDS =
            GEOFENCE_EXPIRATION_IN_HOURS * 60 * 60 * 1000;
    public static final float GEOFENCE_RADIUS_IN_METERS = 100;
    public static final int GEOFENCE_LOITERING_DELAY = 60000; // 1 min
    public static final int GEOFENCE_RESPONSIVENESS = 5000; // 5 sec

    public static Boolean DEBUG_WITHOUT_GEOFENCES = true;

    private static final Object lock = new Object();
    private static boolean inGeofenceArea;
    public static boolean isInGeofenceArea() {
        synchronized (lock) {
            if( Globals.DEBUG_WITHOUT_GEOFENCES )
                return true;
            else
                return inGeofenceArea;
        }
    }
    public static void setInGeofenceArea(boolean value) {
        synchronized (lock) {
            inGeofenceArea = value;
        }
    }

    private static final Object lock3 = new Object();
    private static boolean _REMIND_GEOFENCE_ENTRANCE;
    public static void setRemindGeofenceEntrance() {
        synchronized ( lock3 ) {
            _REMIND_GEOFENCE_ENTRANCE = true;
        }
    }
    public static Boolean getRemindGeofenceEntrance() {
        synchronized ( lock3 ) {
            return _REMIND_GEOFENCE_ENTRANCE;
        }
    }

    public static  String CASCADE_URL = "http://maximum.azurewebsites.net/data/haarcascades/haarcascade_frontalface_default.xml";
    private static String CASCADE_PATH;
    public static void initCascadePath(Context ctx) {
        String DEFAULT_CASCADE_NAME = "haarcascade_frontalface_default.xml";
        File file = new File(ctx.getFilesDir(), DEFAULT_CASCADE_NAME);
        synchronized (lock ) {
            CASCADE_PATH = file.getAbsolutePath();
        }
    }

    public static String getCascadePath (Context ctx) {
        if( CASCADE_PATH == null || CASCADE_PATH.isEmpty() )
            initCascadePath(ctx);

        return CASCADE_PATH;
    }
    public static void setCascadePath(String path) {
        synchronized (lock) {
            CASCADE_PATH = path;
        }
    }

    private static final Object lockPassengerFaces = new Object();
    private static ConcurrentHashMap<Integer, PassengerFace> _passengerFaces = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<Integer, PassengerFace> get_PassengerFaces() {
        synchronized (lockPassengerFaces) {
            return _passengerFaces;
        }
    }
    public static void set_PassengerFaces(ConcurrentHashMap<Integer, PassengerFace> faces) {
        _passengerFaces = faces;
    }
    public static void add_PassengerFace(PassengerFace pf) {
        for(int i = 0; i < Globals.REQUIRED_PASSENGERS_NUMBER; i++) {
            if( _passengerFaces.get(i) == null ) {
                _passengerFaces.put(i, pf);
                break;
            }
        }
    }
    public static PassengerFace get_PassengerFace(int at){
        synchronized (lockPassengerFaces) {
            return _passengerFaces.get(at);
        }
    }
    public static void clearPassengerFaces() {
        synchronized (lockPassengerFaces) {
            _passengerFaces.clear();
        }
    }

    // Identity matrix : ones on the main diagonal
    // and zeros elsewhere.
    public static Matrix4f verificationMat = new Matrix4f();

    public static int PASSENGER_DISCOVERY_PERIOD = 20;
    public static int PASSENGER_ADVERTISING_PERIOD = 40;
    public static int DRIVER_DISCOVERY_PERIOD = 20;

    // Parcels
    public static String PARCELABLE_KEY_RIDE_CODE = "ride_code_key";
    public static String PARCELABLE_KEY_PASSENGERS = "passengers_key";
    public static String PARCELABLE_KEY_DRIVERS = "drivers_key";
    public static String PARCELABLE_KEY_CURRENT_RIDE = "current_ride";
    public static String PARCELABLE_KEY_APPEAL_SHOWN = "appeal_shown";
    public static String PARCELABLE_KEY_CAPTURED_PASSENGERS_IDS = "captured_passengers_ids";
    public static String PARCELABLE_KEY_PASSENGERS_FACE_IDS = "face_ids";
    public static String PARCELABLE_KEY_APPEAL_PHOTO_URI = "appeal_uri";
    public static String PARCELABLE_KEY_EMOJIID = "emoji_id";

    public static String TWITTER_CONSUMER_KEY = "NJUZRWiKT5FRRq6Q7ni6BgckK";
    public static String TWITTER_CONSUMER_SECRET = "HVOOFxJgiTawiqtCtZgngc4eShFKCj1CVZjegjGEutWys6WDYP";

    public static final String storageConnectionString =
            "DefaultEndpointsProtocol=http;" +
                    "AccountName=fastride;" +
                    "AccountKey=tuyeJ4EmEuaoeGsvptgyXD0Evvsu1cTiYPAF2cwaDzcGkONdAOZ/3VEY1RHAmGXmXwwkrPN1yQmRVdchXQVgIQ==";

    public static final int FACE_VERIFY_TASK_TAG = 1;
    public static final int APPEAL_UPLOAD_TASK_TAG = 2;

    public static final int TUTORIAL_Intro      = 1;
    public static final int TUTORIAL_Driver     = 2;
    public static final int TUTORIAL_Passenger  = 3;
    public static final int TUTORIAL_Appeal     = 4;
}
