package com.labs.okey.freeride.utils.wifip2p;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import junit.framework.Assert;

import java.lang.reflect.Method;

/**
 * Created by Oleg Kleiman on 06-Jan-16.
 */
public class P2pPreparer implements IConversation {

    public interface P2pPreparerListener {
        void prepared();
        void interrupted();

    }

    public class P2pPreparerReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context c, Intent intent) {

//            NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
//            Log.i(LOG_TAG, "==>1: " + info);

            ConnectivityManager cm = (ConnectivityManager) mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info2 =  cm.getActiveNetworkInfo();
            Log.i(LOG_TAG, "==>2: " + info2);

            if( info2 != null ) {
                if( info2.isConnected() ) {

                    Log.i(LOG_TAG, "==>Info2 connected");

                    if (mRestoreRunnable != null) {

                        mActivity.unregisterReceiver(mBroadcastReceiver);

                        mRestoreRunnable.run();
                    }
                }
            } else {
                if( intent.getAction().equals(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION) ) {
                    SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
                    Log.i(LOG_TAG, "" + state);
                }

            }


//            if (intent.hasExtra(WifiManager.EXTRA_NEW_STATE)) {
//                SupplicantState state = intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE);
//                Log.i(LOG_TAG, "" + state);
//
////                NetworkInfo.DetailedState detailedState = WifiInfo.getDetailedStateOf(state);
////                Log.i(LOG_TAG, "" + detailedState);
//
//                WifiInfo wifiInfo = mWiFiManager.getConnectionInfo();
//                wifiInfo.
//
//                if( state == SupplicantState.COMPLETED) {
//                    if( mRestoreRunnable != null ) {
//
//                        //mActivity.unregisterReceiver(mBroadcastReceiver);
//
//                        mRestoreRunnable.run();
//                    }
//                }
////                NetworkInfo.DetailedState detailedState = WifiInfo.getDetailedStateOf(state);
////                if( state == SupplicantState.COMPLETED ) {
////                    Log.i(LOG_TAG, "Supplicant completed");
////                    prepareInternal();
////                }
//            }
        }
    }

    //
    // Implementation of IConversation
    //
    @Override
    public boolean established() {

        try {
            mActivity.unregisterReceiver(mBroadcastReceiver);
        } catch(Exception ex) {
            Log.e(LOG_TAG, ex.getLocalizedMessage());
        }

        if( mNetworkID != -1
            && mWiFiManager.enableNetwork(mNetworkID, true) ) {

            boolean bReconnected = mWiFiManager.reconnect();
            return bReconnected;// && mWiFiManager.reassociate();
        }

        return false;
    }

    public void restore(Runnable r) {

        if( mNetworkID != -1 ) {

            mRestoreRunnable = r;

            mWiFiManager.enableNetwork(mNetworkID, true);
            mWiFiManager.reconnect();
        } else
            r.run();
    }

    private final String                LOG_TAG = "FR.P2pPreparer";

    private P2pPreparerListener         mListener;
    private final Activity              mActivity;
    private final WifiManager           mWiFiManager;
    private final BroadcastReceiver     mBroadcastReceiver;
    private int                         mNetworkID;

    private Runnable                    mRestoreRunnable;


    public P2pPreparer(Activity activity) {

        Assert.assertNotNull(activity);

        // Set-up Wi-Fi Manager
        mActivity = activity;
        mWiFiManager = (WifiManager) mActivity.getSystemService(Context.WIFI_SERVICE);
        Assert.assertNotNull(mWiFiManager);
        mBroadcastReceiver = new P2pPreparerReceiver();
    }

    private void prepareInternal() {

        // Disconnect from currently active WiFi network
        // and remember its networkID (as known to wpa_supplicant)
        // to re-connect on subsequent IConversation.terminated() call.

        WifiInfo wifiInfo =  mWiFiManager.getConnectionInfo();
        mNetworkID = wifiInfo.getNetworkId();
        if( mNetworkID != -1 ) {
            mWiFiManager.disableNetwork(mNetworkID); // analogue of 'forget' network

            IntentFilter _if = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            //_if.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
            mActivity.registerReceiver(mBroadcastReceiver, _if);
        }

        if( mListener != null)
            mListener.prepared();

//
//        deletePersistentGroups();
//
//        WifiInfo wifiInfo =  mWiFiManager.getConnectionInfo();
//        SupplicantState supplicantState = wifiInfo.getSupplicantState();
//
//        mWiFiManager.disconnect(); // disassociate from the currently active access point
//
//        if( mLogger != null )
//            mLogger.i("Disconnected from " + wifiInfo.getSSID());
//
//        mNetworkID = wifiInfo.getNetworkId();
//        mWiFiManager.disableNetwork(mNetworkID); // analogue to 'forget' network:
//
////        if( supplicantState == SupplicantState.COMPLETED) {
////
////            mWiFiManager.disconnect(); // disassociate from the currently active access point
////
////            if( mLogger != null )
////                mLogger.i("Disconnected from " + wifiInfo.getSSID());
////
////            mNetworkID = wifiInfo.getNetworkId();
////            mWiFiManager.disableNetwork(mNetworkID); // analogue to 'forget' network:
////            // Prevents the wpa_supplicant's attempts to
////            // associate this network for further
////        } else {
////            if( mLogger != null ) {
////                int nRssi = wifiInfo.getRssi();
////                mLogger.i("Active Wi-Fi Network: " + wifiInfo.toString());
////            }
////        }
//
//        if( mListener != null)
//            mListener.prepared();
    }

    public void prepare(P2pPreparerListener listener) {

        mListener = listener;

        prepareInternal();

//        mListener = listener;
//
//        ConnectivityManager connManager = (ConnectivityManager)
//                mActivity.getSystemService(Context.CONNECTIVITY_SERVICE);
//        NetworkInfo info = connManager.getActiveNetworkInfo();
//
//        if( info != null
//            && info.getType() == ConnectivityManager.TYPE_MOBILE
//            && info.isConnected() ) {
//
//            // if connected through mobile, just make it sure Wi-Fi is turned on
//            if( !mWiFiManager.isWifiEnabled() ) {
//                mWiFiManager.setWifiEnabled(true); // prepareInternal() be called on Intent with
//                                                   // COMPLETED supplicant state
//
//                mActivity.registerReceiver(mBroadcastReceiver,
//                        new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
//
//            } else {
//               if (mListener != null)
//                    mListener.prepared();
//            }
//            return;
//        }
//
//        // Connected through WiFi
//        if( mWiFiManager.isWifiEnabled() ) {
//            prepareInternal();
//        } else {
//            mWiFiManager.setWifiEnabled(true); // prepareInternal() be called on Intent with
//                                               // COMPLETED supplicant state
//
//            mActivity.registerReceiver(mBroadcastReceiver,
//                    new IntentFilter(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION));
//
//        }
    }

    private void deletePersistentGroups() {

        WifiP2pManager wiFiP2pManager = (WifiP2pManager) mActivity.getSystemService(Context.WIFI_P2P_SERVICE);
        WifiP2pManager.Channel mChannel = wiFiP2pManager.initialize(mActivity, mActivity.getMainLooper(), null);

        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Delete any persistent group
                    for (int netid = 0; netid < 32; netid++) {
                        methods[i].invoke(wiFiP2pManager, mChannel, netid, null);
                    }
                }
            }

        }catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }


}
