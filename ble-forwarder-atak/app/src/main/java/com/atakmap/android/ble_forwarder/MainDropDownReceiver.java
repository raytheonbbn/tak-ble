
package com.atakmap.android.ble_forwarder;

import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_RESPONSE_START_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DELIMITER;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.START_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.ble_forwarder.ble.TAKBLEManager;
import com.atakmap.android.ble_forwarder.plugin.R;
import com.atakmap.android.ble_forwarder.takserver_facade.CoTServerThread;
import com.atakmap.android.ble_forwarder.takserver_facade.MyRestServer;
import com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FileManager;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FilesInformation;
import com.atakmap.android.ble_forwarder.util.CotUtils;
import com.atakmap.android.ble_forwarder.util.FileNameAndBytes;
import com.atakmap.android.ble_forwarder.util.Utils;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
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

    Thread peripheralLoggerThread;
    Thread centralLoggerThread;

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

    // listens for CoT's from local ATAK instance and sends them to NewCotDequeuer,
    // sends CoT's received over BLE to local ATAK instance
    private final CoTServerThread cotServer;
    // sends CoT's over BLE connection using TAKBLEManager
    private static NewCotDequeuer newCotDequeuer;
    // handles scanning for BLE devices, connecting, sending data over BLE connection,
    // receiving data over BLE connection
    TAKBLEManager bleManager;

    public static final String SERVER_STATUS_DOWN = "DOWN";
    public static final String SERVER_STATUS_UP = "UP";
    public static final String REMOTE_DEVICE_CONNECTED = "CONNECTED";
    public static final String REMOTE_DEVICE_NOT_CONNECTED = "NOT CONNECTED";
    public static final String REMOTE_DEVICE_CONNECTING = "CONNECTING";

    public static Queue<String> peripheralLogMessages = new ArrayBlockingQueue<>(1000);
    public static Queue<String> centralLogMessages = new ArrayBlockingQueue<>(1000);

    public static String receivedCot = "";

    private static SyncSearchCallback currentSyncSearchCallback = null;
    private static SyncContentCallback currentSyncContentCallback = null;

    private static Gson gson = new Gson();

    public interface SyncSearchCallback {
        void result(String json);
    }

    public interface SyncContentCallback {
        void result(FileNameAndBytes fileNameAndBytes);
    }

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
        peripheralLogsButton.setText(R.string.hide_peripheral_logs);
        peripheralLogsScrollView = templateView.findViewById(R.id.peripheralLogsScrollView);
        peripheralLogsButton.setOnClickListener(view -> {
            if (peripheralLogsScrollView.getVisibility() == View.VISIBLE) {
                peripheralLogsButton.setText(R.string.show_peripheral_logs);
                peripheralLogsScrollView.setVisibility(View.INVISIBLE);
            } else {
                peripheralLogsButton.setText(R.string.hide_peripheral_logs);
                peripheralLogsScrollView.setVisibility(View.VISIBLE);
            }
        });

        centralLogsButton = templateView.findViewById(R.id.centralLogsButton);
        centralLogsButton.setText(R.string.hide_central_logs);
        centralLogsScrollView = templateView.findViewById(R.id.centralLogsScrollView);
        centralLogsButton.setOnClickListener(view -> {
            if (centralLogsScrollView.getVisibility() == View.VISIBLE) {
                centralLogsButton.setText(R.string.show_central_logs);
                centralLogsScrollView.setVisibility(View.INVISIBLE);
            } else {
                centralLogsButton.setText(R.string.hide_central_logs);
                centralLogsScrollView.setVisibility(View.VISIBLE);
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
        startScanButton.setOnClickListener(view -> {
            bleManager.startScanning();
            startScanButton.setEnabled(false);
        });

        disconnectButton = templateView.findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(view -> bleManager.disconnect());

        serverStatusTextView = templateView.findViewById(R.id.serverStatus);
        serverStatusTextView.setText(SERVER_STATUS_DOWN);
        serverStatusTextView.setTextColor(Color.RED);

        connectionStatusTextView = templateView.findViewById(R.id.remoteConnectionStatus);
        connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
        connectionStatusTextView.setTextColor(Color.RED);

        this.cotServer = new CoTServerThread(8089, this, peripheralLogMessages);
        Thread cotServerThread = new Thread(cotServer);
        cotServerThread.start();

//        Thread httpServerThread = new Thread(new HttpServerThread(8080, peripheralLogMessages));
//        httpServerThread.start();
        MyRestServer restServer = new MyRestServer(8080, context);
        try {
            restServer.start();
            Log.d(TAG, "Started rest server on port 8080");
        } catch (IOException e) {
            Log.e(TAG, "Failure to start http rest server", e);
        }

        newCotDequeuer = new NewCotDequeuer(
                this,
                centralLogMessages,
                peripheralLogMessages
        );
        Thread cotdequeuerThread = new Thread(newCotDequeuer);
        cotdequeuerThread.start();

    }

    @Override
    public void onNewCotReceived(String newCot) {
        Log.d(TAG, "onNewCotReceived: \n" + newCot);
        newCotDequeuer.addNewCotToQueue(newCot);
    }

    @Override
    public void newCotSubstringDequeued(String newCotSubstring) {
        bleManager.sendDataToConnectedDevices(newCotSubstring);
    }

    @Override
    public void centralUp() {
        handler.post(() -> {
            serverStatusTextView.setText(SERVER_STATUS_UP);
            serverStatusTextView.setTextColor(Color.GREEN);
        });
    }

    @Override
    public void remotePeripheralConnected() {
        handler.post(() -> {
            connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTED);
            connectionStatusTextView.setTextColor(Color.GREEN);
        });
    }

    @Override
    public void remotePeripheralConnecting() {
        handler.post(() -> {
            connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTING);
            connectionStatusTextView.setTextColor(Color.YELLOW);
        });
    }

    @Override
    public void remotePeripiheralDisconnected() {
        handler.post(() -> {
            connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
            connectionStatusTextView.setTextColor(Color.RED);
        });
    }

    @Override
    public void disconnectedFromRemoteCentral() {
        handler.post(() -> {
            disconnectButton.setEnabled(false);
            startScanButton.setEnabled(true);
        });
    }

    @Override
    public void connectedToRemoteCentral() {
        handler.post(() -> disconnectButton.setEnabled(true));
    }

    @Override
    public void scanFinished() {
        handler.post(() -> startScanButton.setEnabled(true));
    }

    @Override
    public void receivedStringOverBLE(String receivedValue) {

        if (receivedValue.startsWith(START_DELIMITER_STRING)) {
            peripheralLogMessages.add("Got start of CoT.");
            //receivedCot = START_DELIMITER_STRING;// + " ";
            receivedCot = receivedValue;

            if (receivedCot.startsWith(START_DELIMITER_STRING) && receivedCot.endsWith(DELIMITER_STRING)) {
                processReceivedCoT(receivedCot);
                receivedCot = "";
            }

        } else {
            if (!receivedCot.equals("")) {
                receivedCot += receivedValue;

                Log.d(TAG, "ReceivedCot so far: " + receivedCot);

                if (receivedCot.startsWith(START_DELIMITER_STRING) && receivedCot.endsWith(DELIMITER_STRING)) {
                    processReceivedCoT(receivedCot);
                    receivedCot = "";
                }
            }

        }

    }

    private void processReceivedCoT(String cot) {
        peripheralLogMessages.add("Received full cot: " + cot);
        Log.d(TAG, "Received full cot: " + cot);
        if (cot.equals(CotUtils.SYNC_SEARCH_FAKE_COT_STRING)) {
            Log.d(TAG, "Got fake cot that signals a sync search from other device - generating JSON response from files that I am currently aware of...");
            String currentFilesJsonString = FileManager.getInstance().getJsonStringForCurrentFiles();
            Log.d(TAG, "Current files json string: " + currentFilesJsonString);
            String syncSearchResponseCot = SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING + currentFilesJsonString + DELIMITER_STRING;
            newCotDequeuer.addNewCotToQueue(syncSearchResponseCot);
        } else if (cot.startsWith(SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING)) {
            if (currentSyncSearchCallback != null) {
                String json = cot.substring(SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING.length());
                json = json.substring(0, json.length() - DELIMITER_STRING.length());
                currentSyncSearchCallback.result(json);
                currentSyncSearchCallback = null;
            } else {
                Log.w(TAG, "Got sync search response string, but sync request callback was null.");
            }
        } else if (cot.startsWith(CotUtils.CONTENT_REQUEST_START_DELIMITER_STRING)) {
            String hash = cot.substring(CotUtils.CONTENT_REQUEST_START_DELIMITER_STRING.length());
            hash = hash.substring(0, hash.length() - DELIMITER_STRING.length());
            Log.d(TAG, "Got content request with hash: " + hash);
            File file = FileManager.getInstance().getFileData(hash);
            FilesInformation.FileInfo fileInfo = FileManager.getInstance().getFileInfo(hash);
            try {
                byte[] fileBytes = Utils.convertFileToByteArray(file);
                String fileBytesString = Utils.byteArrayToHexString(fileBytes);
                FileNameAndBytes fileNameAndBytes = new FileNameAndBytes();
                fileNameAndBytes.setFileName(fileInfo.getName());
                fileNameAndBytes.setFileBytesString(fileBytesString);
                String fileNameAndBytesString = gson.toJson(fileNameAndBytes);
                newCotDequeuer.addNewCotToQueue(CONTENT_RESPONSE_START_DELIMITER_STRING + fileNameAndBytesString + DELIMITER_STRING);
            } catch (IOException e) {
                e.printStackTrace();
                Log.w(TAG, "Failed to read contents of file: " + e.getMessage(), e);
            }

        } else if (cot.startsWith(CONTENT_RESPONSE_START_DELIMITER_STRING)) {
            if (currentSyncContentCallback != null) {
                String fileNameAndBytesString = cot.substring(CONTENT_RESPONSE_START_DELIMITER_STRING.length());
                fileNameAndBytesString = fileNameAndBytesString.substring(0, fileNameAndBytesString.length() - DELIMITER_STRING.length());
                Log.d(TAG, "Got file bytes string: " + fileNameAndBytesString);
                FileNameAndBytes fileNameAndBytes = gson.fromJson(fileNameAndBytesString, FileNameAndBytes.class);
                currentSyncContentCallback.result(fileNameAndBytes);
                currentSyncContentCallback = null;
            }
        } else {
            cotServer.addNewOutgoingCot(cot);
        }
    }

    class PeripheralLogger implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Starting UI logger...");
            while (true) {
                String msg = peripheralLogMessages.poll();
                if (peripheralLogTextView != null && msg != null) {
                    handler.post(() -> peripheralLogTextView.append(msg + "\n" + "---" + "\n"));
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
                    handler.post(() -> centralLogTextView.append(msg + "\n" + "---" + "\n"));
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

    public static void sendSyncSearchRequest(SyncSearchCallback callback) {
        Log.d(TAG, "Sending cot for sync search request over BLE to other device...");
        newCotDequeuer.addNewCotToQueue(CotUtils.SYNC_SEARCH_FAKE_COT_STRING);
        currentSyncSearchCallback = callback;
    }

    public static void sendSyncContentRequest(String hash, SyncContentCallback callback) {
        Log.d(TAG, "Sending cot for sync content request over BLE to other device...");
        newCotDequeuer.addNewCotToQueue(CotUtils.CONTENT_REQUEST_START_DELIMITER_STRING + hash + DELIMITER_STRING);
        currentSyncContentCallback = callback;
    }
}
