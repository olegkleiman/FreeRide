package com.labs.okey.freeride.utils;

import android.content.Context;

import java.io.File;

/**
 * Created by Oleg on 22-Aug-15.
 */
public class Globals {

    private static Object lock2 = new Object();
    private static String MONITOR_STATUS;
    public static String getMonitorStatus() {
        synchronized (lock2) {
            return MONITOR_STATUS;
        }
    }
    public static void setMonitorStatus(String value) {
        synchronized (lock2) {
            MONITOR_STATUS = value;
        }
    }

    private static final Object lock = new Object();
    public static  String CASCADE_URL = "http://maximum.azurewebsites.net/data/lbpcascades/lbpcascade_frontalface.xml";
    private static String CASCADE_PATH;
    public static void initCascadePath(Context ctx) {
        String DEFAULT_CASCADE_NAME = "lbpcascade_frontalface.xml";
        File file = new File(ctx.getFilesDir(), DEFAULT_CASCADE_NAME);
        synchronized (lock ) {
            CASCADE_PATH = file.getAbsolutePath();
        }
    }

    public static String getCascadePath (Context ctx) {
        if( CASCADE_PATH == null || CASCADE_PATH.isEmpty() )
            initCascadePath(ctx);

        return CASCADE_PATH;
    }
    public static void setCascadePath(String path) {
        synchronized (lock) {
            CASCADE_PATH = path;
        }
    }

}
