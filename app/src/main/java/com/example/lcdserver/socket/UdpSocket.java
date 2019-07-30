package com.example.lcdserver.socket;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.example.lcdserver.Config;
import com.example.lcdserver.Listener.OnRecvMessageListener;
import com.example.lcdserver.ServerApplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UdpSocket {
    private static final String TAG = "UdpSocket";
    private static final long TIME_OUT = 15*1000;
    private static final long SEND_PERIOD = 1000;
    private static final int BUFFER_LENGTH = 1024;
    private byte[] receiveByte = new byte[BUFFER_LENGTH];


    public static final int CLIENT_PORT = 2425;

    private  boolean isThreadRunning = false;

    private Context mContext;
    private DatagramSocket client;
    private DatagramPacket receivePacket;
    private ExecutorService mThreadPool;
    private Thread clientThread;
    private HeartBeatTimer timer;
    private long lastRecvTime=0;

    private final List<OnRecvMessageListener> RecvMessageList;
    public UdpSocket(Context context){
        mContext = context;

        int cpuNum=Runtime.getRuntime().availableProcessors();
        mThreadPool= Executors.newFixedThreadPool(cpuNum+1);
        lastRecvTime =System.currentTimeMillis();
        RecvMessageList = new ArrayList<>();
    }

    public void addOnRecvMessageListener(OnRecvMessageListener onRecvMessageListener){RecvMessageList.add(onRecvMessageListener);}

    private void notifyRecvMessage(String message){
        for(OnRecvMessageListener listener:RecvMessageList){
            if(listener!=null) listener.onRecvMessage(message);
        }
    }

    public void startUdpSocket() {
        if(client!=null)return;
        try {
            client=new DatagramSocket(CLIENT_PORT);
            client.setReuseAddress(true);
            if(receivePacket==null){
                receivePacket = new DatagramPacket(receiveByte,BUFFER_LENGTH);
            }
            startUdpThread();
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private void startUdpThread() {
        clientThread = new Thread(new Runnable() {
            @Override
            public void run() {
                recvMsg();
            }
        });
        isThreadRunning = true;
        clientThread.start();
    }

    public void startHeartBeatTimer() {
        if(timer==null){
            timer = new HeartBeatTimer();
        }
        timer.setOnScheduleListener(new HeartBeatTimer.OnScheduleListener() {
            @Override
            public void onSchedule() {
                Log.d(TAG, "timer is onSchedule...");
                long duration = System.currentTimeMillis()-lastRecvTime;
                if(duration>TIME_OUT){
                    Log.d(TAG, "onSchedule: 超时");
                    lastRecvTime=System.currentTimeMillis();
                    for(OnRecvMessageListener listener:RecvMessageList)listener.onError(Config.ErrorCode.UDP_PING_TIME_OUT);
                }else if(duration>SEND_PERIOD){
                    Log.d(TAG, "onSchedule: 发送有ip和port的数据包");
                    sendRequest();
                }

            }
        });
        timer.startTimer(0,1000);
    }

    private void sendRequest() {
        String localIP=getLocalIP();
        Log.d(TAG, "sendRequest: "+localIP);
        final InetAddress broadcastIp;
        try {
            broadcastIp = InetAddress.getByName(ServerApplication.getBroadcast());
            if(localIP==null||broadcastIp== null){
                for (OnRecvMessageListener listener:RecvMessageList) {
                    listener.onError(Config.ErrorCode.NO_WIFI);
                }
            }else {
                Log.d(TAG, "sendRequest: broadcastIp:"+broadcastIp.toString());
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("ip",localIP);
                jsonObject.put("port",4396);
                jsonObject.put("request", Config.MsgCode.GET_SERVER_IP);

                final String message = jsonObject.toString();
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        DatagramPacket datagramPacket = new DatagramPacket(message.getBytes(),message.length(),broadcastIp,CLIENT_PORT);
                        try {
                            client.send(datagramPacket);
                            Log.d(TAG, "数据发送成功");
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void stopHeartBeatTimer(){
        if (timer!=null){
            timer.exit();
            timer = null;
        }
    }

    private void recvMsg() {
        while (isThreadRunning) {
            long recvTime=0;
            try {
                if (client != null) {
                    Log.d(TAG, "recvMsg: 开始等待数据");
                    client.receive(receivePacket);
                }
                Log.d(TAG, "recvMsg:receive packet success ");
                recvTime =System.currentTimeMillis();
            } catch (IOException e) {
                Log.e(TAG, "UDP数据包接收失败！线程停止");
                stopUDPSocket();
                isThreadRunning=false;
                e.printStackTrace();
                return;
            }
            if (receivePacket == null || receivePacket.getLength() == 0) {
                Log.e(TAG, "无法接收UDP数据或者接收到的UDP数据为空");
                continue;
            }
            String strReceive = new String(receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength());
            Log.d(TAG, strReceive + " from " + receivePacket.getAddress().getHostAddress() + ":" + receivePacket.getPort());
            //这里要屏蔽自己不然定时器一直不会超时
            try {
                JSONObject jsonObject = new JSONObject(strReceive);
                int request =jsonObject.optInt("request");
                if(request!=Config.MsgCode.GET_SERVER_IP){
                    notifyRecvMessage(strReceive);
                    lastRecvTime=recvTime;
                    Log.d(TAG, "recvMsg: lastRecvTime已经更新");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (receivePacket != null) {
                receivePacket.setLength(BUFFER_LENGTH);
            }
        }
    }


    public void stopUDPSocket() {
        isThreadRunning = false;
        receivePacket = null;
        if (clientThread != null) {
            clientThread.interrupt();
        }
        if (mThreadPool != null) {
            mThreadPool.shutdown();
        }
        if (client != null) {
            client.close();
            client = null;
        }
    }

    private String getLocalIP(){
        WifiManager wifiManager=(WifiManager)mContext.getSystemService(Context.WIFI_SERVICE);
        if(!wifiManager.isWifiEnabled()){
            wifiManager.setWifiEnabled(true);
            return null;
        }
        WifiInfo wifiInfo=wifiManager.getConnectionInfo();
        int ipAddr=wifiInfo.getIpAddress();
        return IntToIP(ipAddr);
    }

    private String IntToIP(int ipAddr) {
        return (ipAddr & 0xFF ) + "." +
                ((ipAddr >> 8 ) & 0xFF) + "." +
                ((ipAddr >> 16 ) & 0xFF) + "." +
                ( ipAddr >> 24 & 0xFF) ;
    }
}
