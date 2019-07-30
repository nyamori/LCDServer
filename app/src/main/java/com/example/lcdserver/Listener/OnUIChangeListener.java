package com.example.lcdserver.Listener;

public interface OnUIChangeListener {
    void onChange(int msgCode, String msg);
    void onError(int errorCode);
}
