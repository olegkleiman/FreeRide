package com.labs.okey.freeride;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.test.InstrumentationTestCase;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.labs.okey.freeride.utils.Globals;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * Created by Oleg on 29-Jan-16.
 */
public class WFDClearTest extends InstrumentationTestCase
    implements WifiP2pManager.ChannelListener{

    private ListeningExecutorService executorService;
    private CountDownLatch startSignal;
    private CountDownLatch endSignal;
    private Boolean callbackCalled;

    private WifiP2pManager          mWiFiP2pManager;
    private WifiP2pDnsSdServiceInfo mServiceInfo;
    private WifiP2pManager.Channel  mChannel;

    protected void setUp() throws Exception {
        super.setUp();

        startSignal = new CountDownLatch(1);
        endSignal = new CountDownLatch(1);

        callbackCalled = false;

        Context context = getInstrumentation().getTargetContext();

        mWiFiP2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        assertNotNull(mWiFiP2pManager);

        mChannel = mWiFiP2pManager.initialize(context, context.getMainLooper(), this);

        Map<String, String> record = new HashMap<>();
        record.put(Globals.TXTRECORD_PROP_PORT, Globals.SERVER_PORT);
        record.put(Globals.TXTRECORD_PROP_USERID, "fb:12345678");

        mServiceInfo = WifiP2pDnsSdServiceInfo.newInstance(
                Globals.SERVICE_INSTANCE,
                Globals.SERVICE_REG_TYPE,
                record);

        mWiFiP2pManager.addLocalService(mChannel, mServiceInfo,
                new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        startSignal.countDown();
                    }

                    @Override
                    public void onFailure(int reason) {
                        //throw new Exception("addLocalService failure");
                    }
                });

        startSignal.await();
        executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(2));
    }

    protected void tearDown() throws Exception {
        super.tearDown();

        executorService.shutdown();
    }

    public void testClear() throws Exception {

        ListenableFuture<Boolean> futureTask = executorService.submit(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {

                final CountDownLatch removalSignal = new CountDownLatch(2);

                mWiFiP2pManager.removeLocalService(mChannel, mServiceInfo,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                removalSignal.countDown();
                            }

                            @Override
                            public void onFailure(int reason) {
                                removalSignal.countDown();
                            }
                        });

                mWiFiP2pManager.clearServiceRequests(mChannel,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                removalSignal.countDown();
                            }

                            @Override
                            public void onFailure(int reason) {
                                removalSignal.countDown();
                            }
                        });

                removalSignal.await();
                return true;
            }
        });

        futureTask.addListener(new Runnable() {
            @Override
            public void run() {
                callbackCalled = true;
                endSignal.countDown();
            }
        }, executorService);

        endSignal.await();
        assertTrue(callbackCalled);
    }

    @Override
    public void onChannelDisconnected() {

    }
}
