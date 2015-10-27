package com.labs.okey.freeride.utils;

/**
 * Created by Oleg Kleiman on 18-Aug-15.
 */
public interface IPicturesVerifier {
    void update(String url);
    void finished(boolean sucess);
}