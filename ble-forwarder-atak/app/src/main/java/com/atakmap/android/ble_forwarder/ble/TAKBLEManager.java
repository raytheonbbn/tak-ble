package com.atakmap.android.ble_forwarder.ble;

import static android.content.Context.BLUETOOTH_SERVICE;

import static com.atakmap.android.ble_forwarder.takserver_facade.NewCotDequeuer.READ_SIZE;

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
import android.os.ParcelUuid;

import com.atakmap.android.ble_forwarder.TimeProfile;
import com.atakmap.android.ble_forwarder.util.BLEUtil;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public class TAKBLEManager {

    public static final String TAG = TAKBLEManager.class.getSimpleName();

    public interface TAKBLEManagerCallbacks {

        // the BLE central server hosted on this device is up
        void centralUp();

        // remote peripheral is connected to central hosted on this device
        void remotePeripheralConnected();
        // remote peripheral is connecting to central hosted on this device
        void remotePeripheralConnecting();
        // remote peripheral is disconnected from central hosted on this device
        void remotePeripiheralDisconnected();

        // peripheral on this device is disconnected from central hosted on other device
        void disconnectedFromRemoteCentral();
        // peripheral on this device is connected to central hosted on other device
        void connectedToRemoteCentral();

        void scanFinished();

        void receivedStringOverBLE(String receivedValue);
    }

    Queue<String> peripheralLogMessages;
    Queue<String> centralLogMessages;
    TAKBLEManagerCallbacks callbacks;

    public TAKBLEManager(Queue<String> peripheralLogMessages, Queue<String> centralLogMessages,
                         TAKBLEManagerCallbacks callbacks) {
        this.peripheralLogMessages = peripheralLogMessages;
        this.centralLogMessages = centralLogMessages;
        this.callbacks = callbacks;
    }

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothLeScanner mBluetoothScanner;
    private BluetoothGatt mBluetoothGatt;
    /* Collection of notification subscribers */
    private final Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();

    boolean mtuNegotiated = false;

    @SuppressLint("MissingPermission")
    public boolean initialize() {
        mBluetoothManager = (BluetoothManager) MapView.getMapView().getContext().getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Log.e(TAG, "NO BLUETOOTH SUPPORT!");
            centralLogMessages.add("FOUND THAT THERE WAS NO BLUETOOTH SUPPORT!!!:");
            return false;
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

        return true;
    }

    @SuppressLint("MissingPermission")
    public void disconnect() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt = null;
        }
    }

    public void startScanning() {
        Thread bleScannerThread = new Thread(new BLEScanner());
        bleScannerThread.start();
    }

    public void sendDataToConnectedDevices(String s) {
        if (mtuNegotiated) {
            notifyRegisteredDevices(s);
        } else {
            Log.w(TAG, "Not notifying registered devices of new data, mtu not negotiated yet!");
        }
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
                callbacks.scanFinished();
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
    private final BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver() {
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
            callbacks.centralUp();
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
    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
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
    private void notifyRegisteredDevices(String string) {
        if (mRegisteredDevices.isEmpty()) {
            Log.i(TAG, "No subscribers registered");
            return;
        }

        byte[] stringBytes = string.getBytes(StandardCharsets.UTF_8);
        centralLogMessages.add("Updating connected devices with new string:\n" + string);
        Log.d(TAG, "Updating connected devices with new string:\n" + string);
        centralLogMessages.add("Length of new string: " + stringBytes.length);

        //Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                .getService(TimeProfile.TIME_SERVICE)
                .getCharacteristic(TimeProfile.CURRENT_TIME);
        timeCharacteristic.setValue(stringBytes);

        for (BluetoothDevice device : mRegisteredDevices) {
            mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                centralLogMessages.add("BluetoothDevice CONNECTED: " + device.getAddress());
                callbacks.remotePeripheralConnected();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                centralLogMessages.add("BluetoothDevice DISCONNECTED: " + device.getAddress());
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
                callbacks.remotePeripiheralDisconnected();

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                centralLogMessages.add("Detected device in connecting state: " + device.getAddress());
                callbacks.remotePeripheralConnecting();

            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();
            Log.d(TAG, "Got characteristic read request for uuid: " + characteristic.getUuid());
            if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read LocalTimeInfo");
                BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                        .getService(TimeProfile.TIME_SERVICE)
                        .getCharacteristic(TimeProfile.CURRENT_TIME);

                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        timeCharacteristic.getValue());
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

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d(TAG, "Mtu changed!");
            mtuNegotiated = true;
        }
    };

    // Device connect call back
    private final BluetoothGattCallback btleGattCallback = new BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation

            Log.d(TAG, "onCharacteristicChanged notification.");

            //String receivedValue = characteristic.getStringValue(0);

            Log.d(TAG, "Trying to read characteristic.");
            gatt.readCharacteristic(characteristic);

            //callbacks.receivedStringOverBLE(receivedValue);

        }

        @SuppressLint("MissingPermission")
        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            System.out.println(newState);
            switch (newState) {
                case 0:
                    peripheralLogMessages.add("Disconnected from device.");
                    callbacks.disconnectedFromRemoteCentral();
                    break;
                case 2:
                    peripheralLogMessages.add("Connected to device with address: " + gatt.getDevice().getAddress());
                    callbacks.connectedToRemoteCentral();

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

                Log.d(TAG, "Successfully discovered services for connected device.");
                peripheralLogMessages.add("Discovered services for connected device.");

                Log.d(TAG, "requesting MTU");
                mBluetoothGatt.requestMtu(READ_SIZE+1);

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
                String readValue = characteristic.getStringValue(0);
                if (readValue.length() > READ_SIZE) {
                    readValue = readValue.substring(0, READ_SIZE);
                }
                peripheralLogMessages.add("Got value: " + readValue);
                Log.d(TAG, "Got value: " + readValue);
                callbacks.receivedStringOverBLE(readValue);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully negotiated MTU of " + READ_SIZE);

                for (BluetoothGattService service : mBluetoothGatt.getServices()) {
                    peripheralLogMessages.add("Got service with uuid " + service.getUuid());
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
                Log.w(TAG, "Failed to negotiate MTU.");
            }
        }
    };

}
