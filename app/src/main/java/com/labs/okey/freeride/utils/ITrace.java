package com.labs.okey.freeride.utils;

/**
 * Created by Oleg Kleiman on 09-May-15.
 */
public interface ITrace {
    public void trace(String status);
    public void alert(String message, String intent);
}
