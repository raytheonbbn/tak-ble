package com.atakmap.android.ble_forwarder.util;

import android.app.Activity;

/**todo move to atakUtils*/


public class ActivityManager {
    private static ActivityManager instance = null;
    private Activity activity;

    private ActivityManager(){

    }

    public static ActivityManager getInstance(){
        if(instance == null){
            instance = new ActivityManager();
        }
        return instance;
    }

    public void setActivity(Activity activity){
        this.activity = activity;
    }

    public Activity getActivity(){
        return activity;
    }
}