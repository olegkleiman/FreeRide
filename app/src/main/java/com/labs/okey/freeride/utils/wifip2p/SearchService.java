package com.labs.okey.freeride.utils.wifip2p;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.BatteryManager;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by Oleg on 14-Sep-15.
 */
public class SearchService extends Service implements WifiBase.WifiStatusCallBack{

    private static final String LOG_TAG = "FR.WiFi.SearchService";

    WifiBase mWifiBase = null;
    private final IBinder mBinder = new MyLocalBinder();

    IntentFilter mfilter = null;
    BroadcastReceiver mReceiver = null;

    WifiServiceAdvertiser mWifiAccessPoint = null;
    WifiServiceSearcher mWifiServiceSearcher = null;

    CountDownTimer WifiResetTimeOutTimer = new CountDownTimer(1800000, 1000) { //
        public void onTick(long millisUntilFinished) {
            // not using
        }
        public void onFinish() {
            WifiCounterCount = WifiCounterCount + 1;
            //Restart service discovery
            //startServices();

            //switch off Wlan :)
            if(mWifiBase != null) {
                ITurnedWifiOff = true;
                mWifiBase.setWifiEnabled(false);
            }
        }
    };

    long WifiCounterCount = 0;
    Boolean ITurnedWifiOff = false;

    public void start() {

        mfilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mReceiver = new PowerConnectionReceiver();
        registerReceiver(mReceiver, mfilter);

        WifiResetTimeOutTimer.start();
        startServices();
    }

    public void stop() {
        WifiResetTimeOutTimer.cancel();

        if(mReceiver != null){
            unregisterReceiver(mReceiver);
            mReceiver = null;
        }

        stopServices();
        if(mWifiBase != null){
            mWifiBase.stop();
            mWifiBase = null;
        }
    }

    private void startServices(){
        stopServices();

        if(mWifiBase == null){
            mWifiBase = new WifiBase(this,this);
            mWifiBase.start();
        }

        WifiP2pManager.Channel channel = mWifiBase.GetWifiChannel();
        WifiP2pManager p2p = mWifiBase.GetWifiP2pManager();

        if(channel != null && p2p != null) {
            Log.i(LOG_TAG, "Starting services");

            mWifiAccessPoint = new WifiServiceAdvertiser(p2p, channel);
            mWifiAccessPoint.start("powerTests", WifiBase.SERVICE_TYPE);

            mWifiServiceSearcher = new WifiServiceSearcher(this, p2p, channel,
                                                           this, WifiBase.SERVICE_TYPE);
            mWifiServiceSearcher.start();
            Log.i(LOG_TAG, "services started");
        }
    }

    private void stopServices(){

    }

    public class MyLocalBinder extends Binder {
        public SearchService getService() {
            return SearchService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.i(LOG_TAG, "onStartCommand rounds so far :" + fullRoundCount);
        start();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(LOG_TAG,"onDestroy");
        super.onDestroy();
        //ServiceFoundTimeOutTimer.cancel();
        stop();
    }

    @Override
    public void WifiStateChanged(int state) {
        if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
            startServices();
        } else {
            stopServices();
        }
    }

    //
    // Implementing WifiBase.WifiStatusCallBack
    //
    @Override
    public void gotPeersList(Collection<WifiP2pDevice> list) {

    }

    @Override
    public void gotServicesList(List<ServiceItem> list) {
        if(mWifiServiceSearcher != null & mWifiBase != null) {

            if (list != null && list.size() > 0) {

                ServiceItem selItem = mWifiBase.SelectServiceToConnect(list);

            }

        }
    }

    @Override
    public void gotDnsTXTRecordList(Map<String, String> mapping) {

    }

    @Override
    public void PeerStartError(int error) {

    }

    @Override
    public void ServiceStartError(int error) {

    }

    @Override
    public void AddReqStartError(int error) {

    }

    @Override
    public void PeerChangedEvent() {

    }


    @Override
    public void PeerDiscoveryStopped() {

    }

    @Override
    public void WaitTimeCallback() {

    }

    public class PowerConnectionReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        }
    }
}
