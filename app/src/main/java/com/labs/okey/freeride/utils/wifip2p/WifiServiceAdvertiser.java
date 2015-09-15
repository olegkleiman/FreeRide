package com.labs.okey.freeride.utils.wifip2p;

import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pUpnpServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Oleg on 14-Sep-15.
 */
public class WifiServiceAdvertiser {

    private static final String LOG_TAG = "FR.WiFi.Advertiser";

    private WifiP2pManager p2p;
    private WifiP2pManager.Channel channel;

    int lastError = -1;

    public WifiServiceAdvertiser(WifiP2pManager Manager,
                                 WifiP2pManager.Channel Channel) {
        this.p2p = Manager;
        this.channel = Channel;
    }

    public void start(String instance,String service_type) {
        String uuid = "6859dede-8574-59ab-9332-123456789088";

        String device = "urn:schemas-upnp-org:device:A";

        List<String> services = new ArrayList<String>();

        services.add("000000000011111111112222222222333333333344444444445555555555666666666677777777778888888888999999999000000000011111111112222222222333333333344444444445555555555666666666677777777778888888888");
        services.add("111111111122222222223333333333444444444455555555556666666666777777777788888888889999999990000000000000000000011111111112222222222333333333344444444445555555555666666666677777777778888888888");
        services.add("222222222233333333334444444444555555555566666666667777777777888888888899999999900000000001111111111000000000011111111112222222222333333333344444444445555555555666666666677777777778888888888");
        services.add("333333333344444444445555555555666666666677777777778888888888999999999000000000011111111112222222222000000000011111111112222222222333333333344444444445555555555666666666677777777778888888888");

        // create a new instance to advertise
        WifiP2pUpnpServiceInfo service =  WifiP2pUpnpServiceInfo.newInstance(uuid,device,services);

        p2p.addLocalService(channel, service, new WifiP2pManager.ActionListener() {
            public void onSuccess() {
                lastError = -1;
                Log.i(LOG_TAG, "Added local service");
            }

            public void onFailure(int reason) {
                lastError = reason;
                Log.i(LOG_TAG, "Adding local service failed, error code " + reason);
            }
        });
    }

    public void stop() {
        p2p.clearLocalServices(channel,
                new TaggedActionListener(null, "clear local services"));
    }
}
