package com.labs.okey.freeride.utils.wifip2p;

/**
 * Created by Oleg Kleiman on 14-Sep-15.
 */
public class ServiceItem {

    public ServiceItem(String instance, String type, String address, String name){
        this.instanceName = instance;
        this.serviceType = type;
        this.deviceAddress = address;
        this.deviceName =  name;
    }
    public String instanceName;
    public String serviceType;
    public String deviceAddress;
    public String deviceName;
}
