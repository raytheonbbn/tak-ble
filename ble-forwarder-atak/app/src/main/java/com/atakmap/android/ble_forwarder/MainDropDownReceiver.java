
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
        NewCotDequeuer.NewCotDequeuedCallback {

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
    private Thread bleScannerThread = null;

    public static final String SERVER_STATUS_DOWN = "DOWN";
    public static final String SERVER_STATUS_UP = "UP";
    public static final String REMOTE_DEVICE_CONNECTED = "CONNECTED";
    public static final String REMOTE_DEVICE_NOT_CONNECTED = "NOT CONNECTED";
    public static final String REMOTE_DEVICE_CONNECTING = "CONNECTING";

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothLeScanner mBluetoothScanner;
    private BluetoothGatt mBluetoothGatt;
    /* Collection of notification subscribers */
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    public static Queue<String> newHttpQueue = new ArrayBlockingQueue<>(1000);

    public static Queue<String> peripheralLogMessages = new ArrayBlockingQueue<>(1000);
    public static Queue<String> centralLogMessages = new ArrayBlockingQueue<>(1000);

    public static String receivedCot = "";

    /**************************** CONSTRUCTOR *****************************/

    @SuppressLint("MissingPermission")
    public MainDropDownReceiver(final MapView mapView,
                                final Context context) {
        super(mapView);

        templateView = PluginLayoutInflater.inflate(context,
                R.layout.main_layout, null);

        peripheralLogTextView = templateView.findViewById(R.id.textView);
        centralLogTextView = templateView.findViewById(R.id.serverLogs);
        startScanButton = templateView.findViewById(R.id.startScanButton);
        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                bleScannerThread = new Thread(new MainDropDownReceiver.BLEScanner());
                bleScannerThread.start();
                startScanButton.setEnabled(false);
            }
        });

        disconnectButton = templateView.findViewById(R.id.disconnectButton);
        disconnectButton.setEnabled(false);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mBluetoothGatt != null) {
                    mBluetoothGatt.disconnect();
                    mBluetoothGatt = null;
                }
            }
        });

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

        serverStatusTextView = templateView.findViewById(R.id.serverStatus);
        serverStatusTextView.setText(SERVER_STATUS_DOWN);
        serverStatusTextView.setTextColor(Color.RED);

        connectionStatusTextView = templateView.findViewById(R.id.remoteConnectionStatus);
        connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
        connectionStatusTextView.setTextColor(Color.RED);

        handler = new Handler(Looper.getMainLooper());

        this.peripheralLoggerThread = new Thread(new PeripheralLogger());
        this.peripheralLoggerThread.start();
        this.centralLoggerThread = new Thread(new CentralLogger());
        this.centralLoggerThread.start();

        mBluetoothManager = (BluetoothManager) MapView.getMapView().getContext().getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Log.e(TAG, "NO BLUETOOTH SUPPORT!");
            centralLogMessages.add("FOUND THAT THERE WAS NO BLUETOOTH SUPPORT!!!:");
            return;
        } else {
            centralLogMessages.add("Found that there was bluetooth support.");
            Log.d(TAG, "found that there was bluetooth support");
        }

        // Register for system Bluetooth events
        AtakBroadcast.DocumentedIntentFilter filter = new AtakBroadcast.DocumentedIntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        AtakBroadcast.getInstance().registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
            centralLogMessages.add("Enabling bluetooth...");
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            centralLogMessages.add("Bluetooth enabled, starting services...");
            startAdvertising();
            startServer();

            mBluetoothScanner = mBluetoothManager.getAdapter().getBluetoothLeScanner();
        }

        Log.d(TAG, "Finished bluetooth related setup.");

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
        notifyRegisteredDevices(newCotSubstring);
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

    class BLEScanner implements Runnable {

        ScanCallback leScanCallback = new ScanCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d(TAG, "Got callbacktype: " + callbackType);
                Log.d(TAG, "Found device with address " + result.getDevice().getAddress());
                List<UUID> serviceUUIDs = BLEUtil.getServiceUUIDsList(result);
                Log.d(TAG, "Device had service uuids: " + serviceUUIDs);
                if (serviceUUIDs.contains(TimeProfile.TIME_SERVICE)) {
                    peripheralLogMessages.add("Found device with service uuid " + TimeProfile.TIME_SERVICE);
                    mBluetoothScanner.stopScan(this);

                    if (mBluetoothGatt == null) {
                        peripheralLogMessages.add("Found that no connection has been made yet, connecting to device.");
                        mBluetoothGatt = result.getDevice().connectGatt(MapView.getMapView().getContext(), false, btleGattCallback);
                    }
                }
            }
        };

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            Log.d(TAG, "Starting scan for ble devices");
            peripheralLogMessages.add("Starting scan for BLE devices...");

            mBluetoothScanner.startScan(leScanCallback);

            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted", e);
            }

            mBluetoothScanner.stopScan(leScanCallback);
            Log.d(TAG, "Stopping scan for ble devices");
            peripheralLogMessages.add("Stopping scan for ble devices.");
            if (mBluetoothGatt == null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        startScanButton.setEnabled(true);
                    }
                });

            }

        }

    }

    /**
     * Verify the level of Bluetooth support provided by the hardware.
     * @param bluetoothAdapter System {@link BluetoothAdapter}.
     * @return true if Bluetooth is properly supported, false otherwise.
     */
    private boolean checkBluetoothSupport(BluetoothAdapter bluetoothAdapter) {

        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth is not supported");
            return false;
        }

        if (!MapView.getMapView().getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.w(TAG, "Bluetooth LE is not supported");
            return false;
        }

        return true;
    }

    /**
     * Listens for Bluetooth adapter events to enable/disable
     * advertising and server functionality.
     */
    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    startAdvertising();
                    startServer();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    stopServer();
                    stopAdvertising();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    @SuppressLint("MissingPermission")
    private void startAdvertising() {
        Log.d(TAG, "starting advertising");
        centralLogMessages.add("Starting BLE advertising");
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(TimeProfile.TIME_SERVICE))
                .build();

        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
    }

    /**
     * Stop Bluetooth advertisements.
     */
    @SuppressLint("MissingPermission")
    private void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    @SuppressLint("MissingPermission")
    private void startServer() {
        Log.d(TAG, "starting server");
        centralLogMessages.add("Starting BLE central server...");
        mBluetoothGattServer = mBluetoothManager.openGattServer(MapView.getMapView().getContext(), mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            centralLogMessages.add("Unable to start GATT server.");
            return;
        } else {
            centralLogMessages.add("Successfully started BLE server.");
            handler.post(new Runnable() {
                @Override
                public void run() {
                    serverStatusTextView.setText(SERVER_STATUS_UP);
                    serverStatusTextView.setTextColor(Color.GREEN);
                }
            });

        }

        mBluetoothGattServer.addService(TimeProfile.createTimeService());
        centralLogMessages.add("Added service for data transfer.");

    }

    /**
     * Shut down the GATT server.
     */
    @SuppressLint("MissingPermission")
    private void stopServer() {
        if (mBluetoothGattServer == null) return;

        mBluetoothGattServer.close();
    }

    /**
     * Callback to receive information about the advertisement process.
     */
    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "LE Advertise Started.");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "LE Advertise Failed: "+errorCode);
        }
    };

    /**
     * Send a time service notification to any devices that are subscribed
     * to the characteristic.
     */
    @SuppressLint("MissingPermission")
    private void notifyRegisteredDevices(String newCot) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered");
            return;
        }

        byte[] newCotBytes = newCot.getBytes(StandardCharsets.UTF_8);
        centralLogMessages.add("Updating connected devices with new cot:\n" + newCot);
        centralLogMessages.add("Length of new cot: " + newCotBytes.length);

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                    .getService(TimeProfile.TIME_SERVICE)
                    .getCharacteristic(TimeProfile.CURRENT_TIME);
            timeCharacteristic.setValue(newCotBytes);
            mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                centralLogMessages.add("BluetoothDevice CONNECTED: " + device.getAddress());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTED);
                        connectionStatusTextView.setTextColor(Color.GREEN);
                    }
                });

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                centralLogMessages.add("BluetoothDevice DISCONNECTED: " + device.getAddress());
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatusTextView.setText(REMOTE_DEVICE_NOT_CONNECTED);
                        connectionStatusTextView.setTextColor(Color.RED);
                    }
                });

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                centralLogMessages.add("Detected device in connecting state: " + device.getAddress());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        connectionStatusTextView.setText(REMOTE_DEVICE_CONNECTING);
                        connectionStatusTextView.setTextColor(Color.YELLOW);
                    }
                });

            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();
            if (TimeProfile.LOCAL_TIME_INFO.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read LocalTimeInfo");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        TimeProfile.getLocalTimeInfo(now));
            } else {
                // Invalid characteristic
                Log.w(TAG, "Invalid Characteristic Read: " + characteristic.getUuid());
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                                            BluetoothGattDescriptor descriptor) {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                Log.d(TAG, "Config descriptor read");
                byte[] returnValue;
                if (mRegisteredDevices.contains(device)) {
                    returnValue = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
                } else {
                    returnValue = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
                }
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        returnValue);
            } else {
                Log.w(TAG, "Unknown descriptor read request");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_FAILURE,
                        0,
                        null);
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite, boolean responseNeeded,
                                             int offset, byte[] value) {
            if (TimeProfile.CLIENT_CONFIG.equals(descriptor.getUuid())) {
                if (Arrays.equals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Subscribe device to notifications: " + device);
                    mRegisteredDevices.add(device);
                } else if (Arrays.equals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE, value)) {
                    Log.d(TAG, "Unsubscribe device from notifications: " + device);
                    mRegisteredDevices.remove(device);
                }

                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            0,
                            null);
                }
            } else {
                Log.w(TAG, "Unknown descriptor write request");
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device,
                            requestId,
                            BluetoothGatt.GATT_FAILURE,
                            0,
                            null);
                }
            }
        }
    };

    // Device connect call back
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation

            String receivedValue = characteristic.getStringValue(0);

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

            //peripheralLogMessages.add("Current value of receivedCot: " + receivedCot);

            //mBluetoothGatt.readCharacteristic(characteristic);
            //peripheralLogMessages.add("Read characteristic.");
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            System.out.println(newState);
            switch (newState) {
                case 0:
                    peripheralLogMessages.add("Disconnected from device.");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            disconnectButton.setEnabled(false);
                            startScanButton.setEnabled(true);
                        }
                    });
                    break;
                case 2:
                    peripheralLogMessages.add("Connected to device with address: " + gatt.getDevice().getAddress());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            disconnectButton.setEnabled(true);
                        }
                    });

                    // discover services and characteristics for this device
                    mBluetoothGatt.discoverServices();

                    break;
                default:
                    peripheralLogMessages.add("Encountered unknown state with device!");
                    break;
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                peripheralLogMessages.add("Discovered services for connected device.");
                List<UUID> serviceUUIDs = new ArrayList<>();
                for (BluetoothGattService service : mBluetoothGatt.getServices()) {
                    peripheralLogMessages.add("Got service with uuid " + service.getUuid());
                    serviceUUIDs.add(service.getUuid());
                    if (service.getUuid().equals(TimeProfile.TIME_SERVICE)) {
                        peripheralLogMessages.add("Found data transfer service, trying to read data...");
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(TimeProfile.CURRENT_TIME);
                        if (characteristic != null) {
                            peripheralLogMessages.add("Found data transfer characteristic, trying to subscribe to notifications...");
                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                            // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
                            UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mBluetoothGatt.writeDescriptor(descriptor);
                        } else {
                            peripheralLogMessages.add("Did not find data transfer characteristic...");
                        }
                    }
                }
            } else {
                peripheralLogMessages.add("Failed to discover services: " + status);
            }

        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            peripheralLogMessages.add("onCharacteristicRead with status " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                peripheralLogMessages.add("Successfully read from characteristic with uuid " + characteristic.getUuid());
                peripheralLogMessages.add("Got value: " + characteristic.getStringValue(0));
                Log.d(TAG, "Got value: " + characteristic.getStringValue(0));
            }
        }
    };

}
