package com.labs.okey.freeride.utils.wifip2p;

import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import com.labs.okey.freeride.utils.ITrace;

/**
 * Created by Oleg Kleiman on 14-Sep-15.
 */
public class TaggedActionListener implements WifiP2pManager.ActionListener{

    String tag;
    ITrace mTracer;

    private static final String LOG_TAG = "FR.WiFi.TagListener";

    public TaggedActionListener(ITrace tracer, String tag){
        this.tag = tag;
        mTracer = tracer;
    }

    @Override
    public void onSuccess() {
        String message = tag + " succeeded";
        if( mTracer != null )
            mTracer.trace(message);
        Log.d(LOG_TAG, message);
    }

    @Override
    public void onFailure(int reasonCode) {
        String message = tag + " failed. Reason: " + failureReasonToString(reasonCode);
        if( mTracer != null )
            mTracer.trace(message);
        Log.e(LOG_TAG, message);
    }

    private String failureReasonToString(int reason) {

        switch ( reason ){
            case WifiP2pManager.ERROR:
                return "Error";

            case WifiP2pManager.P2P_UNSUPPORTED:
                return "P2P unsupported";

            case WifiP2pManager.BUSY:
                return "Busy";

            default:
                return "Unknown";
        }
    }
}

