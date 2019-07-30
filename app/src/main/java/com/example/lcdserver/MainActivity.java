package com.example.lcdserver;


import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.example.lcdserver.Fragment.LCDNowDisplayFragment;
import com.example.lcdserver.Fragment.LCDSettingFragment;
import com.example.lcdserver.Fragment.LinkManagerFragment;
import com.example.lcdserver.Fragment.UserManagerFragment;
import com.example.lcdserver.Listener.OnUIChangeListener;

import interfaces.heweather.com.interfacesmodule.view.HeConfig;

public class MainActivity extends AppCompatActivity {
    private final static String TAG="MainActivity";

    private DrawerLayout mDrawerLayout;

    private NetService.MyBinder myBinder;
    private NetService netService;
    private OnUIChangeListener onUIChangeListener;
    private ServiceConnection connection=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            myBinder=(NetService.MyBinder)iBinder;
            netService=myBinder.getService();
            netService.setOnUIChangeListener(onUIChangeListener);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: 启动了一次");
        setContentView(R.layout.activity_main);
        //初始化和风天气sdk
        HeConfig.init("HE1907151441281567","75d0d7d2ebb94051a4723adff487c148");
        HeConfig.switchToFreeServerNode();

        initListener();
        //处理toolbar和drawerLayout
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mDrawerLayout =(DrawerLayout)findViewById(R.id.drawer_layout);
        NavigationView navigationView =(NavigationView)findViewById(R.id.nav_view);

        Log.d(TAG, "onCreate: 开始设置action bar");
        final ActionBar actionBar = getSupportActionBar();
        if(actionBar!=null){
            Log.d(TAG, "onCreate: action bar 获取成功");
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
        }

        navigationView.setCheckedItem(R.id.LCD_now_display);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {

                if(ServerApplication.getInstance().getCounter()<ServerApplication.getInstance().getCounterMax()){
                    Toast.makeText(MainActivity.this,"尚未完成初始化",Toast.LENGTH_SHORT).show();
                }else {
                    switch (menuItem.getItemId()){
                        case R.id.LCD_setting:
                            newFragment(new LCDSettingFragment(),"lcd_setting");
                            actionBar.setTitle("LCD设置");
                            break;
                        case R.id.user_manager:
                            newFragment(new UserManagerFragment(),"user_manager");
                            actionBar.setTitle("用户管理");
                            break;
                        case R.id.link_manager:
                            newFragment(new LinkManagerFragment(),"link_manager");
                            actionBar.setTitle("连接管理");
                            break;
                        case R.id.LCD_now_display:
                            newFragment(new LCDNowDisplayFragment(),"lcd_now_display");
                            actionBar.setTitle("正在显示");
                        default:
                            break;
                    }
                }
                mDrawerLayout.closeDrawers();
                return true;
            }
        });

        Intent intent = new Intent();
        intent.setClass(MainActivity.this,NetService.class);
        bindService(intent,connection,BIND_AUTO_CREATE);
        ServerApplication.getInstance().setOnUIChangeListener(onUIChangeListener);
    }

    private void initListener() {
        onUIChangeListener=new OnUIChangeListener() {
            @Override
            public void onChange(int msgCode, String msg) {
                LCDNowDisplayFragment lcdNowDisplayFragment=(LCDNowDisplayFragment) getSupportFragmentManager().findFragmentByTag("lcd_now_display");
                switch (msgCode){
                    case Config.MsgCode.WEATHER_PRE:
                        findViewById(R.id.now_init).setVisibility(View.GONE);
                        Toast.makeText(MainActivity.this,"初始化完成",Toast.LENGTH_SHORT).show();
                        newFragment(new LCDNowDisplayFragment(),"lcd_now_display");
                        break;
                    case Config.MsgCode.NEW_TIME:
                        if(lcdNowDisplayFragment!=null) lcdNowDisplayFragment.updateTime();
                        break;
                    case Config.MsgCode.NEW_CONTENT:
                        if(lcdNowDisplayFragment!=null) lcdNowDisplayFragment.updateContent();
                    default:
                        break;
                }
            }

            @Override
            public void onError(int errorCode) {

            }
        };
    }

    private void newFragment(Fragment fragment,String Tag) {
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.frag_container,fragment,Tag);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.toolbar,menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        unbindService(connection);
        super.onDestroy();
    }
}
