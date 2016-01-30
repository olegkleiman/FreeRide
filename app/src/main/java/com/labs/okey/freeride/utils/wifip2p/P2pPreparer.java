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

        }
    }

    //
    // Implementation of IConversation
    //
    @Override
    public boolean established() {

        try {
            if( mNetworkID != -1 )
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

    public void undo(Runnable r) {

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

    }

    public void prepare(P2pPreparerListener listener) {

        mListener = listener;

//        deletePersistentGroups();
//        setWiFiP2pChannel(4);

        prepareInternal();
    }

    private void setWiFiP2pChannel(final int channelNumber) {
        WifiP2pManager wiFiP2pManager = (WifiP2pManager) mActivity.getSystemService(Context.WIFI_P2P_SERVICE);
        WifiP2pManager.Channel channel = wiFiP2pManager.initialize(mActivity, mActivity.getMainLooper(), null);

        try {

            Method setWifiP2pChannels = wiFiP2pManager.getClass().getMethod("setWifiP2pChannels",
                                                WifiP2pManager.Channel.class,
                                                int.class,
                                                int.class,
                                                WifiP2pManager.ActionListener.class);

            setWifiP2pChannels.invoke(wiFiP2pManager, channel, 0, channelNumber, new WifiP2pManager.ActionListener(){
                @Override
                public void onSuccess() {

                    Log.d(LOG_TAG, "Changed channel (" + channelNumber + ") succeeded");
                }

                @Override
                public void onFailure(int reason) {
                    Log.d(LOG_TAG, "Changed channel (" + channelNumber + ")  failed");
                }
            });
        } catch (Exception ex) {
            Log.e(LOG_TAG, ex.getLocalizedMessage());
        }

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
