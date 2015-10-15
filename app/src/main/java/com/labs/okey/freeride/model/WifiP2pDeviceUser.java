package com.labs.okey.freeride.model;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Created by Oleg Kleiman on 12-May-15.
 */
public class WifiP2pDeviceUser extends WifiP2pDevice
                               implements Parcelable {
    private String _userName;
    public String getUserName() {
        return _userName;
    }
    public void setUserName(String value) {
        _userName = value;
    }

    private String _userId;
    public String getUserId() {
        return _userId;
    }
    public void setUserId(String value) {
        _userId = value;
    }

    private String _rideCode;
    public String getRideCode() { return  _rideCode; }
    public void setRideCode(String value) { _rideCode = value; }

    public WifiP2pDeviceUser(WifiP2pDevice device) {
        super(device);
    }

    public WifiP2pDeviceUser(String deviceName,
                             String deviceAddress) {
        this.deviceName = deviceName;
        this.deviceAddress = deviceAddress;
    }

    //
    // Implementation of Parcelable (partially, the rest is implemented in base class
    //

    private WifiP2pDeviceUser(Parcel in) {
        setUserName(in.readString());
        setUserId(in.readString());
        setRideCode(in.readString());
    }

    public static final Parcelable.Creator<WifiP2pDeviceUser> CREATOR = new Parcelable.Creator<WifiP2pDeviceUser>() {
        public WifiP2pDeviceUser createFromParcel(Parcel in) {
            return new WifiP2pDeviceUser(in);
        }

        public WifiP2pDeviceUser[] newArray(int size) {
            return new WifiP2pDeviceUser[size];
        }
    };

}
