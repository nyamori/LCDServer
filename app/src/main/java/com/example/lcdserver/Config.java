package com.example.lcdserver;

public  class Config {
    public static class ErrorCode{
        public final static int TCP_CONNECT_ERROR=1;
        public final static int PING_TIME_OUT=2;
        public final static int NO_WIFI=3;
        public final static int UDP_PING_TIME_OUT=4;
    }

    public static class MsgCode{
        public final static int GET_CLIENT_REQUEST=10;
        public final static int GET_SERVER_IP=11;
        public final static int LOGIN_SUCCESS=12;
        public final static int LOGIN_FAIL=13;
        public final static int DISPLAY_NSG=14;
        public final static int SET_DISPLAY = 15;
        public final static int SET_SUCCESS=16;
        public final static int SET_FAIL=17;
        public final static int OFFLINE=18;
        public final static int CHANGE_PASSWORD=19;
        public final static int CHANGE_FAIL=20;


        public final static int NEW_TIME=101;
        public final static int WEATHER_PRE=102;
        public final static int NEW_CONTENT=103;
        public final static int LCD_THREAD_CLOSE=104;

        public final static int PING=1001;
    }

    public static class OnlineType{
        public final static int OFFLINE=0;
        public final static int ONLINE=1;
    }
}
