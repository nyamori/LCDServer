package com.example.lcdserver.socket;

import android.app.DownloadManager;
import android.content.Context;
import android.util.Log;

import com.example.lcdserver.Bean.User;
import com.example.lcdserver.Config;
import com.example.lcdserver.Listener.OnConnectionStateListener;
import com.example.lcdserver.ServerApplication;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class TcpServerSocket {
    private static final String TAG = "TcpServerSocket";
    private static final long TIME_OUT = 30 * 1000;
    private static final long PING_PERIOD = 3 * 1000;
    private static final String KEYSTOREPASSWORD = "123456";    //密钥库密码
    private static final String KEYSTOREPATH_SERVER = "kserver.bks";    //本地密钥库
    private static final String KEYSTOREPATH_TRUST = "tserver.bks";        //信任密钥库
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");


    private Context mContext;

    private SSLContext sslContext;
    private KeyStore clientKeyStore;
    private KeyStore trustKeyStore;
    private KeyManagerFactory keyManagerFactory;
    private TrustManagerFactory trustManagerFactory;

    private ExecutorService mThreadPool;
    private SSLServerSocket serverSocket = null;
    private OnConnectionStateListener mListener;

    private boolean isAcceptRunning=false;
    private int linkCounter=0;

    public TcpServerSocket(Context context) {
        mContext = context;
        mThreadPool = Executors.newCachedThreadPool();
    }


    public void setOnConnectionStateListener(OnConnectionStateListener listener){
        mListener=listener;
    }

    public boolean closeSocket() {
        try {
            if (mThreadPool != null) {
                mThreadPool.shutdown();
                mThreadPool = null;
            }
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void initSocket() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (serverSocket == null) {
                            //取得TLS协议的SSLContext实例
                            sslContext = SSLContext.getInstance("TLS");
                            //取得BKS类型的本地密钥库实例，这里特别注意：手机只支持BKS密钥库，不支持Java默认的JKS密钥库
                            clientKeyStore = KeyStore.getInstance("BKS");
                            //初始化
                            clientKeyStore.load(
                                    mContext.getResources().getAssets().open(KEYSTOREPATH_SERVER),
                                    KEYSTOREPASSWORD.toCharArray());
                            trustKeyStore = KeyStore.getInstance("BKS");
                            trustKeyStore.load(mContext.getResources().getAssets()
                                    .open(KEYSTOREPATH_TRUST), KEYSTOREPASSWORD.toCharArray());
                            //获得X509密钥库管理实例
                            keyManagerFactory = KeyManagerFactory.getInstance("X509");
                            keyManagerFactory.init(clientKeyStore, KEYSTOREPASSWORD.toCharArray());
                            trustManagerFactory = TrustManagerFactory
                                    .getInstance("X509");
                            trustManagerFactory.init(trustKeyStore);
                            //初始化SSLContext实例
                            sslContext.init(keyManagerFactory.getKeyManagers(),
                                    trustManagerFactory.getTrustManagers(), null);

                            SSLServerSocketFactory sslServerSocketFactory=sslContext.getServerSocketFactory();
                            serverSocket=(SSLServerSocket)sslServerSocketFactory.createServerSocket(4396);
                            Log.d(TAG, "initSocket: 创建了一个socket");
                            online(Config.OnlineType.ONLINE,ServerApplication.getDeviceName(),ServerApplication.getMacFromHardware(),1);
                            serverSocket.setReuseAddress(true);
                            isAcceptRunning = true;
                        }
                        while (isAcceptRunning) {
                            try {
                                SSLSocket socket =(SSLSocket)serverSocket.accept();
                                linkCounter=linkCounter+1;
                                Log.d(TAG, "run: 接收到第"+linkCounter+"个连接");
                                //多线程多客户端需要一个map记录
                                mListener.onLink();
                                mThreadPool.execute(new ServerThread(socket));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (CertificateException e) {
                        e.printStackTrace();
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    } catch (UnrecoverableKeyException e) {
                        e.printStackTrace();
                    } catch (KeyStoreException e) {
                        e.printStackTrace();
                    } catch (KeyManagementException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
    }

    public static void online(int type,String deviceName,String deviceID,int deviceType){
        try {
            String url="http://192.168.112.66:8080/V1.0/online";
            SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
            String time= sdf.format( new Date());
            JSONObject jsonObject=new JSONObject();
            jsonObject.put("type",type);
            jsonObject.put("time",time);
            jsonObject.put("deviceName",deviceName);
            jsonObject.put("deviceID",deviceID);
            jsonObject.put("deviceType",deviceType);
            Log.i(TAG, "online: 发送的消息"+jsonObject.toString());
            OkHttpClient client=new OkHttpClient();
            //RequestBody requestBody= RequestBody.create(JSON,jsonObject.toString());
            RequestBody requestBody=new FormBody.Builder()
                    .add("type",String.valueOf(type))
                    .add("time",time)
                    .add("deviceName",deviceName)
                    .add("deviceID",deviceID)
                    .add("deviceType",String.valueOf(deviceType))
                    .build();
            Request request=new Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build();
            Call call=client.newCall(request);
            call.enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {

                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    final String res = response.body().string();
                    Log.i(TAG, "onResponse: "+res);
                    if (response.code() == 200) {
                        Log.i(TAG, "成功");
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public class ServerThread implements Runnable{
        private final static int UN_LOGIN=1;
        private final static int LOGIN=2;
        private Socket mSocket;
        private HeartBeatTimer timer;
        private int stateCode=UN_LOGIN;
        private PrintWriter printWriter;
        private BufferedReader reader;
        private InputStream inputStream;
        private OutputStream outputStream;
        private long lastRecvTime = 0;
        private String mAccount;
        private String deviceName;
        private String deviceID;
        public ServerThread(Socket socket) {
            this.mSocket=socket;
        }
        @Override
        public void run() {
            initClientSocket();
            String line;
            try {
                while ((line= reader.readLine())!=null){
                    onRecvMsg(line);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }finally {
                if(mSocket!=null){
                    closemSocket();
                }
            }
        }

        private  void closemSocket() {
            try {
                linkCounter=linkCounter-1;
                ServerApplication.getInstance().getLoginMap().remove(mAccount);
                Log.d(TAG, "close: 减少了一个连接，现有连接数 :" +linkCounter);
                if(timer!=null)timer.exit();
                timer = null;
                if(!mSocket.isClosed())mSocket.close();
                mSocket=null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void onRecvMsg(String line) {
            try {
                Log.d(TAG, "onRecvMsg: 接受到一条消息"+line);
                JSONObject jsonObject = new JSONObject(line);
                int request=jsonObject.optInt("request");
                if(stateCode==UN_LOGIN){
                    String account=jsonObject.optString("account");
                    String password = jsonObject.optString("password");
                    switch (request){
                        case Config.MsgCode.LOGIN_SUCCESS:
                            deviceName=jsonObject.optString("deviceName");
                            deviceID=jsonObject.optString("deviceID");
                            login(account,password);
                            break;
                        case Config.MsgCode.PING:
                            Log.d(TAG, "onRecvMsg: "+request);
                            lastRecvTime=System.currentTimeMillis();
                            break;
                        default:
                            try {
                                JSONObject jsonObject2send = new JSONObject();
                                jsonObject2send.put("answer", Config.MsgCode.DISPLAY_NSG);
                                jsonObject2send.put("msg","现在是未登录状态");
                                sendMsg(jsonObject2send.toString());
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            break;
                    }
                }else if(stateCode==LOGIN){
                    switch (request){
                        case Config.MsgCode.DISPLAY_NSG:
                            returnDisplay();
                            break;
                        case Config.MsgCode.SET_DISPLAY:
                            String newDisplay=jsonObject.optString("msg");
                            setDisplay(newDisplay);
                            break;
                        case Config.MsgCode.OFFLINE:
                            offline();
                            break;
                        case Config.MsgCode.PING:
                            Log.d(TAG, "onRecvMsg: "+request);
                            lastRecvTime=System.currentTimeMillis();
                            break;
                        case Config.MsgCode.CHANGE_PASSWORD:
                            String oldPassword=jsonObject.optString("old");
                            String newPassword=jsonObject.optString("new");
                            changePassword(oldPassword,newPassword);
                        default:
                            break;
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void changePassword(String oldPassword, String newPassword) {
            try {
                JSONObject jsonObject=new JSONObject();
                if(oldPassword.equals(ServerApplication.getInstance().getUserByUsername(mAccount).getPassword())){
                    ServerApplication.getInstance().getUserByUsername(mAccount).setPassword(newPassword);
                    ServerApplication.getInstance().updateUser(ServerApplication.getInstance().getUserByUsername(mAccount));
                    jsonObject.put("answer", Config.MsgCode.CHANGE_PASSWORD);
                }else {
                    jsonObject.put("answer", Config.MsgCode.CHANGE_FAIL);
                    jsonObject.put("msg","原密码错误，修改失败");
                }
                sendMsg(jsonObject.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void offline() {
            try {
                online(Config.OnlineType.OFFLINE,deviceName,deviceID,0);
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("answer", Config.MsgCode.OFFLINE);
                jsonObject.put("msg","用户主动下线");
                printWriter.println(jsonObject.toString());
                printWriter.flush();
                Log.d(TAG, "offline: "+jsonObject.toString());
                closemSocket();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void setDisplay(String newDisplay) {
            Log.d(TAG, "setDisplay: "+newDisplay);
            ServerApplication.getInstance().setTextToShow(newDisplay);
            try {
                JSONObject jsonObject=new JSONObject();
                jsonObject.put("answer", Config.MsgCode.SET_SUCCESS);
                sendMsg(jsonObject.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void returnDisplay() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("answer", Config.MsgCode.DISPLAY_NSG);
                jsonObject.put("msg",ServerApplication.getInstance().getTextToShow());
                sendMsg(jsonObject.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void login(String account,String password) {
            try {
                JSONObject jsonObject = new JSONObject();
                Log.d(TAG, "login: 现有连接："+ServerApplication.getInstance().getLoginMap().size());
                if(ServerApplication.getInstance().getLoginMap().containsKey(account)){
                    jsonObject.put("answer", Config.MsgCode.LOGIN_FAIL);
                    jsonObject.put("msg","帐号已经登录");
                    sendMsg(jsonObject.toString());
                    Log.d(TAG, "login: 帐号已经登录");
                    return;
                }else {
                    Log.d(TAG, "login: 帐号未登录");
                    List<User> users=ServerApplication.getInstance().getUserList();
                    for (User user:users){
                        if(user.getAccount().equals(account)){
                            if(user.getPassword().equals(password)){
                                stateCode=LOGIN;
                                jsonObject.put("answer", Config.MsgCode.LOGIN_SUCCESS);
                                sendMsg(jsonObject.toString());
                                ServerApplication.getInstance().getLoginMap().put(account,mSocket);
                                mAccount=account;
                                online(Config.OnlineType.ONLINE,deviceName,deviceID,0);
                                ServerApplication.getInstance().getUserByUsername(account).setDeviceName(deviceName);
                                ServerApplication.getInstance().getUserByUsername(account).setDeviceID(deviceID);
                                Log.d(TAG, "login: 成功添加新帐号，现有帐号："+ServerApplication.getInstance().getLoginMap().size());
                                return;
                            }else {
                                jsonObject.put("answer", Config.MsgCode.LOGIN_FAIL);
                                jsonObject.put("msg","帐号密码错误");
                                sendMsg(jsonObject.toString());
                                return;
                            }
                        }
                    }
                    Log.d(TAG, "login: 帐号不对");
                    jsonObject.put("answer", Config.MsgCode.LOGIN_FAIL);
                    jsonObject.put("msg","帐号不存在");
                    sendMsg(jsonObject.toString());
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void initClientSocket() {
            try {
                mSocket.setKeepAlive(true);
                mSocket.setReuseAddress(true);
                mSocket.setTcpNoDelay(true);
                inputStream = mSocket.getInputStream();
                reader = new BufferedReader(new InputStreamReader(inputStream));
                outputStream = mSocket.getOutputStream();
                printWriter = new PrintWriter(new OutputStreamWriter(outputStream));
                lastRecvTime=System.currentTimeMillis();
                startHeartBeatTimer();
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void startHeartBeatTimer() {
            if(timer==null){
                timer=new HeartBeatTimer();
            }
            timer.setOnScheduleListener(new HeartBeatTimer.OnScheduleListener() {
                @Override
                public void onSchedule() {
                    Log.d(TAG, "timer is onSchedule...");
                    long duration = System.currentTimeMillis()-lastRecvTime;
                    if(mSocket.isClosed())duration=TIME_OUT+1;
                    if(duration>TIME_OUT){
                        Log.d(TAG, "30s未收到心跳包，超时");
                        //断开这个socket的连接
                        try {
                            mSocket.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }else {
                        JSONObject jsonObject=new JSONObject();
                        try {
                            jsonObject.put("answer",Config.MsgCode.PING);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        sendMsg(jsonObject.toString());
                    }
                }
            });
            timer.startTimer(0,5*1000);
        }

        private void sendMsg(final String toString) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String toSend = toString;
                    printWriter.println(toSend);
                    printWriter.flush();
                    Log.d(TAG, "run: send success:" + toSend);
                }
            }).start();
        }
    }
}