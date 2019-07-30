package com.example.lcdserver.Bean;

import org.litepal.crud.LitePalSupport;

public class User extends LitePalSupport {
    private int id;
    private String account;
    private String password;
    private String deviceName;
    private String deviceID;

    public User(String account,String password){
        this.account=account;
        this.password=password;
    }

    public String getAccount() {
        return account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String newPassword){
        password=newPassword;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public void setDeviceID(String deviceID) {
        this.deviceID = deviceID;
    }
}
