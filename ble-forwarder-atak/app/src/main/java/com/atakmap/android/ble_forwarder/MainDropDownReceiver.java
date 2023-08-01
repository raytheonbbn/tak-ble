
package com.atakmap.android.ble_forwarder;

import static android.content.Context.BLUETOOTH_SERVICE;

import static com.atakmap.android.ble_forwarder.util.CotUtils.DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.START_DELIMITER_STRING;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.ble_forwarder.ble.TAKBLEManager;
import com.atakmap.android.ble_forwarder.plugin.R;
import com.atakmap.android.ble_forwarder.takserver_facade.CoTServerThread;
import com.atakmap.android.ble_forwarder.takserver_facade.HttpServerThread;
import com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer;
import com.atakmap.android.ble_forwarder.util.BLEUtil;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * MainDropDownReceiver displays a menu containing information about the current missions,
 * as well as the next task and its due date, among other things
 */
public class MainDropDownReceiver extends DropDownReceiver
        implements DropDown.OnStateListener, CoTServerThread.NewCotCallback,
        NewCotDequeuer.NewCotDequeuedCallback,
        TAKBLEManager.TAKBLEManagerCallbacks {

    public static final String TAG = MainDropDownReceiver.class.getSimpleName();

    public static final String SHOW_PLUGIN = MainDropDownReceiver.class.getSimpleName() + "SHOW_PLUGIN";
    public static final String REFRESH_MAIN_SCREEN = MainDropDownReceiver.class.getSimpleName() + "REFRESH_MAIN_SCREEN";
    private final View templateView;

    Thread peripheralLoggerThread = null;
    Thread centralLoggerThread = null;

    public TextView peripheralLogTextView;
    public TextView centralLogTextView;
    public TextView serverStatusTextView;
    public TextView connectionStatusTextView;
    public Button startScanButton;
    public Button disconnectButton;
    public Button peripheralLogsButton;
    public Button centralLogsButton;
    public ScrollView peripheralLogsScrollView;
    public ScrollView centralLogsScrollView;

    Handler handler;



    private CoTServerThread cotServer = null;
    private Thread cotServerThread = null;
    private Thread httpServerThread = null;
    private NewCotDequeuer newCotDequeuer = null;
    private Thread cotdequeuerThread = null;

    public static final String SERVER_STATUS_DOWN = "DOWN";
    public static final String SERVER_STATUS_UP = "UP";
    public static final String REMOTE_DEVICE_CONNECTED = "CONNECTED";
    public static final String REMOTE_DEVICE_NOT_CONNECTED = "NOT CONNECTED";
    public static final String REMOTE_DEVICE_CONNECTING = "CONNECTING";

    TAKBLEManager bleManager;

    public static Queue<String> peripheralLogMessages = new ArrayBlockingQueue<>(1000);
    public static Queue<String> centralLogMessages = new ArrayBlockingQueue<>(1000);

    public static String receivedCot = "";

    /**************************** CONSTRUCTOR *****************************/


    public MainDropDownReceiver(final MapView mapView,
                                final Context context) {
        super(mapView);

        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        handler = new Handler(Looper.getMainLooper());

        this.peripheralLoggerThread = new Thread(new PeripheralLogger());
        this.peripheralLoggerThread.start();
        this.centralLoggerThread = new Thread(new CentralLogger());
        this.centralLoggerThread.start();

        peripheralLogsButton = templateView.findViewById(R.id.peripheralLogsButton);
        peripheralLogsButton.setText("Hide Peripheral Logs");
        peripheralLogsScrollView = templateView.findViewById(R.id.peripheralLogsScrollView);
        peripheralLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (peripheralLogsScrollView.getVisibility() == View.VISIBLE) {
                    peripheralLogsButton.setText("Show Peripheral Logs");
                    peripheralLogsScrollView.setVisibility(View.INVISIBLE);
                } else {
                    peripheralLogsButton.setText("Hide Peripheral Logs");
                    peripheralLogsScrollView.setVisibility(View.VISIBLE);
                }
            }
        });

        centralLogsButton = templateView.findViewById(R.id.centralLogsButton);
        centralLogsButton.setText("Hide Central Logs");
        centralLogsScrollView = templateView.findViewById(R.id.centralLogsScrollView);
        centralLogsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (centralLogsScrollView.getVisibility() == View.VISIBLE) {
                    centralLogsButton.setText("Show Central Logs");
                    centralLogsScrollView.setVisibility(View.INVISIBLE);
                } else {
                    centralLogsButton.setText("Hide Central Logs");
                    centralLogsScrollView.setVisibility(View.VISIBLE);
                }
            }
        });

        Log.d(TAG, "Creating ble manager.");

        bleManager = new TAKBLEManager(
                peripheralLogMessages, centralLogMessages,
                this
        );

        Log.d(TAG, "Trying to do ble setup.");

        if (!bleManager.initialize()) {
            Log.w(TAG, "Failed bluetooth related setup.");
            peripheralLogMessages.add("Failed bluetooth related setup.");
            centralLogMessages.add("Failed bluetooth related setup.");
        } else {
            Log.d(TAG, "Successfully fnished bluetooth related setup.");
        }

        Log.d(TAG, "Got past ble setup.");

        peripheralLogTextView = templateView.findViewById(R.id.textView);
        centralLogTextView = templateView.findViewById(R.id.serverLogs);
        startScanButton = templateView.findViewById(R.id.startScanButton);
        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bleManager.startScanning();
                startScanButton.setEnabled(false);
            }
        });

        disconnectButton = templateView.findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bleManager.disconnect();
            }
        });

        serverStatusTextView = templateView.findViewById(R.id.serverStatus);
        serverStatusTextView.setText(SERVER_STATUS_DOWN);
        serverStatusTextView.setTextColor(Color.RED);

        connectionStatusTextView = templateView.findViewById(R.id.remoteConnectionStatus);
        connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
        connectionStatusTextView.setTextColor(Color.RED);

        this.cotServer = new CoTServerThread(8089, this, peripheralLogMessages);
        this.cotServerThread = new Thread(cotServer);
        this.cotServerThread.start();

        this.httpServerThread = new Thread(new HttpServerThread(8080, peripheralLogMessages));
        httpServerThread.start();

        newCotDequeuer = new NewCotDequeuer(
                this,
                centralLogMessages,
                peripheralLogMessages
        );
        this.cotdequeuerThread = new Thread(newCotDequeuer);
        this.cotdequeuerThread.start();

    }

    @Override
    public void onNewCotReceived(String newCot) {
        Log.d(TAG, "onNewCotReceived");
        newCotDequeuer.addNewCotToQueue(newCot);
    }

    @Override
    public void newCotSubstringDequeued(String newCotSubstring) {
        bleManager.sendDataToConnectedDevices(newCotSubstring);
    }

    @Override
    public void centralUp() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                serverStatusTextView.setText(SERVER_STATUS_UP);
                serverStatusTextView.setTextColor(Color.GREEN);
            }
        });
    }

    @Override
    public void remotePeripheralConnected() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTED);
                connectionStatusTextView.setTextColor(Color.GREEN);
            }
        });
    }

    @Override
    public void remotePeripheralConnecting() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTING);
                connectionStatusTextView.setTextColor(Color.YELLOW);
            }
        });
    }

    @Override
    public void remotePeripiheralDisconnected() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
                connectionStatusTextView.setTextColor(Color.RED);
            }
        });
    }

    @Override
    public void disconnectedFromRemoteCentral() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectButton.setEnabled(false);
                startScanButton.setEnabled(true);
            }
        });
    }

    @Override
    public void connectedToRemoteCentral() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                disconnectButton.setEnabled(true);
            }
        });
    }

    @Override
    public void scanFinished() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                startScanButton.setEnabled(true);
            }
        });
    }

    @Override
    public void receivedStringOverBLE(String receivedValue) {

        //.add("Got notified of new value for characteristic with uuid " + characteristic.getUuid());
        //peripheralLogMessages.add("Got characteristic value: " + receivedValue);

        // TODO: This needs to be modified to read both HTTP and CoT's

        if (receivedValue.startsWith(START_DELIMITER_STRING)) {
            peripheralLogMessages.add("Got start of CoT.");
            receivedCot = START_DELIMITER_STRING + " ";
        } else {
            if (receivedCot.equals("")) {
                // ignore this - if we are getting data that is not the startDelimiterString and receivedCot is empty,
                // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                // just ignore all this data
//                    peripheralLogMessages.add("Ignoring this value, we didn't get start delimiter ("
//                            + startDelimiterString + ") yet.");
            } else {

//                    peripheralLogMessages.add("Got non start delimiter value after receiving start delimiter, adding it to receivedCot...");

                receivedCot += receivedValue;

                if (receivedCot.startsWith(START_DELIMITER_STRING) && receivedCot.endsWith(DELIMITER_STRING)) {
                    peripheralLogMessages.add("Received full cot: " + receivedCot);
                    cotServer.addNewOutgoingCot(receivedCot);
                    receivedCot = "";
                    //peripheralLogMessages.add("TODO: send CoT to ATAK");
                }
            }

        }
    }

    class PeripheralLogger implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Starting UI logger...");
            while (true) {
                String msg = peripheralLogMessages.poll();
                if (peripheralLogTextView != null && msg != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            peripheralLogTextView.append(msg + "\n" + "---" + "\n");
                        }
                    });
                }
            }
        }
    }

    class CentralLogger implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Starting UI logger...");
            while (true) {
                String msg = centralLogMessages.poll();
                if (centralLogTextView != null && msg != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            centralLogTextView.append(msg + "\n" + "---" + "\n");
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
