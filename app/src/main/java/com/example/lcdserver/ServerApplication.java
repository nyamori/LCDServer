package com.example.lcdserver;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.util.Log;

import com.example.lcdserver.Bean.User;
import com.example.lcdserver.Listener.OnUIChangeListener;
import com.example.lcdserver.socket.TcpServerSocket;

import org.json.JSONException;
import org.json.JSONObject;
import org.litepal.LitePal;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ServerApplication extends Application {
    private static ServerApplication instance;
    private final static String TAG="ServerApplication";
    private final static int CounterMax=7;
    private final static long SPEED_MIN=5000;
    private final static long SPEED_MAX=10;
    private String location;
    private String parentCity;
    private String temperature;
    private String humidity;
    private String weather;
    //专门查空气质量
    private String QIty;
    private String aqi;

    private int counter=0;

    private String textToShow;
    private boolean toLeft=true;
    private long speed=(101-51)/100*(SPEED_MAX+SPEED_MIN);


    private List<User>userList;

    private HashMap<String, Socket> loginMap;

    private List<OnUIChangeListener> listenerList;

    private SQLiteDatabase database=null;


    @Override
    public void onCreate() {
        super.onCreate();
        LitePal.initialize(this);
        database=LitePal.getDatabase();
        instance=this;
        listenerList=new ArrayList<>();
        userList=new ArrayList<>();
        loginMap = new HashMap<>();
        userList= LitePal.findAll(User.class);
        textToShow="star-net";
    }

    public static ServerApplication getInstance() {
        return instance;
    }

    public List<User> getUserList(){
        return userList;
    }

    public User getUserByUsername(String username){
        User user=null;
        for (User user1:userList){
            if(user1.getAccount().equals(username))user=user1;
        }
        return user;
    }

    public void setOnUIChangeListener(OnUIChangeListener listener){listenerList.add(listener);}


    /**
     * 获取外网IP地址
     * @return
     */
    public static String GetNetIp(){
        String IP;
        try {
            Log.d(TAG, "GetNetIp: start try");
            OkHttpClient client=new OkHttpClient();
            Request request=new Request.Builder()
                    .url("https://ifconfig.co/ip")
                    .build();
            Response response = client.newCall(request).execute();
            String responseData=response.body().string();
            Log.d(TAG, "GetNetIp:"+responseData);
            IP = responseData;
        } catch (Exception e) {
            IP = null;
            Log.e("提示", "获取IP地址时出现异常，异常信息是：" + e.toString());
        }
        return IP;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        if(this.location==null)counter=counter+1;
        Log.d(TAG, "setLocation: location set"+counter);
        this.location = location;
    }

    public String getParentCity() {
        return parentCity;
    }

    public void setParentCity(String parentCity) {
        if(this.parentCity==null)counter=counter+1;
        Log.d(TAG, "setLocation: city set"+counter);
        this.parentCity = parentCity;
    }

    public String getTemperature() {
        return temperature;
    }

    public void setTemperature(String temperature) {
        if(this.temperature==null)counter=counter+1;
        Log.d(TAG, "setLocation: temp set"+counter);
        this.temperature = temperature;
    }

    public String getHumidity() {
        return humidity;
    }

    public void setHumidity(String humidity) {
        if(this.humidity==null)counter=counter+1;
        Log.d(TAG, "setLocation: humidity set"+counter);
        this.humidity = humidity;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        if(this.weather==null)counter=counter+1;
        Log.d(TAG, "setLocation: weather set"+counter);
        this.weather = weather;
    }

    public String getQIty() {
        return QIty;
    }

    public void setQIty(String QIty) {
        if(this.QIty==null)counter=counter+1;
        Log.d(TAG, "setLocation: QIty set"+counter);
        this.QIty = QIty;
    }

    public boolean resetState() {
        if (counter >= CounterMax) {
            location = null;
            parentCity = null;
            weather = null;
            humidity = null;
            temperature = null;
            QIty = null;
            counter = 0;
            return true;
        }
        return false;
    }

    public int getCounter(){return counter;}

    /**
     * 获取IP地址
     * @return
     * @throws SocketException
     */
    public static String getLocalIPAddress() throws SocketException {
        for(Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();){
            NetworkInterface intf = en.nextElement();
            for(Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();){
                InetAddress inetAddress = enumIpAddr.nextElement();
                if(!inetAddress.isLoopbackAddress() && (inetAddress instanceof Inet4Address)){
                    return inetAddress.getHostAddress().toString();
                }
            }
        }
        return null;
    }

    public static String getBroadcast()  {
        System.setProperty("java.net.preferIPv4Stack", "true");
        try {
            for (Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces(); niEnum.hasMoreElements();) {
                NetworkInterface ni = niEnum.nextElement();
                if (!ni.isLoopback()) {
                    for (InterfaceAddress interfaceAddress : ni.getInterfaceAddresses()) {
                        if (interfaceAddress.getBroadcast() != null) {
                            return interfaceAddress.getBroadcast().toString().substring(1);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getWeek(String time) {
        String Week = "";
        Calendar c = Calendar.getInstance();
        int wek=c.get(Calendar.DAY_OF_WEEK);

        if (wek == 1) {
            Week += "星期日";
        }
        if (wek == 2) {
            Week += "星期一";
        }
        if (wek == 3) {
            Week += "星期二";
        }
        if (wek == 4) {
            Week += "星期三";
        }
        if (wek == 5) {
            Week += "星期四";
        }
        if (wek == 6) {
            Week += "星期五";
        }
        if (wek == 7) {
            Week += "星期六";
        }
        return Week;
    }

    public String getAqi() {
        return aqi;
    }

    public void setAqi(String aqi) {
        if(this.aqi==null)counter=counter+1;
        this.aqi = aqi;
    }

    public int getCounterMax(){return CounterMax; }

    public HashMap<String, Socket> getLoginMap() {
        return loginMap;
    }

    public String getTextToShow() {
        return textToShow;
    }

    public void setTextToShow(String textToShow) {
        this.textToShow = textToShow;
        Log.d(TAG, "setTextToShow: "+this.textToShow);
        for(OnUIChangeListener listener:listenerList){
            listener.onChange(Config.MsgCode.NEW_CONTENT,textToShow);
        }
    }

    public boolean updateUser(User user) {
        boolean result=false;
        if(!user.isSaved())userList.add(user);
        result=user.save();
        return result;
    }

    public void deleteUser(User user){
        final Socket socket=loginMap.get(user.getAccount());
        final String deviceName=user.getDeviceName();
        final String deviceID=user.getDeviceID();
        user.delete();
        userList.remove(user);
        //如果有连接应该把连接断掉
        if(socket!=null){
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try{
                        TcpServerSocket.online(Config.OnlineType.OFFLINE,deviceName,deviceID,0);
                        PrintWriter printWriter=new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                        JSONObject jsonObject=new JSONObject();
                        jsonObject.put("answer", Config.MsgCode.OFFLINE);
                        jsonObject.put("msg","服务器删除了用户");
                        printWriter.println(jsonObject.toString());
                        printWriter.flush();
                        socket.close();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();
        }}

    public boolean isToLeft() {
        return toLeft;
    }

    public void setToLeft(boolean toLeft) {
        this.toLeft = toLeft;
    }

    public long getSpeed() {
        return speed;
    }

    public void setSpeed(int speed) {
        float newSpeed=(100-(float)speed)/100*(SPEED_MIN+SPEED_MAX);
        Log.d(TAG, "setSpeed: new speed is "+newSpeed);
        this.speed = (long)newSpeed;
    }

    public static String getDeviceName(){
        String deviceName="";
        deviceName= Build.DEVICE;
        Log.i(TAG, "getDeviceName: "+deviceName);
        return deviceName;
    }

    public static String getMacFromHardware() {
        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02X:", b));
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "02:00:00:00:00:00";
    }
}
