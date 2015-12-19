package com.labs.okey.freeride.model;

/**
 * Created by Oleg on 18-Dec-15.
 */
public class GeoFence {

    @com.google.gson.annotations.SerializedName("id")
    public String Id;

    @com.google.gson.annotations.SerializedName("lat")
    private float lat;
    public float getLat() { return lat; }
    public void setLat(float val) { lat = val; }

    @com.google.gson.annotations.SerializedName("lon")
    private float lon;
    public float getLon() { return lon; }
    public void setLon(float val) { lon = val; }

    @com.google.gson.annotations.SerializedName("label")
    private String label;
    public String getLabel() { return label; }
    public void setLabel(String value) { label = value; }

    @com.google.gson.annotations.SerializedName("radius")
    private float radius;
    public float getRadius() { return radius; }
    public void setRadius(float val) { radius = val; }

    @com.google.gson.annotations.SerializedName("isactive")
    private Boolean _isActive;
    public boolean isActive() { return _isActive; }
    public void setActive(boolean value) { _isActive = value; }

    @com.google.gson.annotations.SerializedName("route_code")
    private String _routeCode;
    public String getRouteCode() { return _routeCode; }

}
