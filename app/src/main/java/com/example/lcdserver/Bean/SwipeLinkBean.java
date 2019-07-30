package com.example.lcdserver.Bean;

import java.net.Socket;

public class SwipeLinkBean {
    private String userName;
    private Socket socket;

    public SwipeLinkBean(String userName, Socket socket){
        this.socket=socket;
        this.userName=userName;
    }

    public String getUserName() {
        return userName;
    }

    public Socket getSocket() {
        return socket;
    }
}
