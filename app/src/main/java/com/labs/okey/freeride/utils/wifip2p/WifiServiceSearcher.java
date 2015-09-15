package com.labs.okey.freeride.utils.wifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceRequest;
import android.os.CountDownTimer;
import android.os.Handler;
import android.telephony.ServiceState;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static android.net.wifi.p2p.WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION;

/**
 * Created by Oleg on 14-Sep-15.
 */
public class WifiServiceSearcher {

    private static final String LOG_TAG = "FR.WiFi.Searcher";

    private Context context;
    private BroadcastReceiver receiver;
    private IntentFilter filter;
    private String SERVICE_TYPE;

    private final WifiBase.WifiStatusCallBack callback;
    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;
    private WifiP2pManager.UpnpServiceResponseListener serviceListener;
    private WifiP2pManager.PeerListListener peerListListener;

    enum ServiceState{
        NONE,
        //    WaitingTimer,
        DiscoverPeer,
        DiscoverService
    }
    ServiceState myServiceState = ServiceState.NONE;

    List<ServiceItem> myServiceList = new ArrayList<ServiceItem>();

    CountDownTimer ServiceDiscoveryTimeOutTimer = new CountDownTimer(60000, 1000) {
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            stopDiscovery();
            startPeerDiscovery();
        }
    };

    CountDownTimer peerDiscoveryTimer = null;

    public WifiServiceSearcher(Context Context,
                               WifiP2pManager Manager,
                               WifiP2pManager.Channel Channel,
                               WifiBase.WifiStatusCallBack handler,
                               String serviceType) {
        this.context = Context;
        this.p2p = Manager;
        this.channel = Channel;
        this.callback = handler;
        this.SERVICE_TYPE = serviceType;

        Random ran = new Random(System.currentTimeMillis());
        // if this 4 seconds minimum, then we see this
        // triggering before we got all services
        long millisInFuture = 5000 + (ran.nextInt(5000));

        peerDiscoveryTimer = new CountDownTimer(millisInFuture, 1000) {
            public void onTick(long millisUntilFinished) {
                // not using
            }
            public void onFinish() {
                myServiceState = ServiceState.NONE;
                if(callback != null) {
                    callback.gotServicesList(myServiceList);

                    myServiceState = ServiceState.DiscoverPeer;
                    //cancel all other counters, and start our wait cycle
                    ServiceDiscoveryTimeOutTimer.cancel();
                    peerDiscoveryTimer.cancel();
                    stopDiscovery();
                    startPeerDiscovery();
                    //  stopPeerDiscovery();
                    // WaitTimeTimeOutTimer.start();
                }else{
                    startPeerDiscovery();
                }
            }
        };
    }

    public void start() {
        receiver = new ServiceSearcherReceiver();

        filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        filter.addAction(WIFI_P2P_PEERS_CHANGED_ACTION);

        this.context.registerReceiver(receiver, filter);

        peerListListener = new WifiP2pManager.PeerListListener() {

            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                if (peers.getDeviceList().size() > 0) {
                    if(myServiceState != ServiceState.DiscoverService){
                        if(callback != null) {
                            callback.gotPeersList(peers.getDeviceList());
                        }

                        //tests have shown that if we have multiple peers with services advertising
                        // who disappear same time when we do this, there is a chance that we get stuck
                        // thus, if this happens, in 60 seconds we'll cancel this query and start peer discovery again
                        ServiceDiscoveryTimeOutTimer.start();
                        startServiceDiscovery();
                    }
                }
            }
        };

        serviceListener = new WifiP2pManager.UpnpServiceResponseListener(){

            @Override
            public void onUpnpServiceAvailable(List<String> uniqueServiceNames, WifiP2pDevice srcDevice) {
                if(uniqueServiceNames != null && srcDevice != null) {
                    Log.i(LOG_TAG, "Found Service in device, :" + srcDevice.deviceName + ", with " + uniqueServiceNames.size() + " services");
                }

                //incase we are called after we have started the wait cycle
                //    if(myServiceState != ServiceState.WaitingTimer) {
                ServiceDiscoveryTimeOutTimer.cancel();

                peerDiscoveryTimer.cancel();
                peerDiscoveryTimer.start();
            }};

        p2p.setUpnpServiceResponseListener(channel, serviceListener);
        startPeerDiscovery();
    }

    public void stop() {
        this.context.unregisterReceiver(receiver);

        ServiceDiscoveryTimeOutTimer.cancel();
        peerDiscoveryTimer.cancel();
        //     PeerDiscoveryTimeOutTimer.cancel();
        stopDiscovery();
        stopPeerDiscovery();
    }

    private void startServiceDiscovery() {
        myServiceState = ServiceState.DiscoverService;

        WifiP2pUpnpServiceRequest request = WifiP2pUpnpServiceRequest.newInstance();
        final Handler handler = new Handler();

        p2p.addServiceRequest(channel, request, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                handler.postDelayed(new Runnable() {

                    @Override
                    public void run() {
                        p2p.discoverServices(channel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                myServiceList.clear();
                                Log.i(LOG_TAG, "Started service discovery");
                                myServiceState = ServiceState.DiscoverService;
                            }
                            public void onFailure(int reason) {
                                stopDiscovery();
                                myServiceState = ServiceState.NONE;
                                if(callback != null) {
                                    callback.ServiceStartError(reason);
                                }
                                Log.i(LOG_TAG, "Starting service discovery failed, error code " + reason);
                                //lets try again after 1 minute time-out !
                                ServiceDiscoveryTimeOutTimer.start();
                            }
                        });
                    }
                }, 1000);
            }

            @Override
            public void onFailure(int reason) {
                myServiceState = ServiceState.NONE;
                if(callback != null) {
                    callback.AddReqStartError(reason);
                }
            }
        });
    }

    private void stopDiscovery() {

    }

    private void startPeerDiscovery() {
        p2p.discoverPeers(channel, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                // if(myServiceState != ServiceState.WaitingTimer) {
                myServiceState = ServiceState.DiscoverPeer;
                Log.i(LOG_TAG, "Started peer discovery");
                // }else{
                //     debug_print("Keeping up visibility by starting peer discovery while waiting");
                // }
            }
            public void onFailure(int reason) {
                myServiceState = ServiceState.NONE;
                Log.i(LOG_TAG, "Starting peer discovery failed, error code " + reason);
                if(callback != null) {
                    callback.PeerStartError(reason);
                }
                //lets try again after 1 minute time-out !
                ServiceDiscoveryTimeOutTimer.start();
            }
        });
    }

    private void stopPeerDiscovery() {
        p2p.stopPeerDiscovery(channel,
                new TaggedActionListener(null, "Stopped peer discover") );
    }

    private class ServiceSearcherReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if(WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if(callback != null) {
                    callback.PeerChangedEvent();
                }

                if(myServiceState != ServiceState.DiscoverService) {
                    //&& myServiceState != ServiceState.WaitingTimer) {
                    p2p.requestPeers(channel, peerListListener);
                }

            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action) ){
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE,
                                                WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                String persTatu = "Discovery state changed to ";

                if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                    if (callback != null) {
                        callback.PeerDiscoveryStopped();
                    }
                    persTatu = persTatu + "Stopped.";
                    startPeerDiscovery();
                } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    persTatu = persTatu + "Started.";
                } else {
                    persTatu = persTatu + "unknown  " + state;
                }

                Log.i(LOG_TAG, persTatu);
            }
        }
    }
}
