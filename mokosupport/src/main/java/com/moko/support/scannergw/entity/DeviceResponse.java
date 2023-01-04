package com.moko.support.scannergw.entity;


import java.io.Serializable;

public class DeviceResponse implements Serializable{
    public int code;
    public String message;
    public DeviceResult result;
}
