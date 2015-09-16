package com.labs.okey.freeride.utils;

import android.app.Activity;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.Log;

import com.labs.okey.freeride.WiFiDirectBroadcastReceiver;
import com.labs.okey.freeride.model.AdvertisedRide;
import com.labs.okey.freeride.model.WifiP2pDeviceUser;
import com.labs.okey.freeride.utils.wifip2p.TaggedActionListener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Oleg Kleiman on 26-Apr-15.
 */
public class WiFiUtil
    implements WifiP2pManager.DnsSdServiceResponseListener,
               WifiP2pManager.DnsSdTxtRecordListener,
               WifiP2pManager.GroupInfoListener{

    private static final String LOG_TAG = "FR.WiFiUtil";

    private Context mContext;

    private final IntentFilter intentFilter = new IntentFilter();
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    WifiP2pDnsSdServiceRequest mServiceRequest;
    WifiP2pDnsSdServiceInfo mServiceInfo;

    WiFiDirectBroadcastReceiver mReceiver;

    IPeersChangedListener mPeersChangedListener;
    final HashMap<String, AdvertisedRide> mBuddies = new HashMap<>();

    // Stored only for recovery
    String mUserId;
    String mUserName;
    String mRideCode;

    public interface IPeersChangedListener {
        public void add(WifiP2pDeviceUser device);
    }

    public WiFiUtil(Context context) {

        mContext = context;

        //  Indicates a change in the Wi-Fi P2P status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);

        // Indicates the state of Wi-Fi P2P connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mManager = (WifiP2pManager)context.getSystemService(context.WIFI_P2P_SERVICE);
        if( mManager != null ) {
            mChannel = mManager.initialize(context, context.getMainLooper(), null);
        }
    }

    public WifiP2pManager getWiFiP2pManager() {
        return mManager;
    }

    public WifiP2pManager.Channel getWiFiP2pChannel() {
        return mChannel;
    }

    public void registerReceiver(Activity listener) {
        /** register the BroadcastReceiver with the intent values to be matched */
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, listener);
        mContext.registerReceiver(mReceiver, intentFilter);

        Log.d(LOG_TAG, "Receiver registered");
    }

    public void unregisterReceiver(){
        mContext.unregisterReceiver(mReceiver);

        Log.d(LOG_TAG, "Receiver un-registered");
    }

    public void deletePersistentGroups() {
        try {
            Method[] methods = WifiP2pManager.class.getMethods();
            for (int i = 0; i < methods.length; i++) {
                if (methods[i].getName().equals("deletePersistentGroup")) {
                    // Delete any persistent group
                    for (int netid = 0; netid < 32; netid++) {
                        methods[i].invoke(mManager, mChannel, netid, null);
                    }
                }
            }

        }catch(Exception e) {
            Log.e(LOG_TAG, e.getMessage());
        }
    }

    public void discoverPeers() {
        mManager.discoverPeers(mChannel,
                new TaggedActionListener((ITrace) mContext, "discover peers request"));
    }

    interface IContinuation{
        void exec();
    }



    /**
     * Registers a local service and then initiates a service discovery
     */
    public void startRegistrationAndDiscovery(final IPeersChangedListener peersChangedListener,
                                              final String userId,
                                              final String userName,
                                              final String rideCode,
                                              final Handler handler,
                                              final int delayMills) {

        // Duplicate for recovery, i.e. for restarting after WiFi reset.
        // Reset is done within discoverService() when WiFiP2pManager.discoverServices() failed.
        mUserId = userId;
        mUserName = userName;
        mRideCode = rideCode;

        mPeersChangedListener = peersChangedListener;

        mManager.clearLocalServices(mChannel,
                new WifiP2pManager.ActionListener() {

                    @Override
                    public void onSuccess() {

                        registerDnsSdService(userId,
                                            userName,
                                            rideCode,
                                            new IContinuation(){
                                                @Override
                                                public void exec() {
                                                    discoverService(peersChangedListener, handler, delayMills);
                                                }
                                            });

                    }

                    @Override
                    public void onFailure(int errCode) {
                        Log.e(LOG_TAG, "Failed to clear previous registration of local service");
                    }
                });

    }

    public void registerDnsSdService(String userId,
                                     String userName,
                                     String rideCode,
                                     final IContinuation continuation) {

        // Put the records to map with extreme care:
        // the size of the buffer that holds these fields varies in different OSs.
        // Generally it is about 200 bytes, but may be smaller.
        // (see here: https://code.google.com/p/android/issues/detail?id=59858 )
        Map<String, String> record = new HashMap<>();
        //record.put(Globals.TXTRECORD_PROP_AVAILABLE, "visible");
        record.put(Globals.TXTRECORD_PROP_USERID, userId);
        record.put(Globals.TXTRECORD_PROP_USERNAME, userName);
        record.put(Globals.TXTRECORD_PROP_RIDECODE, rideCode);
        record.put(Globals.TXTRECORD_PROP_PORT, Integer.toString(Globals.SERVER_PORT));

        // Service information for Bonjour.
        // Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                Globals.SERVICE_INSTANCE,
                Globals.SERVICE_REG_TYPE,
                record);

        mManager.addLocalService(mChannel, mServiceInfo,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        String message = "addLocalService succeeded";
                        Log.d(LOG_TAG, message);

                        continuation.exec();
                    }

                    @Override
                    public void onFailure(int reason) {
                        String message = "addLocalService failed. Reason: " + failureReasonToString(reason);
                        Log.d(LOG_TAG, message);
                        ((ITrace) mContext).trace(message);

                    }
                });

    }

    public void discoverService(final IPeersChangedListener peersChangedListener,
                                final Handler handler,
                                final int delayMills) {

        final HashMap<String, AdvertisedRide> buddies = new HashMap<>();

          /*
         * Register listeners for DNS-SD services. These are callbacks invoked
         * by the system when a service is actually discovered.
         */

        mManager.setDnsSdResponseListeners(mChannel,
                this,
                this);

        // After attaching listeners, create a new service request and initiate
        // discovery.
        mServiceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        mManager.addServiceRequest(mChannel, mServiceRequest,
                new TaggedActionListener((ITrace) mContext, "addServiceRequest"));

        if( handler != null ) {

            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mManager.discoverServices(mChannel,
                            new WifiP2pManager.ActionListener(){

                                @Override
                                public void onSuccess() {
                                    ((ITrace)mContext).alert("restored", "");
                                }

                                @Override
                                public void onFailure(int reason) {
                                    stopDiscovery();

                                    ((ITrace)mContext).alert("discoverServices() failed with error code: " + Integer.toString(reason), "error");

                                    WifiManager wifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
                                    wifiManager.setWifiEnabled(false);
                                    wifiManager.setWifiEnabled(true);

                                    startRegistrationAndDiscovery(mPeersChangedListener, mUserId,
                                            mUserName, mRideCode,
                                            handler, delayMills);
                                }
                            });

                }
            }, delayMills);

        }

    }

    @Override
    public void onDnsSdServiceAvailable(String instanceName,
                                        String registrationType,
                                        WifiP2pDevice device) {
        Log.d(LOG_TAG, "onDnsSdServiceAvailable() called");

        // A service has been discovered. Is this our app?
        if (instanceName.equalsIgnoreCase(Globals.SERVICE_INSTANCE)) {
            String traceMessage = "DNS-SD SRV Record: " + instanceName;
            ((ITrace) mContext).trace(traceMessage);
            Log.d(LOG_TAG, traceMessage);

            if (mPeersChangedListener != null) {

                AdvertisedRide advRide = mBuddies.get(device.deviceName);
                if( advRide != null ) {

                    WifiP2pDeviceUser deviceUser =
                            new WifiP2pDeviceUser(device);

                    deviceUser.setUserName(advRide.getUserName());
                    deviceUser.setUserId(advRide.getUserId());
                    deviceUser.setRideCode(advRide.getRideCode());

                    mPeersChangedListener.add(deviceUser);
                }
            }
        } else {
            Log.d(LOG_TAG, "Other DNS_SD service discovered: "
                    + instanceName);
        }
    }

    @Override
    public void onDnsSdTxtRecordAvailable(String fullDomainName,
                                          Map<String, String> record,
                                          WifiP2pDevice device) {
        Log.d(LOG_TAG, "onDnsSdTxtRecordAvailable() called");

        String traceMessage = "DNS-SD TXT Records: " + device.deviceName;
        String userId = record.get(Globals.TXTRECORD_PROP_USERID);
        traceMessage += "\nUser Id: " + userId;
        String userName = record.get(Globals.TXTRECORD_PROP_USERNAME);
        traceMessage += "\nUser Id: " + userName;
        String rideCode = record.get(Globals.TXTRECORD_PROP_RIDECODE);
        traceMessage += "\nRide Code: " + rideCode;
        ((ITrace) mContext).trace(traceMessage);
        Log.d(LOG_TAG, traceMessage);

        AdvertisedRide advRide = new AdvertisedRide(userId, userName, rideCode);
        mBuddies.put(device.deviceName, advRide);
    }


    public void stopDiscovery(){
        if( mServiceRequest != null ) {
            mManager.removeLocalService(mChannel, mServiceInfo,
                    new TaggedActionListener((ITrace) mContext,
                            "remove local service"));
            mManager.clearServiceRequests(mChannel,
                    new TaggedActionListener((ITrace) mContext,
                            "clear service requests"));
        }
    }

    public void removeGroup() {
        mManager.requestGroupInfo(mChannel, this);
    }

    @Override
    public void onGroupInfoAvailable(WifiP2pGroup group) {
        if( group != null ) {
            Log.d(LOG_TAG, "Group detected. Trying to remove");

            mManager.removeGroup(mChannel,
                    new TaggedActionListener((ITrace) mContext, "remove group request"));
        }
    }

    public void disconnect(){
        mManager.cancelConnect(mChannel,
                new TaggedActionListener((ITrace) mContext, "cancel connect request"));
    }

    public void connectToDevice(WifiP2pDevice device, int delay){

        stopDiscovery();

        final WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        //config.groupOwnerIntent = 15;
        config.wps.setup = WpsInfo.PBC;

        if( delay == 0) {
            ITrace tracer = (ITrace)mContext;
            mManager.connect(mChannel, config,
                    new TaggedActionListener(tracer, "Connected request"));
        } else {

            Handler h = new Handler();

            Runnable r = new Runnable() {
                @Override
                public void run() {
                    mManager.connect(mChannel, config,
                            new TaggedActionListener((ITrace)mContext, "Connected request"));
                }
            };

            h.postDelayed(r, delay); // with delay
        }
    }

    public void requestPeers(WifiP2pManager.PeerListListener listener){
        if( mManager != null && mChannel != null) {
            mManager.requestPeers(mChannel, listener);
        }
    }

    public void requestGroupInfo(WifiP2pManager.GroupInfoListener listener) {
        if( mManager != null && mChannel != null )
            mManager.requestGroupInfo(mChannel, listener);
    }

    private String failureReasonToString(int reason) {

        // Failure reason codes:
        // 0 - internal error
        // 1 - P2P unsupported
        // 2- busy

        switch ( reason ){
            case 0:
                return "Internal Error";

            case 1:
                return "P2P unsupported";

            case 2:
                return "Busy";

            default:
                return "Unknown";
        }
    }

    public static class ClientAsyncTask extends AsyncTask<Void, Void, String> {

        Context mContext;
        String mMessage;
        InetAddress mGroupHostAddress;

        public ClientAsyncTask(Context context,
                               InetAddress groupOwnerAddress,
                               String message){
            this.mContext = context;
            this.mGroupHostAddress = groupOwnerAddress;
            this.mMessage = message;
        }

        @Override
        protected String doInBackground(Void... voids) {

            Log.d(LOG_TAG, "ClientAsyncTask:doBackground() called");

            Socket socket = new Socket();
            String traceMessage = "Client socket created";
            Log.d(LOG_TAG, traceMessage);
            ((ITrace)mContext).trace(traceMessage);

            try {

                // binds this socket to the local address.
                // Because the parameter is null, this socket will  be bound
                // to an available local address and free port
                socket.bind(null);
                InetAddress localAddress = socket.getLocalAddress();

                traceMessage = String.format("Local socket. Address: %s. Port: %d",
                        localAddress.getHostAddress(),
                        socket.getLocalPort());
                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

                traceMessage = String.format("Server socket. Address: %s. Port: %d",
                        mGroupHostAddress.getHostAddress(),
                        Globals.SERVER_PORT);
                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

                socket.connect(
                        new InetSocketAddress(mGroupHostAddress.getHostAddress(),
                                              Globals.SERVER_PORT),
                        Globals.SOCKET_TIMEOUT);

                traceMessage = "Client socket connected";
                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

                OutputStream os = socket.getOutputStream();
                os.write(mMessage.getBytes());

                os.close();

                traceMessage = "!!! MESSAGE WRITTEN.\noutput closed";
                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

            } catch (Exception ex) {
                ex.printStackTrace();
                traceMessage = ex.getMessage();
                Log.e(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);
            } finally {
                try {
                    socket.close();
                    traceMessage = "client socket closed";
                    Log.d(LOG_TAG, traceMessage);
                    ((ITrace)mContext).trace(traceMessage);
                } catch(Exception e) {
                    traceMessage = e.getMessage();
                    Log.e(LOG_TAG, traceMessage);
                    ((ITrace)mContext).trace(traceMessage);
                }
            }

            return null;
        }
    }

    public static class ServerAsyncTask extends AsyncTask<Void, Void, String> {

        Context mContext;

        public ServerAsyncTask(Context context){
            mContext = context;
        }

        @Override
        protected String doInBackground(Void... voids) {

            Log.d(LOG_TAG, "ServerAsyncTask:doBackground() called");

            String traceMessage = "Server: Socket opened on port " + Globals.SERVER_PORT;
            try {
                ServerSocket serverSocket = new ServerSocket(Globals.SERVER_PORT);

                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

                Socket clientSocket = serverSocket.accept();

                BufferedReader reader =
                        new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                traceMessage = reader.readLine();
                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

                clientSocket.close();
                traceMessage = "Client socket closed";
                Log.d(LOG_TAG, traceMessage);

                serverSocket.close();
                traceMessage = "Server socket closed";
                Log.d(LOG_TAG, traceMessage);
                ((ITrace)mContext).trace(traceMessage);

            } catch (IOException e) {
                Log.e(LOG_TAG, e.getMessage());
                ((ITrace)mContext).trace(traceMessage);
            }

            return null;
        }
    }
}
