package com.example.lcdserver;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.example.lcdserver.Listener.OnUIChangeListener;
import com.example.lcdserver.socket.HeartBeatTimer;
import com.example.lcdserver.socket.SocketManager;
import com.example.lcdserver.socket.TcpServerSocket;
import com.friendlyarm.FriendlyThings.BoardType;
import com.friendlyarm.FriendlyThings.FileCtlEnum;
import com.friendlyarm.FriendlyThings.HardwareControler;
import com.friendlyarm.FriendlyThings.PCF8574;
import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import interfaces.heweather.com.interfacesmodule.bean.Code;
import interfaces.heweather.com.interfacesmodule.bean.Lang;
import interfaces.heweather.com.interfacesmodule.bean.Unit;
import interfaces.heweather.com.interfacesmodule.bean.air.now.AirNow;
import interfaces.heweather.com.interfacesmodule.bean.air.now.AirNowCity;
import interfaces.heweather.com.interfacesmodule.bean.basic.Basic;
import interfaces.heweather.com.interfacesmodule.bean.weather.now.Now;
import interfaces.heweather.com.interfacesmodule.bean.weather.now.NowBase;
import interfaces.heweather.com.interfacesmodule.view.HeWeather;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Util;
import okio.Utf8;


public class NetService extends Service {
    private final static String TAG="NetService";
    final int MAX_LINE_SIZE = 16;
    private Context mContext;

    private String netIp;

    private double latitude;
    private double longitude;
    private String district;
    private String city;
    private long lastTime;

    private HeWeather.OnResultWeatherNowBeanListener listener;
    private HeWeather.OnResultAirNowBeansListener airListener;

    private HeartBeatTimer infoGetTimer;
    private HeartBeatTimer updateTimeTimer;
    private HeartBeatTimer webContextCheckTimer;

    private SocketManager socketManager;

    private List<OnUIChangeListener> onUIChangeListenerList;
    private int devFD = -1;
    private boolean lcd1602Inited = false;
    private boolean isThreadRun=true;

    public NetService() {
    }

    /**
     * 与服务绑定用的代码段
     * @param intent
     * @return
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mybinder;
    }
    private MyBinder mybinder = new MyBinder();
    public class MyBinder extends Binder {
        public NetService getService(){
            return NetService.this;
        }
    }
    private Handler handler=new Handler(){
        @Override
        public void handleMessage(@org.jetbrains.annotations.NotNull Message msg) {
            switch (msg.what){
                case Config.MsgCode.WEATHER_PRE:
                    if(updateTimeTimer!=null)updateTimeTimer.startTimer(10*1000,5*1000);
                    if(infoGetTimer!=null)infoGetTimer.exit();
                    notifyOnChange(msg.what,"");
                    break;
                case Config.MsgCode.NEW_TIME:
                    notifyOnChange(msg.what,"");
                    break;
                case Config.MsgCode.LCD_THREAD_CLOSE:
                    isThreadRun=true;
                    startWriteEEPROMThread(ServerApplication.getInstance().getTextToShow());
                default:
                    break;
            }
        }
    };

    public void setOnUIChangeListener(OnUIChangeListener listener){
        if(onUIChangeListenerList==null)onUIChangeListenerList=new ArrayList<>();
        onUIChangeListenerList.add(listener);
    }

    private void notifyOnChange(int msgCode,String msg){
        if(onUIChangeListenerList!=null){
            for(OnUIChangeListener listener:onUIChangeListenerList){
                listener.onChange(msgCode,msg);
            }
        }
    }

    @Override
    public void onDestroy() {
        TcpServerSocket.online(Config.OnlineType.OFFLINE,ServerApplication.getDeviceName(),ServerApplication.getMacFromHardware(),1);
        if (devFD >= 0) {
            HardwareControler.close(devFD);
            devFD = -1;
        }
        webContextCheckTimer.exit();
        updateTimeTimer.exit();
        super.onDestroy();
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate: 服务启动了一次");
        super.onCreate();

        mContext=getApplicationContext();
        Calendar c = Calendar.getInstance();
        lastTime = c.get(Calendar.MINUTE);
        initListener();//设置了和风天气sdk的相关listener
        initLocation();//利用了百度sdk进行了定位，由于获取ip功能获取的ip无法直接成为和风天气sdk的参数，仅在百度定位返回为null时启动
        initTimer();//设定两个定时器，一个是检测启动fragment需要的数据是否齐全，一个是用于更新fragment时间的定时器
        isThreadRun=true;
        socketManager=SocketManager.getInstance(mContext);
        socketManager.startUdpConnection();
        ServerApplication.getInstance().setOnUIChangeListener(new OnUIChangeListener() {
            @Override
            public void onChange(int msgCode, String msg) {
                //监听字符串变化
                if(msgCode== Config.MsgCode.NEW_CONTENT){
                    isThreadRun=false;
                    sendContextToWeb(ServerApplication.getInstance().getTextToShow());
                }
            }

            @Override
            public void onError(int errorCode) {

            }
        });

        String i2cDevName = "/dev/i2c-0";
        int boardType = HardwareControler.getBoardType();
        if (boardType == BoardType.NanoPC_T4 || boardType == BoardType.NanoPi_M4 || boardType == BoardType.NanoPi_NEO4 ) {
            i2cDevName = "/dev/i2c-2";
        }
        devFD = HardwareControler.open(i2cDevName, FileCtlEnum.O_RDWR);
        if(devFD<0){

        }else {
            if (HardwareControler.setI2CSlave(devFD, 0x27) < 0) {
                //发个广播
            } else {
                startWriteEEPROMThread(ServerApplication.getInstance().getTextToShow());
            }
        }
    }

    private void sendContextToWeb(String textToShow) {
        try {
            String url="http://192.168.112.66:8080/V1.0/getcontext";
            SimpleDateFormat sdf=new SimpleDateFormat("yyyy-MM-dd");
            String time= sdf.format( new Date());
            String deviceName=ServerApplication.getDeviceName();
            String deviceID = ServerApplication.getMacFromHardware();
            JSONObject jsonObject=new JSONObject();
            jsonObject.put("time",time);
            jsonObject.put("deviceName",deviceName);
            jsonObject.put("deviceID",deviceID);
            jsonObject.put("context",textToShow);
            Log.i(TAG, "sendContextToWeb: 发送的消息"+jsonObject.toString());
            OkHttpClient client=new OkHttpClient();
            //RequestBody requestBody= RequestBody.create(JSON,jsonObject.toString());
            RequestBody requestBody=new FormBody.Builder()
                    .add("time",time)
                    .add("deviceName",deviceName)
                    .add("deviceID",deviceID)
                    .add("context",URLEncoder.encode(textToShow,"UTF-8"))
                    .build();
            Log.i(TAG, "sendContextToWeb: text "+new String(textToShow.getBytes(),"UTF-8"));
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
                    Log.i(TAG, "onResponse:context "+res);
                    if (response.code() == 200) {
                        Log.i(TAG, "成功");
                    }
                }
            });
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
    private String getContextFromWeb(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String url="http://192.168.112.66:8080/V1.0/postcontext";
                    OkHttpClient client = new OkHttpClient();
                    StringBuilder stringBuilder=new StringBuilder(url);
                    stringBuilder.append("?deviceID="+ServerApplication.getMacFromHardware());
                    Request request=new Request.Builder().url(stringBuilder.toString()).build();
                    Response response=client.newCall(request).execute();
                    if(response.code()==200){
                        String responseString=response.body().string();
                        Log.i(TAG, "getContextFromWeb: "+responseString);
                        JSONObject data=new JSONObject(responseString);
                        String status=data.optString("status");
                        if((data=data.optJSONObject("data"))!=null){
                            String context=data.optString("context");
                            context=URLDecoder.decode(context,"UTF-8");
                            Log.i(TAG, "run: context from data :  "+context);
                            if(context.equals(ServerApplication.getInstance().getTextToShow())){
                                Log.i(TAG, "run: 没有修改广告");
                            }else {
                                Log.i(TAG, "run: 广告已经修改为："+context);
                                ServerApplication.getInstance().setTextToShow(context);
                            }
                        }else {
                            Log.i(TAG, "run: status:"+status);
                            sendContextToWeb(ServerApplication.getInstance().getTextToShow());
                        }
                    }else {
                        Log.i(TAG, "run: wrong code");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return "star-net";
    }

    private void initTimer() {
        infoGetTimer=new HeartBeatTimer();
        updateTimeTimer = new HeartBeatTimer();
        webContextCheckTimer=new HeartBeatTimer();

        infoGetTimer.setOnScheduleListener(new HeartBeatTimer.OnScheduleListener() {
            @Override
            public void onSchedule() {
                Log.d(TAG, "onSchedule: 检查获取的信息");
                if(ServerApplication.getInstance().getCounter()>=ServerApplication.getInstance().getCounterMax()){
                    webContextCheckTimer.startTimer(0,15*1000);
                    Message msg=handler.obtainMessage(Config.MsgCode.WEATHER_PRE);
                    Log.d(TAG, "onSchedule: "+msg.what);
                    handler.sendMessage(msg);
                }
            }
        });

        updateTimeTimer.setOnScheduleListener(new HeartBeatTimer.OnScheduleListener() {
            @Override
            public void onSchedule() {
                Calendar c = Calendar.getInstance();
                int duration = c.get(Calendar.MINUTE);
                if(duration!=lastTime){
                    Log.d(TAG, "onSchedule: updateTime");
                    Message msg = handler.obtainMessage(Config.MsgCode.NEW_TIME);
                    handler.sendMessage(msg);
                    lastTime=duration;
                }
            }
        });

        webContextCheckTimer.setOnScheduleListener(new HeartBeatTimer.OnScheduleListener() {
            @Override
            public void onSchedule() {
                getContextFromWeb();
            }
        });
    }

    private void initListener() {
        listener=new HeWeather.OnResultWeatherNowBeanListener() {
            @Override
            public void onError(Throwable throwable) {
                Log.d(TAG, "onError: "+throwable);
                ServerApplication serverApplication=ServerApplication.getInstance();
                serverApplication.setTemperature(" ");
                serverApplication.setWeather("未获取");
                serverApplication.setHumidity(" ");
            }

            @Override
            public void onSuccess(Now now) {
                Log.d(TAG, " Weather Now onSuccess: " + new Gson().toJson(now));
                if ( Code.OK.getCode().equalsIgnoreCase(now.getStatus()) ){
                    //此时返回数据
                    NowBase data = now.getNow();
                    Basic basic=now.getBasic();

                    ServerApplication serverApplication=ServerApplication.getInstance();
                    if(city==null){
                        serverApplication.setLocation(basic.getLocation());
                        serverApplication.setParentCity("");
                    }
                    serverApplication.setTemperature(data.getTmp());
                    serverApplication.setWeather(data.getCond_txt());
                    serverApplication.setHumidity(data.getHum());
                } else {
                    //在此查看返回数据失败的原因
                    String status = now.getStatus();
                    Code code = Code.toEnum(status);
                    Log.i(TAG, "failed code: " + code);
                    ServerApplication serverApplication=ServerApplication.getInstance();
                    serverApplication.setTemperature(" ");
                    serverApplication.setWeather("未获取");
                    serverApplication.setHumidity(" ");
                    if(city==null){
                        serverApplication.setLocation("定位失败");
                        serverApplication.setParentCity("");
                    }
                }
            }
        };

        airListener=new HeWeather.OnResultAirNowBeansListener() {
            @Override
            public void onError(Throwable throwable) {
                Log.d(TAG, "onError: "+throwable);
                ServerApplication.getInstance().setQIty("不明");
                ServerApplication.getInstance().setAqi("0");
            }

            @Override
            public void onSuccess(AirNow airNow) {
                Log.d(TAG, " Air Now onSuccess: " + new Gson().toJson(airNow));
                if ( Code.OK.getCode().equalsIgnoreCase(airNow.getStatus()) ){
                    //此时返回数据
                    AirNowCity data = airNow.getAir_now_city();
                    ServerApplication.getInstance().setQIty(data.getQlty());
                    ServerApplication.getInstance().setAqi(data.getAqi());
                } else {
                    //在此查看返回数据失败的原因
                    String status = airNow.getStatus();
                    Code code = Code.toEnum(status);
                    Log.i(TAG, "failed code: " + code);
                    ServerApplication.getInstance().setQIty("不明");
                    ServerApplication.getInstance().setAqi("0");
                }
            }
        };
    }

    private void initLocation() {
        LocationClient mLocationClint=new LocationClient(getApplicationContext());
        LocationClientOption option=new LocationClientOption();
        option.setLocationMode(LocationClientOption.LocationMode.Battery_Saving);
        option.setIsNeedAddress(true);
        mLocationClint.setLocOption(option);
        mLocationClint.registerLocationListener(new BDAbstractLocationListener() {
            @Override
            public void onReceiveLocation(BDLocation location) {
                Log.d(TAG, "onReceiveLocation: 收到了信息");
                latitude = location.getLatitude();    //获取纬度信息
                longitude = location.getLongitude();    //获取经度信息
                district = location.getDistrict();    //获取区县
                city = location.getCity();    //获取城市
                //备用位置数据
                String addr = location.getAddrStr();    //获取详细地址信息
                String country = location.getCountry();    //获取国家
                String province = location.getProvince();    //获取省份
                String street = location.getStreet();    //获取街道信息
                infoGetTimer.startTimer(0,2000);
                if(city==null){
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            netIp=ServerApplication.GetNetIp();//这个ip地址字符串最后有一个空位
                            if(netIp!=null){
                                netIp=netIp.substring(0,netIp.length()-1);//计划设置timer将两个数据都收集后开始查询天气，暂时未实现。
                                HeWeather.getWeatherNow(mContext,netIp, Lang.CHINESE_SIMPLIFIED, Unit.METRIC, listener);
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                HeWeather.getAirNow(mContext,netIp, Lang.CHINESE_SIMPLIFIED, Unit.METRIC, airListener);
                            }else {
                                infoGetTimer.exit();
                            }
                        }
                    }).start();
                }else {
                    ServerApplication serverApplication=ServerApplication.getInstance();
                    serverApplication.setLocation(district);
                    serverApplication.setParentCity(city);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            HeWeather.getAirNow(mContext,city, Lang.CHINESE_SIMPLIFIED, Unit.METRIC,airListener);
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            HeWeather.getWeatherNow(mContext, longitude+","+latitude, Lang.CHINESE_SIMPLIFIED, Unit.METRIC, listener);
                        }
                    }).start();
                    Log.d(TAG, "onReceiveLocation: " +
                            "\n经度 " +longitude+
                            "\n纬度 " +latitude+
                            "\n详细地址信息 "+addr+
                            "\n国家 "+country+
                            "\n省份 " +province+
                            "\n城市 "+city+
                            "\n区县 "+district+
                            "\n街道信息 "+street);
                }
            }
        });
        mLocationClint.start();
        Log.d(TAG, "initLocation: 开始获取信息");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    private int LCD1602Init() throws InterruptedException {
        Thread.sleep(0,1000*15);
        if (PCF8574.writeCmd4(devFD, (byte)(0x03 << 4)) == -1) {
            return -1;
        }
        Thread.sleep(0,100*41);
        if (PCF8574.writeCmd4(devFD, (byte)(0x03 << 4)) == -1) {
            return -1;
        }
        Thread.sleep(0,100);
        if (PCF8574.writeCmd4(devFD, (byte)(0x03 << 4)) == -1) {
            return -1;
        }
        if (PCF8574.writeCmd4(devFD, (byte)(0x02 << 4)) == -1) {
            return -1;
        }
        if (PCF8574.writeCmd8(devFD, (byte)(0x28)) == -1) {
            return -1;
        }
        if (PCF8574.writeCmd8(devFD, (byte)(0x0c)) == -1) {
            return -1;
        }
        Thread.sleep(0,2000);
        if (PCF8574.writeCmd8(devFD, (byte)(0x06)) == -1) {
            return -1;
        }
        if (PCF8574.writeCmd8(devFD, (byte)(0x02)) == -1) {
            return -1;
        }
        Thread.sleep(0,2000);
        return 0;
    }

    private int LCD1602DispStr(byte x, byte y, String str) throws InterruptedException {
        byte addr;
        addr = (byte)(((y + 2) * 0x40) + x);

        if (PCF8574.writeCmd8(devFD, addr) == -1) {
            return -1;
        }
        byte[] strBytes = str.getBytes();
        byte b;

        for (int i = 0; i < strBytes.length && i<MAX_LINE_SIZE; i++) {
            b = strBytes[i];
            if (PCF8574.writeData8(devFD, b) == -1) {
                return -1;
            }
        }
        return 0;
    }

    private int LCD1602DispLines(String line1, String line2) throws InterruptedException {
        int ret = LCD1602DispStr((byte)0, (byte)0, line1);
        if (ret != -1 && line2.length() > 0) {
            ret = LCD1602DispStr((byte)0, (byte)1, line2);
        }
        return ret;
    }

    private int LCD1602Clear() throws InterruptedException {
        if (PCF8574.writeCmd8(devFD, (byte)0x01) == -1) {
            return -1;
        }
        return 0;
    }

    private void startWriteEEPROMThread(final String displayText) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!lcd1602Inited) {
                    try {
                        if (LCD1602Init() == 0) {
                            lcd1602Inited = true;
                        }
                    } catch (Exception e) {

                        return;
                    }
                }

                if (!lcd1602Inited) {

                    return;
                }

                try {
                    LCD1602Clear();
                    Thread.sleep(100, 0);
                    char[] display=null;
                    if(displayText.length()>MAX_LINE_SIZE*2){
                        display=new char[displayText.length()+8];
                        strToByte(displayText,display,display.length);
                    }else {
                        display=new char[MAX_LINE_SIZE*2];
                        strToByte(displayText,display,MAX_LINE_SIZE*2);
                    }

                    while (isThreadRun){
                        String line1 = "";
                        String line2 = "";
                        for (int i = 0; i < display.length; i++) {
                            if (line1.length() >= MAX_LINE_SIZE) {
                                if (line2.length() >= MAX_LINE_SIZE) {
                                    break;
                                } else {
                                    line2 = line2 + display[i];
                                }
                            } else {
                                line1 = line1 + display[i];
                            }
                        }
                        if (LCD1602DispLines(line1, line2) == -1) {

                            return;
                        }
                        if(ServerApplication.getInstance().isToLeft()){
                            leftMove(display);
                        }else {
                            rightMove(display);
                        }
                        Thread.sleep(ServerApplication.getInstance().getSpeed());
                    }
                } catch (Exception e) {

                    return;
                }finally {
                    Log.d(TAG, "run: 线程结束");
                    Message message= new Message();
                    message.what= Config.MsgCode.LCD_THREAD_CLOSE;
                    handler.sendMessage(message);
                }
            }
        }).start();
    }

    private void strToByte(String displayText, char[] display, int length) {
        for (int i=0;i<length;i++){
            if(displayText.length()>i){
                display[i]=displayText.charAt(i);
            }else {
                display[i]=' ';
            }
        }
        Log.d(TAG, "strToByte: 拆解结束"+ Arrays.toString(display) +"|end");
    }

    private void leftMove(char[] display){
        char temp=display[0];
        for(int i=0;i<display.length-1;i++){
            display[i]=display[i+1];
        }
        display[display.length-1]=temp;
       // Log.d(TAG, "strToByte: 左移"+ Arrays.toString(display) +"|end");
    }

    private void rightMove(char[] display){
        char temp=display[display.length-1];
        for(int i=display.length-1;i>0;i--){
            display[i]=display[i-1];
        }
        display[0]=temp;
     // Log.d(TAG, "strToByte: 右移"+ Arrays.toString(display) +"|end");
    }
}
