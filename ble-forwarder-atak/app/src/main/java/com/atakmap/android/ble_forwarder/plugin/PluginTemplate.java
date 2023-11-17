
/*
 *
 * TAK-BLE
 * Copyright (c) 2023 Raytheon Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 * https://github.com/atapas/add-copyright.git
 *
 */

package com.atakmap.android.ble_forwarder.plugin;

import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_BEGINNING_TAG;
import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_ENDING_TAG;
import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_REQUEST_HOW;
import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_RESPONSE_HOW;
import static com.atakmap.android.ble_forwarder.util.CotUtils.CONTENT_RESPONSE_START_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DETAIL_AND_CONTENT_BEGINNING_TAGS;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DETAIL_AND_CONTENT_ENDING_TAGS;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DETAIL_BEGINNING_TAG;
import static com.atakmap.android.ble_forwarder.util.CotUtils.DETAIL_ENDING_TAG;
import static com.atakmap.android.ble_forwarder.util.CotUtils.START_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.SYNC_SEARCH_FAKE_COT_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.SYNC_SEARCH_REQUEST_HOW;
import static com.atakmap.android.ble_forwarder.util.CotUtils.SYNC_SEARCH_RESPONSE_HOW;
import static com.atakmap.android.ble_forwarder.util.CotUtils.SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.atakmap.android.atakutils.MiscUtils;
import com.atakmap.android.ble_forwarder.ble.TAKBLEManager;
import com.atakmap.android.ble_forwarder.proto.ProtoBufUtils;
import com.atakmap.android.ble_forwarder.proto.generated.Cotevent;
import com.atakmap.android.ble_forwarder.takserver_facade.CoTServerThread;
import com.atakmap.android.ble_forwarder.takserver_facade.MyRestServer;
import com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FileManager;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FilesInformation;
import com.atakmap.android.ble_forwarder.util.CotUtils;
import com.atakmap.android.ble_forwarder.util.FileNameAndBytes;
import com.atakmap.android.ble_forwarder.util.Utils;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class PluginTemplate implements IPlugin,
        CoTServerThread.NewCotCallback,
        NewCotDequeuer.NewCotDequeuedCallback,
        TAKBLEManager.TAKBLEManagerCallbacks {

    public static final String TAG = PluginTemplate.class.getSimpleName();

    public enum DEVICE_MODE {
        NONE_SELECTED,
        PERIPHERAL_MODE,
        CENTRAL_MODE
    }

    SharedPreferences sharedPreferences;

    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;

    Pane currentPane;

    Thread peripheralLoggerThread;
    Thread centralLoggerThread;

    Pane peripheralPane;
    View peripheralView;
    public TextView peripheralLogTextView;
    public TextView serverStatusTextView;
    public TextView connectionStatusTextView;
    public Button peripheralLogsButton;
    public Button toggleAdvertisingButton;
    public ScrollView peripheralLogsScrollView;

    Pane centralPane;
    View centralView;
    public TextView centralLogTextView;
    public Button centralLogsButton;
    public ScrollView centralLogsScrollView;
    public Button startScanButton;
    public Button disconnectButton;
    public EditText mtuInput;

    public Button peripheralModeButton;
    public Button centralModeButton;

    Handler handler;

    // listens for CoT's from local ATAK instance and sends them to NewCotDequeuer,
    // sends CoT's received over BLE to local ATAK instance
    CoTServerThread cotServer;
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

    public static ArrayBlockingQueue<String> centralLogMessages = new ArrayBlockingQueue<>(1000);
    public static ArrayBlockingQueue<String> peripheralLogMessages = new ArrayBlockingQueue<>(1000);

    public static String receivedCotString = "";
    public static byte[] receivedCot = null;

    private static SyncSearchCallback currentSyncSearchCallback = null;
    private static SyncContentCallback currentSyncContentCallback = null;

    private static Gson gson = new Gson();

    private DEVICE_MODE deviceMode = DEVICE_MODE.NONE_SELECTED;

    private static final String MTU_SHARED_PREF_KEY = "MTU_SHARED_PREF_KEY";

    boolean bluetoothAdapterOn = false;

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
        if(peripheralPane == null) {

            handler = new Handler(Looper.getMainLooper());

            Log.d(TAG, "Creating ble manager.");

            newCotDequeuer = new NewCotDequeuer(
                    this,
                    peripheralLogMessages,
                    centralLogMessages
            );
            Thread cotdequeuerThread = new Thread(newCotDequeuer);
            cotdequeuerThread.start();

            bleManager = new TAKBLEManager(
                    centralLogMessages, peripheralLogMessages,
                    this
            );

            Log.d(TAG, "Trying to do ble setup.");

            if (!bleManager.initialize()) {
                Log.w(TAG, "Failed bluetooth related setup.");
                centralLogMessages.add("Failed bluetooth related setup.");
                peripheralLogMessages.add("Failed bluetooth related setup.");
            } else {
                Log.d(TAG, "Successfully finished bluetooth related setup.");
            }

            Log.d(TAG, "Got past ble setup.");

            // set up initial screen UI

            deviceMode = DEVICE_MODE.PERIPHERAL_MODE;

            newCotDequeuer.setDeviceMode(deviceMode);

            currentPane = peripheralPane;

            bleManager.startPeripheralServer();

            // set up peripheral mode UI

            peripheralView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.peripheral_layout, null);

            centralModeButton = (Button) peripheralView.findViewById(R.id.centralModeButton);
            centralModeButton.setEnabled(bleManager.isBluetoothAdapterOn());

            centralModeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            deviceMode = DEVICE_MODE.CENTRAL_MODE;

                            newCotDequeuer.setDeviceMode(deviceMode);

                            currentPane = centralPane;

                            showPane();
                        }
                    });
                }
            });

            peripheralLoggerThread = new Thread(new PeripheralLogger());
            peripheralLoggerThread.start();

            peripheralLogsButton = peripheralView.findViewById(R.id.peripheralLogsButton);
            peripheralLogsButton.setText(R.string.hide_peripheral_logs);
            peripheralLogsScrollView = peripheralView.findViewById(R.id.peripheralLogsScrollView);
            peripheralLogsButton.setOnClickListener(view -> {
                if (peripheralLogsScrollView.getVisibility() == View.VISIBLE) {
                    peripheralLogsButton.setText(R.string.show_peripheral_logs);
                    peripheralLogsScrollView.setVisibility(View.INVISIBLE);
                } else {
                    peripheralLogsButton.setText(R.string.hide_peripheral_logs);
                    peripheralLogsScrollView.setVisibility(View.VISIBLE);
                }
            });

            toggleAdvertisingButton = (Button) peripheralView.findViewById(R.id.toggleAdvertisingButton);
            if (bleManager.isAdvertising()) {
                toggleAdvertisingButton.setText(R.string.toggle_advertising_off);
            } else {
                toggleAdvertisingButton.setText(R.string.toggle_advertising_on);
            }
            toggleAdvertisingButton.setOnClickListener(view -> {
                if (bleManager.isAdvertising()) {
                    bleManager.stopAdvertising();
                    toggleAdvertisingButton.setText(R.string.toggle_advertising_on);
                } else {
                    bleManager.startAdvertising();
                    toggleAdvertisingButton.setText(R.string.toggle_advertising_off);
                }
            });

            peripheralLogTextView = peripheralView.findViewById(R.id.serverLogs);

            serverStatusTextView = peripheralView.findViewById(R.id.serverStatus);
            serverStatusTextView.setText(SERVER_STATUS_DOWN);
            serverStatusTextView.setTextColor(Color.RED);

            connectionStatusTextView = peripheralView.findViewById(R.id.remoteConnectionStatus);
            connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
            connectionStatusTextView.setTextColor(Color.RED);


            peripheralPane = new PaneBuilder(peripheralView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();

            // set up central mode UI

            centralView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.central_layout);

            this.centralLoggerThread = new Thread(new CentralLogger());
            this.centralLoggerThread.start();

            centralLogsButton = centralView.findViewById(R.id.centralLogsButton);
            centralLogsButton.setText(R.string.hide_central_logs);
            centralLogsScrollView = centralView.findViewById(R.id.centralLogsScrollView);
            centralLogsButton.setOnClickListener(view -> {
                if (centralLogsScrollView.getVisibility() == View.VISIBLE) {
                    centralLogsButton.setText(R.string.show_central_logs);
                    centralLogsScrollView.setVisibility(View.INVISIBLE);
                } else {
                    centralLogsButton.setText(R.string.hide_central_logs);
                    centralLogsScrollView.setVisibility(View.VISIBLE);
                }
            });

            mtuInput = centralView.findViewById(R.id.mtuInput);
            mtuInput.setInputType(InputType.TYPE_CLASS_NUMBER);

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MapView.getMapView().getContext());
            int mtuSize = sharedPref.getInt(MTU_SHARED_PREF_KEY, TAKBLEManager.DEFAULT_MTU_SIZE);
            mtuInput.setText(Integer.toString(mtuSize));

            centralLogTextView = centralView.findViewById(R.id.textView);
            startScanButton = centralView.findViewById(R.id.startScanButton);
            startScanButton.setOnClickListener(view -> {

                try {
                    String newMtuString = mtuInput.getText().toString();
                    int newMtu = Integer.parseInt(newMtuString);
                    bleManager.setMtu(newMtu);

                    SharedPreferences.Editor edit = sharedPref.edit();
                    edit.putInt(MTU_SHARED_PREF_KEY, newMtu);
                    edit.apply();

                } catch (NumberFormatException e) {
                    Log.w(TAG, "Failed to parse mtu", e);
                    MiscUtils.toast("Failed to parse mtu, please re-enter. (" + e.getMessage() + ")");
                    return;
                }

                bleManager.startScanning();
                startScanButton.setEnabled(false);
                mtuInput.setEnabled(false);
            });

            disconnectButton = centralView.findViewById(R.id.disconnectButton);
            disconnectButton.setEnabled(false);
            disconnectButton.setOnClickListener(view -> bleManager.disconnect());

            centralPane = new PaneBuilder(centralView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();

            // set up cot server

            this.cotServer = new CoTServerThread(8089, this, centralLogMessages);
            Thread cotServerThread = new Thread(cotServer);
            cotServerThread.start();

            // set up REST server

            MyRestServer restServer = new MyRestServer(8080, pluginContext);
            try {
                restServer.start();
                Log.d(TAG, "Started rest server on port 8080");
            } catch (IOException e) {
                Log.e(TAG, "Failure to start http rest server", e);
            }

            currentPane = peripheralPane;

            showPane();

        }

        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(currentPane)) {
            uiService.showPane(currentPane, null);
        }
    }

    @Override
    public void onNewCotReceived(String newCot) {
        Log.d(TAG, "onNewCotReceived: \n" + newCot);
        newCotDequeuer.addNewCotToQueue(newCot);
    }

    @Override
    public void newCotSubstringDequeuedForCentrals(byte[] data) {
        bleManager.sendDataToConnectedCentrals(data);
    }

    @Override
    public void newCotDequeuedForPeripheral(byte[] data) {
        bleManager.sendDataToConnectedPeripheral(data);
    }

    @Override
    public void peripheralUp() {
        handler.post(() -> {
            serverStatusTextView.setText(SERVER_STATUS_UP);
            serverStatusTextView.setTextColor(Color.GREEN);
        });
    }

    @Override
    public void remoteCentralConnected() {
        handler.post(() -> {
            connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTED);
            connectionStatusTextView.setTextColor(Color.GREEN);
        });
    }

    @Override
    public void remoteCentralConnecting() {
        handler.post(() -> {
            connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTING);
            connectionStatusTextView.setTextColor(Color.YELLOW);
        });
    }

    @Override
    public void remoteCentralDisconnected() {
        handler.post(() -> {
            connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
            connectionStatusTextView.setTextColor(Color.RED);
        });
    }

    @Override
    public void disconnectedFromRemotePeripheral() {
        handler.post(() -> {
            disconnectButton.setEnabled(false);
            startScanButton.setEnabled(true);
        });
    }

    @Override
    public void connectedToRemotePeripheral() {
        handler.post(() -> disconnectButton.setEnabled(true));
    }

    @Override
    public void scanFinished() {
        handler.post(() -> {
            startScanButton.setEnabled(true);
            mtuInput.setEnabled(true);
        });
    }

    @Override
    public void receivedBytesOverBLE(byte[] receivedValue) {

        String receivedValueString = new String(receivedValue);

        if (receivedValueString.startsWith(START_DELIMITER_STRING)) {

            if (deviceMode.equals(DEVICE_MODE.CENTRAL_MODE)) {
                centralLogMessages.add("Got start of CoT.");
            } else if (deviceMode.equals(DEVICE_MODE.PERIPHERAL_MODE)) {
                peripheralLogMessages.add("Got start of CoT.");
            }
            receivedCotString = receivedValueString;

            // Convert START_DELIMITER_STRING to a byte array
            byte[] startDelimiterBytes = START_DELIMITER_STRING.getBytes(StandardCharsets.UTF_8);

            // Assuming START_DELIMITER_STRING has a known length
            int startDelimiterLength = startDelimiterBytes.length;

            byte[] receivedValueNoStartDelimiter = new byte[receivedValue.length - startDelimiterLength];
            System.arraycopy(receivedValue, startDelimiterLength, receivedValueNoStartDelimiter, 0, receivedValueNoStartDelimiter.length);

            receivedCot = receivedValueNoStartDelimiter;

            if (receivedCotString.startsWith(START_DELIMITER_STRING) && receivedCotString.endsWith(DELIMITER_STRING)) {
                handleFullyReceivedCoT();
            }

        } else {
            if (!receivedCotString.equals("")) {
                receivedCotString += receivedValueString;
                byte[] combinedArray = Arrays.copyOf(receivedCot, receivedCot.length + receivedValue.length);
                System.arraycopy(receivedValue, 0, combinedArray, receivedCot.length, receivedValue.length);
                receivedCot = combinedArray;

                Log.d(TAG, "ReceivedCot so far: " + receivedCotString);

                if (receivedCotString.startsWith(START_DELIMITER_STRING) && receivedCotString.endsWith(DELIMITER_STRING)) {
                    handleFullyReceivedCoT();
                }
            }
        }

    }

    private void handleFullyReceivedCoT() {
        // strip off the end delimiter from the receivedCot byte array

        // Convert DELIMITER_STRING to a byte array
        byte[] delimiterBytes = DELIMITER_STRING.getBytes(StandardCharsets.UTF_8);

        // Assuming DELIMITER_STRING has a known length
        int delimiterLength = delimiterBytes.length;

        byte[] strippedReceivedCot = new byte[receivedCot.length - delimiterLength];
        System.arraycopy(receivedCot, 0, strippedReceivedCot, 0, strippedReceivedCot.length);

        try {
            Cotevent.CotEvent receivedCotEventProtoBuf = Cotevent.CotEvent.parseFrom(strippedReceivedCot);

            String cotEvent = ProtoBufUtils.proto2cot(receivedCotEventProtoBuf);

            processReceivedCoT(cotEvent);

        } catch (InvalidProtocolBufferException e) {
            Log.e(TAG, "Invalid protocol buffer exception", e);
        }
        receivedCotString = "";
        receivedCot = null;
    }

    private void processReceivedCoT(String cot) {
        if (deviceMode.equals(DEVICE_MODE.CENTRAL_MODE)) {
            centralLogMessages.add("Received full cot: " + cot);
        } else if (deviceMode.equals(DEVICE_MODE.PERIPHERAL_MODE)) {
            peripheralLogMessages.add("Received full cot: " + cot);
        }
        Log.d(TAG, "Received full cot: " + cot);

        Pair<String, String> howAndContent = CotUtils.getHowAndContent(cot);
        if (howAndContent != null) {

            Log.d(TAG, "Got how and content from cot: " + howAndContent);

            if (howAndContent.first.equals(SYNC_SEARCH_REQUEST_HOW)) {
                Log.d(TAG, "Got fake cot that signals a sync search from other device - generating JSON response from files that I am currently aware of...");
                String currentFilesJsonString = FileManager.getInstance().getJsonStringForCurrentFiles();
                Log.d(TAG, "Current files json string: " + currentFilesJsonString);
                String syncSearchResponseCot = SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING + DETAIL_AND_CONTENT_BEGINNING_TAGS +
                        currentFilesJsonString + DETAIL_AND_CONTENT_ENDING_TAGS + DELIMITER_STRING;
                newCotDequeuer.addNewCotToQueue(syncSearchResponseCot);
            } else if (howAndContent.first.equals(SYNC_SEARCH_RESPONSE_HOW)) {
                if (currentSyncSearchCallback != null) {
                    String fileListJson = howAndContent.second;
                    currentSyncSearchCallback.result(fileListJson);
                    currentSyncSearchCallback = null;
                } else {
                    Log.w(TAG, "Got sync search response string, but sync request callback was null.");
                }
            } else if (howAndContent.first.equals(CONTENT_REQUEST_HOW)) {
                String hash = howAndContent.second;
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
                    newCotDequeuer.addNewCotToQueue(CONTENT_RESPONSE_START_DELIMITER_STRING + DETAIL_AND_CONTENT_BEGINNING_TAGS + fileNameAndBytesString + DETAIL_AND_CONTENT_ENDING_TAGS + DELIMITER_STRING);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.w(TAG, "Failed to read contents of file: " + e.getMessage(), e);
                }

            } else if (howAndContent.first.equals(CONTENT_RESPONSE_HOW)) {
                if (currentSyncContentCallback != null) {
                    String fileNameAndBytesString = howAndContent.second;
                    Log.d(TAG, "Got file bytes string: " + fileNameAndBytesString);
                    FileNameAndBytes fileNameAndBytes = gson.fromJson(fileNameAndBytesString, FileNameAndBytes.class);
                    currentSyncContentCallback.result(fileNameAndBytes);
                    currentSyncContentCallback = null;
                }
            } else {
                Log.w(TAG, "Got unexpected how: " + howAndContent.first);
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
                try {
                    String msg = peripheralLogMessages.take();

                    if (peripheralLogTextView != null && msg != null) {
                        handler.post(() -> peripheralLogTextView.append(msg + "\n" + "---" + "\n"));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    class CentralLogger implements Runnable {

        @Override
        public void run() {
            Log.d(TAG, "Starting UI logger...");
            while (true) {
                try {
                    String msg = centralLogMessages.take();

                    if (centralLogTextView != null && msg != null) {
                        handler.post(() -> centralLogTextView.append(msg + "\n" + "---" + "\n"));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public static void sendSyncSearchRequest(SyncSearchCallback callback) {
        Log.d(TAG, "Sending cot for sync search request over BLE to other device...");
        newCotDequeuer.addNewCotToQueue(SYNC_SEARCH_FAKE_COT_STRING);
        currentSyncSearchCallback = callback;
    }

    public static void sendSyncContentRequest(String hash, SyncContentCallback callback) {
        Log.d(TAG, "Sending cot for sync content request over BLE to other device...");
        newCotDequeuer.addNewCotToQueue(CotUtils.CONTENT_REQUEST_START_DELIMITER_STRING + DETAIL_AND_CONTENT_BEGINNING_TAGS + hash + DETAIL_AND_CONTENT_ENDING_TAGS + DELIMITER_STRING);
        currentSyncContentCallback = callback;
    }

    @Override
    public void newMtuNegotiated(int newMtu) {
        Log.d(TAG, "Set new cot dequeuer mtu to new mtu " + newMtu);
        newCotDequeuer.setMtu(newMtu);
    }

    @Override
    public void bluetoothAdapterOn() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (peripheralModeButton != null) {
                    peripheralModeButton.setEnabled(true);
                }
                if (centralModeButton != null) {
                    centralModeButton.setEnabled(true);
                }
                bluetoothAdapterOn = true;
            }
        });
    }

    @Override
    public void startedAdvertising() {
        if (toggleAdvertisingButton != null) {
            toggleAdvertisingButton.setText(R.string.toggle_advertising_off);
        }
    }

    @Override
    public void stoppedAdvertising() {
        if (toggleAdvertisingButton != null) {
            toggleAdvertisingButton.setText(R.string.toggle_advertising_on);
        }
    }

}
