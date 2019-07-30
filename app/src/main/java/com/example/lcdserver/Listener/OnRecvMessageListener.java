package com.example.lcdserver.Listener;

public interface OnRecvMessageListener {
        void onRecvMessage(String message);
        void onError(int errorCode);
}
