package com.labs.okey.freeride.utils.wifip2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Oleg Kleiman on 14-Sep-15.
 */
public class WifiBase implements WifiP2pManager.ChannelListener {

    public interface WifiStatusCallBack {
        public void WifiStateChanged(int state);

        public void gotPeersList(Collection<WifiP2pDevice> list);

        public void gotServicesList(List<ServiceItem> list);

        public void gotDnsTXTRecordList(Map<String, String> mapping);

        //additional for testing only
        public void PeerStartError(int error);

        public void ServiceStartError(int error);

        public void AddReqStartError(int error);

        public void PeerChangedEvent();

        public void PeerDiscoveryStopped();

        public void WaitTimeCallback();
    }

    private static final String LOG_TAG = "FR.WiFi.Base";

    public static final String SERVICE_TYPE = "_BT_p2p._tcp";

    private List<ServiceItem> connectedArray = new ArrayList<ServiceItem>();
    private WifiP2pManager.Channel channel = null;
    private WifiP2pManager p2p = null;
    private Context context;

    WifiStatusCallBack callback;
    MainBCReceiver mBRReceiver;
    private IntentFilter filter;

    public WifiBase(Context Context, WifiStatusCallBack handler) {
        this.context = Context;
        this.callback = handler;
    }

    public WifiP2pManager.Channel GetWifiChannel(){
        return channel;
    }
    public WifiP2pManager GetWifiP2pManager(){
        return p2p;
    }

    public boolean start() {

        boolean ret = false;

        mBRReceiver = new MainBCReceiver();

        filter = new IntentFilter();
        filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        this.context.registerReceiver((mBRReceiver), filter);

        p2p = (WifiP2pManager) this.context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (p2p == null) {
            Log.d(LOG_TAG, "This device does not support Wi-Fi Direct");
        } else {
            ret = true;
            channel = p2p.initialize(this.context, this.context.getMainLooper(), this);
        }
        return ret;
    }

    public void stop(){
        this.context.unregisterReceiver(mBRReceiver);
    }

    public boolean isWifiEnabled(){
        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if(wifiManager != null) {
            return wifiManager.isWifiEnabled();
        }else{
            return false;
        }
    }

    public boolean setWifiEnabled(boolean enabled){
        WifiManager wifiManager = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if(wifiManager != null) {
            return wifiManager.setWifiEnabled(enabled);
        }else{
            return false;
        }
    }

    @Override
    public void onChannelDisconnected() {
        // we might need to do something in here !
    }

    public ServiceItem SelectServiceToConnect(List<ServiceItem> available){

        ServiceItem  ret = null;

        if(connectedArray.size() > 0 && available.size() > 0) {

        }

        return ret;
    }

    private class MainBCReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                if (callback != null) {
                    callback.WifiStateChanged(state);
                }
            }
        }
    }

}