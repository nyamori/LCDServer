package com.example.lcdserver.socket;

import android.content.Context;
import android.util.Log;

import com.example.lcdserver.Config;
import com.example.lcdserver.Listener.OnConnectionStateListener;
import com.example.lcdserver.Listener.OnRecvMessageListener;
import com.example.lcdserver.Listener.OnUIChangeListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;


public class SocketManager {
    private static volatile SocketManager instance = null;

    private final static String TAG="SocketManager";

    private Context mContext;
    private TcpServerSocket tcpServerSocket;
    private UdpSocket udpSocket;
    private List<OnUIChangeListener> onUIChangeListenerList;



    private SocketManager(Context context){
        mContext=context.getApplicationContext();
        onUIChangeListenerList=new ArrayList<>();
    }

    public static  SocketManager getInstance(Context context) {
        if(instance==null){
            synchronized (SocketManager.class){
                if(instance==null){
                    instance = new SocketManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    //暂时还没有用
    public void setOnUIChangeListener(OnUIChangeListener listener) {
        onUIChangeListenerList.add(listener);
    }

    /**
     * 启动udp连接并写接口内的逻辑
     * 考虑把OnRecvMessageListener和OnUIChangeListener写成同一个，前者完全被后者覆盖
     */
    public void startUdpConnection() {
        if(udpSocket==null){
            udpSocket = new UdpSocket(mContext);
        }
        Log.d(TAG, "startUdpConnection: 启动udp");
        udpSocket.addOnRecvMessageListener(new OnRecvMessageListener() {
            @Override
            public void onRecvMessage(String message) {
                handleUdpMessage(message);
            }

            @Override
            public void onError(int errorCode) {
                switch (errorCode){
                    case Config.ErrorCode.UDP_PING_TIME_OUT:
                        udpSocket.stopHeartBeatTimer();
                        break;
                    case Config.ErrorCode.NO_WIFI:
                        udpSocket.stopHeartBeatTimer();
                        for (OnUIChangeListener listener:onUIChangeListenerList){
                            listener.onError(Config.ErrorCode.NO_WIFI);
                        }
                        stopSocket();
                        break;
                    default:
                        break;
                }
            }
        });
        udpSocket.startUdpSocket();
        startTcpConnection();
    }


    /**
     * 服务器专用的处理逻辑，客户端同名函数逻辑不同
     * 以下各个函数同
     */
    private void handleUdpMessage(String message) {
        try {
            JSONObject jsonObject=new JSONObject(message);
            int request=jsonObject.optInt("request");
            if(request == Config.MsgCode.GET_CLIENT_REQUEST){
                Log.d(TAG, "handleUdpMessage: 收到了客户端的请求");
                for (OnUIChangeListener listener:onUIChangeListenerList){
                    listener.onChange(Config.MsgCode.GET_CLIENT_REQUEST,"");
                }
                udpSocket.startHeartBeatTimer();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void startTcpConnection() {
        if(tcpServerSocket ==null){
            tcpServerSocket =new TcpServerSocket(mContext);
            tcpServerSocket.setOnConnectionStateListener(new OnConnectionStateListener() {
                @Override
                public void onLink() {
                    udpSocket.stopHeartBeatTimer();
                }
                @Override
                public void onError(int errorCode) {
                    switch (errorCode){
                        case Config.ErrorCode.TCP_CONNECT_ERROR:
                            break;
                        default:
                            break;
                    }
                }
            });
        }
        tcpServerSocket.initSocket();
    }


    public void stopSocket() {
        if(udpSocket!=null) {
            udpSocket.stopUDPSocket();
            udpSocket=null;
        }
        if(tcpServerSocket !=null){
            tcpServerSocket.closeSocket();
            tcpServerSocket =null;
        }
    }
}
