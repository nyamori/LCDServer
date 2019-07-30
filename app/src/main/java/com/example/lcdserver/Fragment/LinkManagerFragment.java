package com.example.lcdserver.Fragment;


import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.lcdserver.Bean.SwipeLinkBean;
import com.example.lcdserver.Config;
import com.example.lcdserver.R;
import com.example.lcdserver.ServerApplication;
import com.example.lcdserver.socket.TcpServerSocket;
import com.mcxtzhang.commonadapter.lvgv.CommonAdapter;
import com.mcxtzhang.commonadapter.lvgv.ViewHolder;
import com.mcxtzhang.swipemenulib.SwipeMenuLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * A simple {@link Fragment} subclass.
 */
public class LinkManagerFragment extends Fragment {
    private final static String TAG="LinkManagerFragment";

    private HashMap<String, Socket> mLinkMap;
    private List<SwipeLinkBean> linkBeanList;
    private ListView mListView;

    public LinkManagerFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_link_manager, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        TextView textView=getView().findViewById(R.id.link_number);
        textView.setText(String.valueOf(ServerApplication.getInstance().getLoginMap().size()));
        mListView=getView().findViewById(R.id.link_list);
        initLinkData();
        mListView.setAdapter(new CommonAdapter<SwipeLinkBean>(getActivity(),linkBeanList,R.layout.link_item) {

            @Override
            public void convert(final ViewHolder viewHolder, SwipeLinkBean swipeLinkBean, final int position) {
                final String username=swipeLinkBean.getUserName();
                final Socket socket=swipeLinkBean.getSocket();
                viewHolder.setText(R.id.user_name_of_link,"用户名："+username);
                viewHolder.setText(R.id.user_ip_of_link,"ip:"+socket.getInetAddress().toString());
                viewHolder.setOnClickListener(R.id.delete_link, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Toast.makeText(getActivity(), "删除:" + (position+1), Toast.LENGTH_SHORT).show();
                        //在ListView里，点击侧滑菜单上的选项时，如果想让擦花菜单同时关闭，调用这句话
                        ((SwipeMenuLayout) viewHolder.getConvertView()).quickClose();
                        //断开连接
                        deleteLink(username,socket);
                        mDatas.remove(position);
                        notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private void deleteLink(final String username, final Socket socket) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                ServerApplication.getInstance().getLoginMap().remove(username);
                TextView textView=getView().findViewById(R.id.link_number);
                textView.setText(String.valueOf(ServerApplication.getInstance().getLoginMap().size()));
                try {
                    String deviceName=ServerApplication.getInstance().getUserByUsername(username).getDeviceName();
                    String deviceID=ServerApplication.getInstance().getUserByUsername(username).getDeviceID();
                    TcpServerSocket.online(Config.OnlineType.OFFLINE,deviceName,deviceID,0);
                    PrintWriter printWriter=new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                    JSONObject jsonObject=new JSONObject();
                    jsonObject.put("answer", Config.MsgCode.OFFLINE);
                    jsonObject.put("msg","服务器断开了连接");
                    printWriter.println(jsonObject.toString());
                    printWriter.flush();
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void initLinkData() {
        mLinkMap=ServerApplication.getInstance().getLoginMap();
        linkBeanList=new ArrayList<>();
        Set<String> keys = mLinkMap.keySet();   //此行可省略，直接将map.keySet()写在for-each循环的条件中
        for(String key:keys){
            Log.d(TAG, "initLinkData: "+key+" "+mLinkMap.get(key).getInetAddress());
            linkBeanList.add(new SwipeLinkBean(key,mLinkMap.get(key)));
        }
    }
}
