
package com.atakmap.android.ble_forwarder;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.ble_forwarder.plugin.R;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import java.net.ServerSocket;

/**
 * MainDropDownReceiver displays a menu containing information about the current missions,
 * as well as the next task and its due date, among other things
 */
public class MainDropDownReceiver extends DropDownReceiver implements DropDown.OnStateListener {

    public static final String TAG = MainDropDownReceiver.class.getSimpleName();

    public static final String SHOW_PLUGIN = MainDropDownReceiver.class.getSimpleName() + "SHOW_PLUGIN";
    public static final String REFRESH_MAIN_SCREEN = MainDropDownReceiver.class.getSimpleName() + "REFRESH_MAIN_SCREEN";
    private final View templateView;
    private final MapView mapView;
    private final Context pluginContext;

    ServerSocket serverSocket;
    Thread Thread1 = null;
    public static String SERVER_IP = "";
    public static final int SERVER_PORT = 8080;
    String message;

    Thread loggerThread = null;

    public TextView textView;

    Handler handler;

    /**************************** CONSTRUCTOR *****************************/

    public MainDropDownReceiver(final MapView mapView,
                                final Context context) {
        super(mapView);
        this.pluginContext = context;
        this.mapView = mapView;

        // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
        // In this case, using it is not necessary - but I am putting it here to remind
        // developers to look at this Inflator
        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        textView = templateView.findViewById(R.id.textView);

        handler = new Handler(Looper.getMainLooper());

        this.loggerThread = new Thread(new UILogger());
        this.loggerThread.start();
    }

    class UILogger implements Runnable {
        @Override
        public void run() {
            Log.d(TAG, "Starting UI logger...");
            while (true) {
                String msg = BleForwarderMapComponent.logMessages.poll();
                if (textView != null && msg != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            textView.append(msg + "\n" + "---" + "\n");
                        }
                    });
                }
            }
        }
    }

    @Override
    protected void disposeImpl() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.v(TAG, "MainDropDownReceiver invoked");

        final String action = intent.getAction();
        if (action == null)
            return;

        if (action.equals(SHOW_PLUGIN)) {

            Log.d(TAG, "showing plugin drop down for dino");
            showDropDown(templateView, HALF_WIDTH, FULL_HEIGHT, FULL_WIDTH,
                    HALF_HEIGHT, false, this);

        }
    }

    @Override
    public void onDropDownSelectionRemoved() {

    }

    @Override
    public void onDropDownClose() {

    }

    @Override
    public void onDropDownSizeChanged(double width, double height) {

    }

    @Override
    public void onDropDownVisible(boolean v) {

    }

}
