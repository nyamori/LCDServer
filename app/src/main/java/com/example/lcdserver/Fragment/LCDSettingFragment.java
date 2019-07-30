package com.example.lcdserver.Fragment;


import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import com.example.lcdserver.R;
import com.example.lcdserver.ServerApplication;

/**
 * A simple {@link Fragment} subclass.
 */
public class LCDSettingFragment extends Fragment implements View.OnClickListener {
    private final static String TAG="LCDSettingFragment";

    private TextView direction;
    private TextView speedText;
    private int speed=50;
    private AlertDialog.Builder builder;
    private int checkIndex=0;

    public LCDSettingFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_lcdsetting, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        getView().findViewById(R.id.direction_setting).setOnClickListener(this);
        getView().findViewById(R.id.speed_setting).setOnClickListener(this);
        direction=(TextView)getView().findViewById(R.id.direction_text);
        speedText=(TextView)getView().findViewById(R.id.speed_value);
        if(ServerApplication.getInstance().isToLeft()){
            direction.setText("从右向左");
        }else {
            direction.setText("从左向右");
        }
        if(speedText!=null)speedText.setText(String.valueOf(speed));
        ServerApplication.getInstance().setSpeed(speed);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.direction_setting:
                settingDirection();
                break;
            case R.id.speed_setting:
                settingSpeed();
                break;
            default:
                break;
        }
    }

    private void settingSpeed() {
        builder=new AlertDialog.Builder(getActivity());
        View view=LayoutInflater.from(getActivity()).inflate(R.layout.speed_setting_dialogs,null);
        SeekBar seekBar=view.findViewById(R.id.speed_setting_bar);
        seekBar.setProgress(speed);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                speed=i;
                speedText.setText(String.valueOf(speed));
                ServerApplication.getInstance().setSpeed(speed);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        builder.setView(view);
        builder.create().show();

    }

    private void settingDirection() {
        final String[] items={"从右向左","从左向右"};
        builder=new AlertDialog.Builder(getActivity()).setSingleChoiceItems(items, checkIndex, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.d(TAG, "onClick: "+i);
                if(i==0){
                    ServerApplication.getInstance().setToLeft(true);
                    direction.setText(items[i]);
                }else if(i==1){
                    ServerApplication.getInstance().setToLeft(false);
                    direction.setText(items[i]);
                }
                checkIndex=i;
                dialogInterface.dismiss();
            }
        });
        builder.create().show();
    }


}
