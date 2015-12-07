package com.labs.okey.freeride.model;

/**
 * Created by Oleg Kleiman on 03-Dec-15.
 */
public class GFCircle {
    private double x;
    private double y;
    private int radius;
    private String tag;

    public GFCircle(double x, double y, int radius, String tag){
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.tag = tag;
    }

    public double getX(){
        return x;
    }

    public double getY() {
        return y;
    }

    public int getRadius() {
        return radius;
    }

    public String getTag() {
        return tag;
    }
}
