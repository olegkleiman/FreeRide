package com.labs.okey.freeride.model;

import com.google.gson.annotations.Expose;

/**
 * Created by Oleg on 9/16/15.
 */
public class GlobalSettings {

    @Expose
    @com.google.gson.annotations.SerializedName("id")
    public String Id;

    @Expose
    @com.google.gson.annotations.SerializedName("s_name")
    private String _name;
    public String getName() {
        return _name;
    }
    public void setName(String value) { _name = value; }

    @Expose
    @com.google.gson.annotations.SerializedName("s_value")
    private String _value;
    public String getValue() {
        return _value;
    }
    public void setValue(String value) { _value = value; }
}
