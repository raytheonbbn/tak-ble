
package com.atakmap.android.ble_forwarder.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;

import com.atakmap.android.atakutils.MapItems;
import com.atakmap.android.ble_forwarder.BleForwarderMapComponent;
import com.atakmap.android.ble_forwarder.util.ActivityManager;
import com.atakmap.android.maps.MapComponent;
import com.atakmap.android.maps.MapEvent;
import com.atakmap.android.maps.MapGroup;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.comms.CommsMapComponent;
import com.atakmap.comms.CotServiceRemote;
import com.atakmap.coremap.cot.event.CotDetail;
import com.atakmap.coremap.cot.event.CotEvent;
import com.atakmap.coremap.log.Log;
import com.bbn.atak.triggeraction.action.ActionExecutionException;
import com.bbn.atak.triggeraction.action.ActionExecutor;
import com.bbn.atak.triggeraction.action.ActionInitException;
import com.bbn.atak.triggeraction.action.ActionNotFoundException;
import com.bbn.atak.triggeraction.action.actions.PopupAction;
import com.bbn.atak.triggeraction.action.actions.SMSAction;
import com.bbn.atak.triggeraction.action.actions.VibrateAction;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import transapps.maps.plugin.lifecycle.Lifecycle;

public class BleForwarderLifecycle implements Lifecycle {
    private static final String POPUP_ACTION = "popupAction";
    private final Context pluginContext;
    private final Collection<MapComponent> overlays;
    private MapView mapView;
    private static Activity atakActivity;
    private final static String TAG = "DinoLifecycle";
    private ActionExecutor executor;
    private boolean isAtakClosing = false;
    public BleForwarderLifecycle(Context ctx) {
        this.pluginContext = ctx;
        this.overlays = new LinkedList<>();
        this.mapView = null;
        PluginNativeLoader.init(ctx);
    }

    @Override
    public void onConfigurationChanged(Configuration arg0) {
        for (MapComponent c : this.overlays)
            c.onConfigurationChanged(arg0);
    }

    @Override
    public void onCreate(final Activity arg0,
            final transapps.mapi.MapView arg1) {
        this.atakActivity = arg0;
        ActivityManager.getInstance().setActivity(atakActivity);
        if (arg1 == null || !(arg1.getView() instanceof MapView)) {
            Log.w(TAG, "This plugin is only compatible with ATAK MapView");
            return;
        }
        this.mapView = (MapView) arg1.getView();
        BleForwarderLifecycle.this.overlays
                .add(new BleForwarderMapComponent());

        // create components
        Iterator<MapComponent> iter = BleForwarderLifecycle.this.overlays
                .iterator();
        MapComponent c;
        while (iter.hasNext()) {
            c = iter.next();
            try {
                c.onCreate(BleForwarderLifecycle.this.pluginContext,
                        arg0.getIntent(),
                        BleForwarderLifecycle.this.mapView);
            } catch (Exception e) {
                Log.w(TAG,
                        "Unhandled exception trying to create overlays MapComponent",
                        e);
                iter.remove();
            }
        }

    }



    @Override
    public void onDestroy() {
        Log.d(TAG, "ON DESTROY");
        isAtakClosing = true;
        for (MapComponent c : this.overlays)
            c.onDestroy(this.pluginContext, this.mapView);
    }

    @Override
    public void onFinish() {
        // XXX - no corresponding MapComponent method
        Log.d(TAG, "ON FINISH");
    }

    @Override
    public void onPause() {
        Log.d(TAG, "ON PAUSE");
        for (MapComponent c : this.overlays)
            c.onPause(this.pluginContext, this.mapView);
    }

    @Override
    public void onResume() {
        for (MapComponent c : this.overlays)
            c.onResume(this.pluginContext, this.mapView);
    }

    @Override
    public void onStart() {
        for (MapComponent c : this.overlays)
            c.onStart(this.pluginContext, this.mapView);
    }

    @Override
    public void onStop() {
        Log.d(TAG, "ON STOP");
        for (MapComponent c : this.overlays)
            c.onStop(this.pluginContext, this.mapView);
    }

    public static Activity getCurrentActivity() {
        return atakActivity;
    }
}
