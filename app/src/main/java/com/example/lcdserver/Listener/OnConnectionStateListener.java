package com.example.lcdserver.Listener;

public interface OnConnectionStateListener {
    void onLink();
    void onError(int errorCode);
}
