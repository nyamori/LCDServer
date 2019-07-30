package com.example.lcdserver.Fragment;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.example.lcdserver.R;
import com.example.lcdserver.ServerApplication;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A simple {@link Fragment} subclass.
 */
public class LCDNowDisplayFragment extends Fragment {

    private final static String TAG="LCDNowDisplayFragment";

    private String time;
    private String date;
    private String week;
    private String weather;
    private String location;
    private String temperature;

    private TextView timeView;
    private TextView contentDiaplay;


    public LCDNowDisplayFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_lcdnow_display, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initData();
        initView();
    }

    private void initView() {
        contentDiaplay=getView().findViewById(R.id.content_display);
        contentDiaplay.setText(ServerApplication.getInstance().getTextToShow());
        timeView=getView().findViewById(R.id.time_display);
        timeView.setText(time);
        TextView textView=getView().findViewById(R.id.date_display);
        textView.setText(date);
        textView=getView().findViewById(R.id.week_display);
        textView.setText(week);
        textView=getView().findViewById(R.id.temperature_display);
        textView.setText(temperature+"℃");
        textView=getView().findViewById(R.id.weather_display);
        textView.setText(weather);
        textView=getView().findViewById(R.id.location_display);
        textView.setText(location);
    }

    public void updateTime(){
        SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm");

        time=timeFormat.format(System.currentTimeMillis());
        timeView.setText(time);
    }

    private void initData() {
        ServerApplication serverApplication=ServerApplication.getInstance();
        location=serverApplication.getLocation()+"  "+serverApplication.getParentCity();
        temperature=serverApplication.getTemperature();
        weather=serverApplication.getWeather()+" "+serverApplication.getHumidity()+"% "+serverApplication.getAqi()+serverApplication.getQIty();

        SimpleDateFormat dateFormat=new SimpleDateFormat("yyyy年MM月dd日");
        SimpleDateFormat timeFormat=new SimpleDateFormat("HH:mm");

        time=timeFormat.format(System.currentTimeMillis());
        date=dateFormat.format(System.currentTimeMillis());
        week=ServerApplication.getWeek(new Date().toString());

        Log.d(TAG, "onActivityCreated: "+
                "location"+location+
                "temp"+temperature+
                "weather"+weather+
                "time"+time+
                "date"+date+
                "week"+week);
    }

    public void updateContent() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String content=ServerApplication.getInstance().getTextToShow();
                contentDiaplay.setText(content);
                contentDiaplay.setTextColor(getResources().getColor(R.color.blue));
            }
        });
    }
}
