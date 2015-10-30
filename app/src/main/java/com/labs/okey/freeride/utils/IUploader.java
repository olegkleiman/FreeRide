package com.labs.okey.freeride.utils;

/**
 * Created by Oleg Kleiman on 18-Aug-15.
 */
public interface IUploader {
    void update(String url);
    void finished(int tag, boolean sucess);
}