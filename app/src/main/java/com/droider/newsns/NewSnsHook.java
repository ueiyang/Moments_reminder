package com.droider.newsns;

import android.app.Activity;
import android.app.AndroidAppHelper;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NewSnsHook implements IXposedHookLoadPackage {
    Activity snsActivity = null;
    final String dataBaseName = "com.tencent.wcdb.database.SQLiteDatabase";
    String insertWithOnConflictMethod = "insertWithOnConflict";
    String launchActivityName = "com.tencent.mm.ui.LauncherUI";
    final String snsActivityName = "com.tencent.mm.plugin.sns.ui.SnsTimeLineUI";
    String snsModelYClass  ="com.tencent.mm.plugin.sns.model.y";
    String onResumeMethod = "onResume";
    String onCreateMethod = "onCreate";
    String onDestroyMethod = "onDestroy";
    TimerTask timerTask = null;
    Timer timer = new Timer();
    Context context;
    boolean snsOn = false;
    boolean  snsLaunchByXP = false;
    boolean friend1 = false;
    boolean friend2 = false;
    boolean friend3 = false;

    public final void schedule()  {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                try {
                    PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    if(!pm.isScreenOn()){  //黑屏时才运行程序，以免影响手机使用
                        if(snsOn && snsActivity != null){   //有时网络不佳，刷新朋友圈时a方法并没有调用，朋友圈因此没有关闭，下一次刷新朋友圈时先关闭之前打开的activity。
                            snsActivity.finish();
                            snsOn = false;
                        }
                        else {
                            Intent intent = new Intent(context, Class.forName(snsActivityName));
                            snsLaunchByXP = true;
                            context.startActivity(intent);  //打开朋友圈的activity
                            snsOn = true;
                            Log.d("myLog", "start sns");
                        }
                    }
                    else{
                        Log.d("myLog","screen on");
                    }
                }
                catch(ClassNotFoundException e){
                }
            }
        };
        timer.schedule(timerTask,20000,600000);  //每隔10分钟运行一次
        Log.d("myLog","timer.schedule(timerTask)");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        XposedHelpers.findAndHookMethod(dataBaseName, lpparam.classLoader, insertWithOnConflictMethod, String.class, String.class, ContentValues.class, int.class, new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam param) throws Throwable {
                if (param.args[0].equals("SnsInfo")) {
                    ContentValues contentValues = ((ContentValues) param.args[2]);
                    int time = contentValues.getAsInteger("createTime");    //获取朋友圈发布时间
                    String name = contentValues.getAsString("userName");  //获取朋友圈发布者微信id
                    if(name.equals("wxid_xxx")) friend1 = true;   //wxid_xxx改成自己想要关注的好友的微信id即可
                    else if(name.equals("wxid_yyy")) friend2 = true;
                    else if(name.equals("wxid_zzz")) friend3 = true;
                    long msgCreatetime = Long.parseLong(time + "") * 1000L;
                    DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String snsDate = format.format(new Date(msgCreatetime));
                    Log.d("myLog：", "insert:" + snsDate + " " + name);   //打印朋友圈发布时间和发布者
                }
            }
        });


        XposedHelpers.findAndHookMethod(launchActivityName, lpparam.classLoader, onDestroyMethod,new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.d("myLog","launchActivity.onDestroy()");
                timerTask.cancel();
                Log.d("myLog","timer.cancel()");
            }
        });

        XposedHelpers.findAndHookMethod(launchActivityName, lpparam.classLoader, onCreateMethod,Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d("myLog","launchActivity.onCreate");
                context = AndroidAppHelper.currentApplication().getApplicationContext();
                Log.d("myLog","context已赋值");
                schedule();
                Log.d("myLog","Schedule方法已调用");
            }
        });

        XposedHelpers.findAndHookMethod(launchActivityName, lpparam.classLoader, onResumeMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d("myLog","launchActivity.onResume");
                Context con = (Context)param.thisObject;
                PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                if(pm.isScreenOn()) {
                    if (friend1){
                        Toast.makeText(con, "你关注的大姐有新朋友圈了！", Toast.LENGTH_LONG).show();
                        friend1 = false;
                    }
                    if (friend2){
                        Toast.makeText(con, "你关注的二姐有新朋友圈了！", Toast.LENGTH_LONG).show();
                        friend2 = false;
                    }
                    if (friend3){
                        Toast.makeText(con, "你关注的好友有新朋友圈了！", Toast.LENGTH_LONG).show();
                        friend3 = false;
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(snsActivityName, lpparam.classLoader, onCreateMethod,Bundle.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d("myLog","sns.onCreate");
                snsActivity = (Activity)param.thisObject;
            }
        });


        XposedHelpers.findAndHookMethod(snsModelYClass, lpparam.classLoader, "a", int.class,int.class,
    int.class,String.class,Class.forName("com.tencent.mm.network.q"),byte[].class,new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(snsLaunchByXP){   //如果是xposed启模块启动的朋友圈，自己打开朋友圈则不会执行下列代码
                    Log.d("myLog", "a()方法被调用");
                    snsActivity.finish();
                    Log.d("myLog", "snsActivity.finish()");  //关闭朋友圈
                    snsOn = false;
                    Log.d("myLog", "snsOn false");
                    snsLaunchByXP = false;
                }
            }
        });

        XposedHelpers.findAndHookMethod(snsActivityName, lpparam.classLoader, onResumeMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Log.d("myLog", "sns.onResume()");
            }
        });

        XposedHelpers.findAndHookMethod(snsActivityName, lpparam.classLoader, onDestroyMethod, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                Log.d("myLog", "sns.onDestroy()");
            }
        });
    }
}
