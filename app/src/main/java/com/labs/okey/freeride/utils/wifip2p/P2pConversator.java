package com.labs.okey.freeride.utils.wifip2p;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.labs.okey.freeride.model.AdvertisedRide;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.Globals;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by Oleg Kleiman on 06-Jan-16.
 */
public class P2pConversator
        implements WifiP2pManager.ChannelListener,
                    WifiP2pManager.DnsSdServiceResponseListener,
                    WifiP2pManager.DnsSdTxtRecordListener {


    private final String                LOG_TAG = "P2pHelper";

    private final Activity              mActivity;
    private final Handler               mHandler;

    private final WifiP2pManager        mWiFiP2pManager;
    private WifiP2pManager.Channel      mChannel;

    private WifiP2pDnsSdServiceInfo     mServiceInfo;
    private WifiP2pDnsSdServiceRequest  mServiceRequest;

    private final IConversation         mConversation;

    private final HashMap<String, AdvertisedRide> mBuddies = new HashMap<>();
    private IPeersChangedListener       mPeersChangedListener;

    public interface IPeersChangedListener {
        void addDeviceUser(WifiP2pDeviceUser device);
    }

    public P2pConversator(Activity activity, IConversation conv, Handler handler) {

        Assert.assertNotNull(activity);

        mActivity = activity;
        mConversation = conv;
        mHandler = handler;

        // Set-up Wi-Fi P2P Manager
        mWiFiP2pManager = (WifiP2pManager) mActivity.getSystemService(Context.WIFI_P2P_SERVICE);
        Assert.assertNotNull(mWiFiP2pManager);
    }

    public void startConversation(final Map<String, String> record,
                                  P2pConversator.IPeersChangedListener peersListener) {

        mPeersChangedListener = peersListener;

        mChannel = mWiFiP2pManager.initialize(mActivity, mActivity.getMainLooper(), this);
        if (mChannel != null) {

            registerDnsSdService(record);

//            mWiFiP2pManager.clearLocalServices(mChannel,
//                    new WifiP2pManager.ActionListener() {
//                        @Override
//                        public void onSuccess() {
//                            String msg = "Local Service cleared";
//                            Log.i(LOG_TAG, msg);
//
//                            registerDnsSdService(record);
//                        }
//
//                        @Override
//                        public void onFailure(int reason) {
//                            String msg = "Failed to clear Local Service. Error: " +
//                                    failureReasonToString(reason);
//                            Log.e(LOG_TAG, msg);
//
//                            Message message = Message.obtain(null, 111);
//                            if (mHandler != null) {
//
//                                Bundle data = new Bundle();
//                                data.putString("error", msg);
//                                message.setData(data);
//                                mHandler.sendMessage(message);
//                            }
//                        }
//                    });
        }

    }

    private void registerDnsSdService(Map<String, String> record){
        // Service information for Bonjour.
        // Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                Globals.SERVICE_INSTANCE,
                Globals.SERVICE_REG_TYPE,
                record);

        if( mServiceInfo != null ) {

            Log.d(LOG_TAG, "ServiceInfo created");

            mWiFiP2pManager.addLocalService(mChannel, mServiceInfo,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            String msg = "LocalService added";
                            Log.i(LOG_TAG, msg);

                            discoverService();
                        }

                        @Override
                        public void onFailure(int reason) {

                            String msg = "Failed to add LocalService. Error: "
                                            + failureReasonToString(reason);
                            Log.e(LOG_TAG, msg);

                            Message message = Message.obtain(null, 111);
                            if( mHandler != null ) {

                                Bundle data = new Bundle();
                                data.putString("error", msg);
                                message.setData(data);
                                mHandler.sendMessage(message);
                            }
                        }
                    });
        } else {
            String msg = "Failed to create ServiceInfo";
            Log.e(LOG_TAG, msg);
        }
    }

    private void discoverService(){
        mWiFiP2pManager.setDnsSdResponseListeners(mChannel,
                this,
                this);

        // After attaching listeners, create a new service request and initiate
        // discovery.
        mServiceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        mWiFiP2pManager.addServiceRequest(mChannel, mServiceRequest,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                         Log.i(LOG_TAG, "ServiceRequest added");
                    }

                    @Override
                    public void onFailure(int reason) {

                        String msg = "Failed to add ServiceRequest.Error: "
                                        + failureReasonToString(reason);

                        Log.e(LOG_TAG, msg);

                        if( mHandler != null ) {
                            Message message = Message.obtain(null, 111);
                            Bundle data = new Bundle();
                            data.putString("error", msg);
                            message.setData(data);
                            mHandler.sendMessage(message);
                        }
                    }
                });


        mWiFiP2pManager.discoverServices(mChannel,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.i(LOG_TAG, "Discovery started.");
                    }

                    @Override
                    public void onFailure(int reason) {

                        String msg = "Discovery failed. Error: "
                                + failureReasonToString(reason);
                        Log.e(LOG_TAG, msg);

                        stopDiscovery();

                        if (mHandler != null) {
                            Message message = Message.obtain(null, Globals.MESSAGE_DISCOVERY_FAILED);
                            Bundle data = new Bundle();
                            data.putString("error", msg);
                            message.setData(data);
                            mHandler.sendMessage(message);
                        }

                    }
                });
    }

    public void stopConversation(){
        stopDiscovery();
    }

    private void stopDiscovery(){

        if( mServiceRequest != null ) {

            mWiFiP2pManager.clearServiceRequests(mChannel,
                    new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            String msg = "ServiceRequest cleared";
                            Log.d(LOG_TAG, msg);

                            clearLocalServices();
                        }

                        @Override
                        public void onFailure(int reason) {

                            String msg = "Failed to clear ServiceRequest. Error: "
                                    + failureReasonToString(reason);
                            Log.e(LOG_TAG, msg);

                            if (mHandler != null) {
                                Message message = Message.obtain(null, 111);
                                Bundle data = new Bundle();
                                data.putString("error", msg);
                                message.setData(data);
                                mHandler.sendMessage(message);
                            }
                        }

                    });
        }

        if( mConversation != null )
            mConversation.established();

    }

    private void clearLocalServices() {
        mWiFiP2pManager.clearLocalServices(mChannel,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {
                        String msg = "LocalService removed";
                        Log.d(LOG_TAG, msg);

                    }

                    @Override
                    public void onFailure(int reason) {

                        String msg = "Failed to remove LocalService. Error: "
                                + failureReasonToString(reason);
                        Log.e(LOG_TAG, msg);

                        if (mHandler != null) {

                            Message message = Message.obtain(null, 111);
                            Bundle data = new Bundle();
                            data.putString("error", msg);
                            message.setData(data);
                            mHandler.sendMessage(message);
                        }

                    }

                });
    }

    //
    // Implementation of WifiP2pManager.DnsSdServiceResponseListener
    //
    @Override
    public void onDnsSdServiceAvailable(String instanceName,
                                        String registrationType,
                                        WifiP2pDevice wifiP2pDevice) {

        // A service has been discovered. Is this our app?
        if (instanceName.equalsIgnoreCase(Globals.SERVICE_INSTANCE)) {

            String msg = "DnsSdService Available:";
            Log.i(LOG_TAG, msg);

            msg = "Instance name: " + instanceName;
            Log.i(LOG_TAG, msg);

            msg = "Reg.Type: " + registrationType;
            Log.i(LOG_TAG, msg);

            msg = "Device name: " + wifiP2pDevice.deviceName;
            Log.i(LOG_TAG, msg);

            if( mConversation != null ) {
                boolean bReconnected = mConversation.established();
                Log.i(LOG_TAG, "Reconnected: " + bReconnected);
            }

            AdvertisedRide advRide = mBuddies.get(wifiP2pDevice.deviceName);
            if( advRide != null) {
                WifiP2pDeviceUser deviceUser =
                        new WifiP2pDeviceUser(wifiP2pDevice);

                deviceUser.setUserName(advRide.getUserName());
                deviceUser.setUserId(advRide.getUserId());
                deviceUser.setRideCode(advRide.getRideCode());

                if( mPeersChangedListener!= null )
                    mPeersChangedListener.addDeviceUser(deviceUser);

                mBuddies.remove(advRide);
            }

        } else {

            String msg = "OTHER Service: " + instanceName;
            Log.e(LOG_TAG, msg);

            if( mConversation != null )
                mConversation.established();

        }


    }

    //
    // Implementation of WifiP2pManager.DnsSdTxtRecordListener
    //
    // Called before onDnsSdServiceAvailable() !!!
    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName,
                                          Map<String, String> record,
                                          WifiP2pDevice wifiP2pDevice) {

        String rideCode = record.get(Globals.TXTRECORD_PROP_RIDECODE);
        String userId = record.get(Globals.TXTRECORD_PROP_USERID);

        String msg = "DNS-SD TXT Record:";
        Log.i(LOG_TAG, msg);

        msg = "\t" + fullDomainName;
        Log.i(LOG_TAG, msg);

        msg = "\t Device Name: " + wifiP2pDevice.deviceName;
        Log.i(LOG_TAG, msg);

        msg = "\tUserID: " + userId;
        Log.i(LOG_TAG, msg);


        if (mHandler != null ) {
            Message message = Message.obtain(null, 112);
            Bundle data = new Bundle();
            data.putString(Globals.TXTRECORD_PROP_RIDECODE, rideCode);
            message.setData(data);
            mHandler.sendMessage(message);
        }

        AdvertisedRide advRide = new AdvertisedRide(userId, "No name", rideCode);
        mBuddies.put(wifiP2pDevice.deviceName, advRide);
     }


    //
    // Implementation of WifiP2pManager.ChannelListener
    //

    @Override
    public void onChannelDisconnected() {
        Log.i(LOG_TAG, "Channel disconnected");
    }

    private String failureReasonToString(int reason) {

        // Failure reason codes:
        // 0 - internal error
        // 1 - P2P unsupported
        // 2- busy

        switch ( reason ){
            case WifiP2pManager.ERROR:
                return "(Internal) Error";

            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P unsupported";

            case WifiP2pManager.BUSY:
                return "Busy";

            case WifiP2pManager.NO_SERVICE_REQUESTS:
                return "No service requests";

            default:
                return "Unknown";
        }
    }
}
