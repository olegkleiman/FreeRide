package com.labs.okey.freeride.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;

/**
 * Created by Oleg on 23-Feb-16.
 * See http://stackoverflow.com/questions/21256274/capturing-executor-for-current-thread
 */
public class UiThreadExecutor implements Executor {

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public void execute(Runnable r) {
        mHandler.post(r);
    }
}
