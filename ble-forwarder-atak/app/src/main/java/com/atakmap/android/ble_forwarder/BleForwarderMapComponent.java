
package com.atakmap.android.ble_forwarder;

import android.content.Context;
import android.content.Intent;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.coremap.log.Log;
import com.atakmap.android.ble_forwarder.plugin.R;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;

public class BleForwarderMapComponent extends DropDownMapComponent {

    private static final String TAG = "DinoMapComponent";

    public static Context pluginContext;

    private Timer dueTimeTimer = new Timer();

    private MainDropDownReceiver mainDdr = null;

    private ServerSocket serverSocket;

    public static final int SERVERPORT1 = 8089;
    public static final int SERVERPORT2 = 8088;

    Thread serverThread = null;

    public void onCreate(final Context context, Intent intent,
            final MapView view) {

        Log.d(TAG, "Creating the dino plugin thing.");

        // NOTE: R.style.ATAKPluginTheme does not support TabLayout, so
        // I needed to change the theme to AppCompat.
        context.setTheme(R.style.Theme_AppCompat);
        super.onCreate(context, intent, view);
        pluginContext = context;

        mainDdr = new MainDropDownReceiver(view, context);

        DocumentedIntentFilter ddFilterMain = new DocumentedIntentFilter();
        ddFilterMain.addAction(MainDropDownReceiver.SHOW_PLUGIN);
        ddFilterMain.addAction(MainDropDownReceiver.REFRESH_MAIN_SCREEN);
        registerDropDownReceiver(mainDdr, ddFilterMain);

        this.serverThread = new Thread(new ServerThread(SERVERPORT1));
        this.serverThread.start();
        this.serverThread = new Thread(new ServerThread(SERVERPORT2));
        this.serverThread.start();
    }

    class ServerThread implements Runnable {

        int port;

        public ServerThread(int port) {
            this.port = port;
        }

        public void run() {

            Log.d(TAG, "Running server thread.");

            Socket socket = null;
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
            }

            Log.d(TAG, "Created server socket on port " + port);

            try {

                socket = serverSocket.accept();

                Log.d(TAG, "Accepted server connection on port " + port);

                DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

                while (!Thread.currentThread().isInterrupted()) {

                    Log.d(TAG, "Trying to read string from connection...");
                    String str = (String) in.readUTF();
                    // Display the string on the console
                    Log.d(TAG, "Got data from ATAK on port " + port + ": " + str);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    Log.d(TAG, "Closing server socket...");
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }


}
