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
        Log.d(LOG_TAG, message);
    }

    @Override
    public void onFailure(int reasonCode) {
        String message = tag + " failed. Reason :" + failureReasonToString(reasonCode);
        if( mTracer != null )
            mTracer.trace(message);
        Log.d(LOG_TAG, message);
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
}

