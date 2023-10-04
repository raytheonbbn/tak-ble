
package com.atakmap.android.ble_forwarder.plugin;

import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_RESPONSE_START_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.START_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.ble_forwarder.takserver_facade.CoTServerThread;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FileManager;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FilesInformation;
import com.atakmap.android.ble_forwarder.util.CotUtils;
import com.atakmap.android.ble_forwarder.util.FileNameAndBytes;
import com.atakmap.android.ble_forwarder.util.Utils;
import com.atakmap.coremap.log.Log;
import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class PluginTemplate implements IPlugin, com.atakmap.android.ble_forwarder.takserver_facade.CoTServerThread.NewCotCallback,
        com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer.NewCotDequeuedCallback,
        com.atakmap.android.ble_forwarder.ble.TAKBLEManager.TAKBLEManagerCallbacks {

    public static final String TAG = PluginTemplate.class.getSimpleName();

    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    Pane templatePane;
    View templateView;

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
    CoTServerThread cotServer;
    // sends CoT's over BLE connection using TAKBLEManager
    private static com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer newCotDequeuer;
    // handles scanning for BLE devices, connecting, sending data over BLE connection,
    // receiving data over BLE connection
    com.atakmap.android.ble_forwarder.ble.TAKBLEManager bleManager;

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
        void result(com.atakmap.android.ble_forwarder.util.FileNameAndBytes fileNameAndBytes);
    }

    public PluginTemplate(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        // obtain the UI service
        uiService = serviceController.getService(IHostUIService.class);

        // initialize the toolbar button for the plugin

        // create the button
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                })
                .build();
    }

    @Override
    public void onStart() {
        // the plugin is starting, add the button to the toolbar
        if (uiService == null)
            return;

        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);
    }

    private void showPane() {

        // instantiate the plugin view if necessary
        if(templatePane == null) {
            // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
            // In this case, using it is not necessary - but I am putting it here to remind
            // developers to look at this Inflator

            templateView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.main_layout, null);

            templateView = PluginLayoutInflater.inflate(pluginContext,
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

            bleManager = new com.atakmap.android.ble_forwarder.ble.TAKBLEManager(
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

            this.cotServer = new com.atakmap.android.ble_forwarder.takserver_facade.CoTServerThread(8089, this, peripheralLogMessages);
            Thread cotServerThread = new Thread(cotServer);
            cotServerThread.start();

//        Thread httpServerThread = new Thread(new HttpServerThread(8080, peripheralLogMessages));
//        httpServerThread.start();
            com.atakmap.android.ble_forwarder.takserver_facade.MyRestServer restServer = new com.atakmap.android.ble_forwarder.takserver_facade.MyRestServer(8080, pluginContext);
            try {
                restServer.start();
                Log.d(TAG, "Started rest server on port 8080");
            } catch (IOException e) {
                Log.e(TAG, "Failure to start http rest server", e);
            }

            newCotDequeuer = new com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer(
                    this,
                    centralLogMessages,
                    peripheralLogMessages
            );
            Thread cotdequeuerThread = new Thread(newCotDequeuer);
            cotdequeuerThread.start();

            templatePane = new PaneBuilder(templateView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }
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
                fileBytesString = fileBytesString.substring(fileBytesString.indexOf("504B0304"));
                fileBytesString = fileBytesString.substring(0, fileBytesString.length() - "0D0A2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D363337396464633735616261323031632D2D0D0A".length());
                FileNameAndBytes fileNameAndBytes = new FileNameAndBytes();
                fileNameAndBytes.setFileName(fileInfo.getName());
                byte[] fileBytesStripped = Utils.hexStringToByteArray(fileBytesString);
                String fileBytesStringBase64 = Utils.encodeToBase64(fileBytesStripped);
                fileNameAndBytes.setFileBytesString(fileBytesStringBase64);
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
