package com.labs.okey.freeride;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.test.InstrumentationTestCase;
import android.util.Pair;

import com.labs.okey.freeride.model.Ride;
import com.labs.okey.freeride.utils.Globals;
import com.microsoft.windowsazure.mobileservices.MobileServiceClient;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceAuthenticationProvider;
import com.microsoft.windowsazure.mobileservices.authentication.MobileServiceUser;
import com.microsoft.windowsazure.mobileservices.table.MobileServiceTable;

import junit.framework.Assert;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Created by Oleg on 20-Dec-15.
 */
public class MobileServiceTableTests extends InstrumentationTestCase {

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testNewMobileServiceTableShouldReturnMobileServiceTable() throws MalformedURLException {
        String tableName = "rides";
        MobileServiceClient client = new MobileServiceClient(Globals.WAMS_URL,
                Globals.WAMS_API_KEY,
                getInstrumentation().getTargetContext());
        MobileServiceTable<Ride> msTable = new MobileServiceTable<Ride>(tableName, client, Ride.class);

        assertEquals(tableName, msTable.getTableName());
    }

    public void testInsertNewRide() throws Throwable {

        final String tableName = "rides";
        MobileServiceClient wamsClient = null;
        try {
            wamsClient = new MobileServiceClient(Globals.WAMS_URL,
                    Globals.WAMS_API_KEY,
                    getInstrumentation().getTargetContext());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        final Ride ride = new Ride();
        int min = 100000;
        int max = 1000000;


        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext());
        final String userID = sharedPrefs.getString(Globals.USERIDPREF, "");
        final String token = sharedPrefs.getString(Globals.WAMSTOKENPREF, "");


//
//            wamsClient = wamsClient.withFilter(new ServiceFilter() {
//                @Override
//                public ListenableFuture<ServiceFilterResponse> handleRequest(ServiceFilterRequest request,
//                                                                             NextServiceFilterCallback nextServiceFilterCallback) {
//
//                    final SettableFuture<ServiceFilterResponse> resultFuture = SettableFuture.create();
//                    Ride ride = new Ride();
//                    ride.setRideCode("12345");
//                    //resultFuture.set(ride);
//                    return nextServiceFilterCallback.onNext(ride);
//                }
//
//                }
//            );


        Random r = new Random();
        int rideCode = r.nextInt(max - min + 1) + min;
        final String _rideCode = Integer.toString(rideCode);
        ride.setRideCode(_rideCode);
        ride.setCarNumber("1199988");
        ride.setGFenceName("Sample");

            List<Pair<String, String>> parameters = new ArrayList<>();
            parameters.add(new Pair<>("rideCodeGenerated", "true"));

            try {

                MobileServiceUser wamsUser = new MobileServiceUser(userID);
                wamsUser.setAuthenticationToken(token);
                wamsClient.setCurrentUser(wamsUser);

                MobileServiceTable<Ride> msTable = wamsClient.getTable(tableName, Ride.class);

                //Ride currentRide = msTable.insert(ride).get();
                Ride currentRide = msTable.insert(ride, parameters).get();
                Assert.assertNotNull(currentRide);
                //Assert.assertEquals(_rideCode, currentRide.getRideCode());
            } catch (Exception e)  {
                e.printStackTrace();
            }

    }

    private MobileServiceAuthenticationProvider getTokenProvider() {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getInstrumentation().getTargetContext());
        String accessTokenProvider = sharedPrefs.getString(Globals.REG_PROVIDER_PREF, "");

        if( accessTokenProvider.equals(Globals.FB_PROVIDER))
            return MobileServiceAuthenticationProvider.Facebook;
        else if( accessTokenProvider.equals(Globals.TWITTER_PROVIDER))
            return MobileServiceAuthenticationProvider.Twitter;
        else
            return null;
    }
}
