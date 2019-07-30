package com.example.lcdserver.Fragment;


import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.lcdserver.Bean.SwipeUserBean;
import com.example.lcdserver.Bean.User;
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
import java.util.List;

/**
 * A simple {@link Fragment} subclass.
 */
public class UserManagerFragment extends Fragment {
    private final static String TAG="UserManagerFrag";

    private FloatingActionButton addButton;
    private ListView mListView;
    private List<SwipeUserBean> mUserNameDatas;
    private AlertDialog.Builder builder;


    public UserManagerFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_user_manager, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView=getView().findViewById(R.id.user_list);
        initUserNameDatas();
        mListView.setAdapter(new CommonAdapter<SwipeUserBean>(getActivity(),mUserNameDatas,R.layout.user_item) {

            @Override
            public void convert(final ViewHolder viewHolder, SwipeUserBean swipeUserBean, final int position) {
                viewHolder.setText(R.id.user_name_of_item,"用户名："+swipeUserBean.getUserName());
                viewHolder.setOnClickListener(R.id.user_edit, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //进入新的activity或者弹一个dialogs？
                        editUser(mDatas.get(position).getUserName());

                    }
                });
                viewHolder.setOnClickListener(R.id.user_delete, new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Toast.makeText(getActivity(), "删除:" + position, Toast.LENGTH_SHORT).show();
                        //在ListView里，点击侧滑菜单上的选项时，如果想让擦花菜单同时关闭，调用这句话
                        ((SwipeMenuLayout) viewHolder.getConvertView()).quickClose();
                        ServerApplication.getInstance().deleteUser(ServerApplication.getInstance().getUserByUsername(mDatas.get(position).getUserName()));
                        mDatas.remove(position);
                        notifyDataSetChanged();
                    }
                });
            }
        });
        addButton=(FloatingActionButton)getView().findViewById(R.id.add_user);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                addUser();
            }
        });
    }

    private void editUser(final String userName) {
        final User user=ServerApplication.getInstance().getUserByUsername(userName);
        View view=LayoutInflater.from(getActivity()).inflate(R.layout.edit_user_dialogs,null);
        TextView username=view.findViewById(R.id.user_name_of_edit);
        TextView password=view.findViewById(R.id.user_password_of_edit);
        username.setText(user.getAccount());
        password.setText(user.getPassword());
        final String oldPassword=user.getPassword();
        final TextInputLayout newPassword=view.findViewById(R.id.new_user_password_edit);
        builder=new AlertDialog.Builder(getActivity()).setView(view).setTitle("编辑").setPositiveButton("修改密码", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String newPasswordString=newPassword.getEditText().getText().toString();
                if(newPasswordString.length()==6){
                    user.setPassword(newPasswordString);
                    boolean result=ServerApplication.getInstance().updateUser(user);
                    if(result) {
                        final Socket socket=ServerApplication.getInstance().getLoginMap().get(userName);
                        Toast.makeText(getActivity(), "修改成功", Toast.LENGTH_SHORT).show();
                        if(socket!=null){
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        String deviceName=user.getDeviceName();
                                        String deviceID=user.getDeviceID();
                                        TcpServerSocket.online(Config.OnlineType.OFFLINE,deviceName,deviceID,0);
                                        PrintWriter printWriter=new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
                                        JSONObject jsonObject=new JSONObject();
                                        jsonObject.put("answer", Config.MsgCode.OFFLINE);
                                        jsonObject.put("msg","服务器修改了密码");
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
                        }
                    }
                    else{
                        Toast.makeText(getActivity(),"修改失败", Toast.LENGTH_SHORT).show();
                        user.setPassword(oldPassword);
                    }
                }else {
                    Toast.makeText(getActivity(),"密码长度错误", Toast.LENGTH_SHORT).show();
                    editUser(user.getAccount());
                }
            }
        }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.create().show();
    }

    private void addUser() {
        View view= LayoutInflater.from(getActivity()).inflate(R.layout.add_user_dialogs,null);
        final TextInputLayout newUsername=view.findViewById(R.id.new_username);
        final TextInputLayout newPassword=view.findViewById(R.id.new_user_password);
        builder= new AlertDialog.Builder(getActivity()).setView(view).setTitle("新建用户").setPositiveButton("创建", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String username=newUsername.getEditText().getText().toString();
                String password=newPassword.getEditText().getText().toString();
                if(username.length()==11
                        &&password.length()==6){
                    User user=new User(username,password);
                    boolean result=ServerApplication.getInstance().updateUser(user);
                    if(result) {
                        Toast.makeText(getActivity(), "创建成功", Toast.LENGTH_SHORT).show();
                        mUserNameDatas.add(new SwipeUserBean(username));
                    }
                    else{
                        Toast.makeText(getActivity(),"创建失败", Toast.LENGTH_SHORT).show();
                    }
                }else {
                    Toast.makeText(getActivity(),"帐号或密码错误", Toast.LENGTH_SHORT).show();
                    addUser();
                }
            }
        }).setNegativeButton("取消", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        builder.create().show();
    }

    private void initUserNameDatas() {
        mUserNameDatas=new ArrayList<>();
        List<User> userList=ServerApplication.getInstance().getUserList();
        for(User user:userList){
            mUserNameDatas.add(new SwipeUserBean(user.getAccount()));
        }
    }
}
