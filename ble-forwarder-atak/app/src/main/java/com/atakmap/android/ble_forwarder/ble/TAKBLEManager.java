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

package com.atakmap.android.ble_forwarder.ble;

import static android.content.Context.BLUETOOTH_SERVICE;

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

    public static final int DEFAULT_MTU_SIZE = 512;

    // I've found that there are issues when sending the MTU between devices - if
    // devices send less than the MTU there are no issues, so I have been making
    // the central send messages with READ_VS_MTU_DISCREPANCY less than the MTU size
    public static final int READ_VS_MTU_DISCREPANCY = 10;

    private int currentMtu = DEFAULT_MTU_SIZE;
    private int currentReadSize = -1;

    public interface TAKBLEManagerCallbacks {

        // the BLE peripheral server hosted on this device is up
        void peripheralUp();

        // remote central is connected to peripheral hosted on this device
        void remoteCentralConnected();
        // remote central is connecting to peripheral hosted on this device
        void remoteCentralConnecting();
        // remote central is disconnected from peripheral hosted on this device
        void remoteCentralDisconnected();

        // central on this device is disconnected from peripheral hosted on other device
        void disconnectedFromRemotePeripheral();
        // central on this device is connected to peripheral hosted on other device
        void connectedToRemotePeripheral();

        void scanFinished();

        void receivedStringOverBLE(String receivedValue);

        void newMtuNegotiated(int newMtu);

        void bluetoothAdapterOn();

        void startedAdvertising();

        void stoppedAdvertising();
    }

    Queue<String> centralLogMessages;
    Queue<String> peripheralLogMessages;
    TAKBLEManagerCallbacks callbacks;

    public TAKBLEManager(Queue<String> centralLogMessages, Queue<String> peripheralLogMessages,
                         TAKBLEManagerCallbacks callbacks) {
        this.centralLogMessages = centralLogMessages;
        this.peripheralLogMessages = peripheralLogMessages;
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
    boolean isAdvertising = false;
    boolean bluetoothAdapterOn = false;

    @SuppressLint("MissingPermission")
    public boolean initialize() {
        mBluetoothManager = (BluetoothManager) MapView.getMapView().getContext().getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Log.e(TAG, "NO BLUETOOTH SUPPORT!");
            peripheralLogMessages.add("FOUND THAT THERE WAS NO BLUETOOTH SUPPORT!!!:");
            return false;
        } else {
            peripheralLogMessages.add("Found that there was bluetooth support.");
            Log.d(TAG, "found that there was bluetooth support");
        }

        // Register for system Bluetooth events
        AtakBroadcast.DocumentedIntentFilter filter = new AtakBroadcast.DocumentedIntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        AtakBroadcast.getInstance().registerReceiver(mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
            peripheralLogMessages.add("Enabling bluetooth...");
        } else {
            Log.d(TAG, "Bluetooth enabled.");
            peripheralLogMessages.add("Bluetooth enabled.");
            mBluetoothScanner = mBluetoothManager.getAdapter().getBluetoothLeScanner();
            bluetoothAdapterOn = true;
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

    public void sendDataToConnectedPeripheral(String s) {
        if (mtuNegotiated) {
            sendDataToPeripheral(s);
        } else {
            Log.w(TAG, "Not sending data to peripheral, mtu not negotiated yet!");
        }
    }

    public void sendDataToConnectedCentrals(String s) {
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
                    centralLogMessages.add("Found device with service uuid " + TimeProfile.TIME_SERVICE);
                    mBluetoothScanner.stopScan(this);

                    if (mBluetoothGatt == null) {
                        centralLogMessages.add("Found that no connection has been made yet, connecting to device.");
                        mBluetoothGatt = result.getDevice().connectGatt(MapView.getMapView().getContext(), false, btleGattCallback);
                    }
                }
            }
        };

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            Log.d(TAG, "Starting scan for ble devices");
            centralLogMessages.add("Starting scan for BLE devices...");

            mBluetoothScanner.startScan(leScanCallback);

            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted", e);
            }

            mBluetoothScanner.stopScan(leScanCallback);
            Log.d(TAG, "Stopping scan for ble devices");
            centralLogMessages.add("Stopping scan for ble devices.");
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
                    bluetoothAdapterOn = true;
                    mBluetoothScanner = mBluetoothManager.getAdapter().getBluetoothLeScanner();
                    callbacks.bluetoothAdapterOn();
                    break;
                case BluetoothAdapter.STATE_OFF:
                    bluetoothAdapterOn = false;
                    stopAdvertising();
                    stopServer();
                    break;
                default:
                    // Do nothing
            }

        }
    };

    public boolean isAdvertising() {
        return isAdvertising;
    }

    /**
     * Begin advertising over Bluetooth that this device is connectable
     * and supports the Current Time Service.
     */
    @SuppressLint("MissingPermission")
    public void startAdvertising() {
        Log.d(TAG, "starting advertising");
        peripheralLogMessages.add("Starting BLE advertising");
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        if (mBluetoothLeAdvertiser == null) {
            Log.w(TAG, "Failed to create advertiser");
            return;
        }

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(TimeProfile.TIME_SERVICE))
                .build();

        isAdvertising = true;
        callbacks.startedAdvertising();
        mBluetoothLeAdvertiser
                .startAdvertising(settings, data, mAdvertiseCallback);
    }

    /**
     * Stop Bluetooth advertisements.
     */
    @SuppressLint("MissingPermission")
    public void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null) return;
        isAdvertising = false;
        callbacks.stoppedAdvertising();
        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }

    /**
     * Initialize the GATT server instance with the services/characteristics
     * from the Time Profile.
     */
    @SuppressLint("MissingPermission")
    private void startServer() {
        Log.d(TAG, "starting server");
        peripheralLogMessages.add("Starting BLE peripheral server...");
        mBluetoothGattServer = mBluetoothManager.openGattServer(MapView.getMapView().getContext(), mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            peripheralLogMessages.add("Unable to start GATT server.");
            return;
        } else {
            peripheralLogMessages.add("Successfully started BLE server.");
            callbacks.peripheralUp();
        }

        mBluetoothGattServer.addService(TimeProfile.createTimeService());
        peripheralLogMessages.add("Added service for data transfer.");

    }

    public void startPeripheralServer() {
        if (bluetoothAdapterOn) {
            Log.d(TAG, "Starting peripheral server...");
            peripheralLogMessages.add("Starting peripheral server...");
            startAdvertising();
            startServer();
        } else {
            Log.w(TAG, "Tried to start peripheral server but bluetooth adapter is not on.");
        }
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
        peripheralLogMessages.add("Updating connected devices with new string:\n" + string);
        Log.d(TAG, "Updating connected devices with new string:\n" + string);
        peripheralLogMessages.add("Length of new string: " + stringBytes.length);

        //Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                .getService(TimeProfile.TIME_SERVICE)
                .getCharacteristic(TimeProfile.CURRENT_TIME);
        timeCharacteristic.setValue(stringBytes);

        for (BluetoothDevice device : mRegisteredDevices) {
            mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
        }
    }

    @SuppressLint("MissingPermission")
    private void sendDataToPeripheral(String string) {

        BluetoothGattCharacteristic localTimeCharacteristic =
                mBluetoothGatt.getService(TimeProfile.TIME_SERVICE)
                        .getCharacteristic(TimeProfile.LOCAL_TIME_INFO);

        localTimeCharacteristic.setValue(string);

        mBluetoothGatt.writeCharacteristic(localTimeCharacteristic);

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
                peripheralLogMessages.add("BluetoothDevice CONNECTED: " + device.getAddress());
                callbacks.remoteCentralConnected();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                peripheralLogMessages.add("BluetoothDevice DISCONNECTED: " + device.getAddress());
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
                callbacks.remoteCentralDisconnected();

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                peripheralLogMessages.add("Detected device in connecting state: " + device.getAddress());
                callbacks.remoteCentralConnecting();

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
            callbacks.newMtuNegotiated(mtu);
            currentReadSize = mtu - READ_VS_MTU_DISCREPANCY;
            mtuNegotiated = true;
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

            if (characteristic.getUuid().equals(TimeProfile.LOCAL_TIME_INFO)) {

                Log.d(TAG, "Got write to local time profile.");
                String readValue = new String(value, StandardCharsets.UTF_8);
                Log.d(TAG, "Value received: " + readValue);

                callbacks.receivedStringOverBLE(readValue);

            }

            if (responseNeeded) {
                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
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
                    centralLogMessages.add("Disconnected from device.");
                    callbacks.disconnectedFromRemotePeripheral();
                    break;
                case 2:
                    centralLogMessages.add("Connected to device with address: " + gatt.getDevice().getAddress());
                    callbacks.connectedToRemotePeripheral();

                    // discover services and characteristics for this device
                    mBluetoothGatt.discoverServices();

                    break;
                default:
                    centralLogMessages.add("Encountered unknown state with device!");
                    break;
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {

                Log.d(TAG, "Successfully discovered services for connected device.");
                centralLogMessages.add("Discovered services for connected device.");

                Log.d(TAG, "requesting MTU");
                mBluetoothGatt.requestMtu(currentMtu);

            } else {
                centralLogMessages.add("Failed to discover services: " + status);
            }

        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            centralLogMessages.add("onCharacteristicRead with status " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                centralLogMessages.add("Successfully read from characteristic with uuid " + characteristic.getUuid());
                String readValue = characteristic.getStringValue(0);
                if (readValue.length() > currentReadSize) {
                    readValue = readValue.substring(0, currentReadSize);
                }
                centralLogMessages.add("Got value: " + readValue);
                Log.d(TAG, "Got value: " + readValue);
                callbacks.receivedStringOverBLE(readValue);
            }
        }

        @SuppressLint("MissingPermission")
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully negotiated MTU of " + mtu);
                centralLogMessages.add("Successfully negotiated MTU of " + mtu);
                callbacks.newMtuNegotiated(mtu);
                currentReadSize = mtu - 10;

                mtuNegotiated = true;

                for (BluetoothGattService service : mBluetoothGatt.getServices()) {
                    centralLogMessages.add("Got service with uuid " + service.getUuid());
                    if (service.getUuid().equals(TimeProfile.TIME_SERVICE)) {
                        centralLogMessages.add("Found data transfer service, trying to read data...");
                        BluetoothGattCharacteristic characteristic = service.getCharacteristic(TimeProfile.CURRENT_TIME);
                        if (characteristic != null) {
                            centralLogMessages.add("Found data transfer characteristic, trying to subscribe to notifications...");
                            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                            mBluetoothGatt.setCharacteristicNotification(characteristic, true);
                            // 0x2902 org.bluetooth.descriptor.gatt.client_characteristic_configuration.xml
                            UUID uuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(uuid);
                            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            mBluetoothGatt.writeDescriptor(descriptor);
                        } else {
                            centralLogMessages.add("Did not find data transfer characteristic...");
                        }
                    }
                }

            } else {
                Log.w(TAG, "Failed to negotiate MTU.");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Successfully wrote to local time profile characteristic.");
            } else {
                Log.d(TAG, "Failed to write to local time profile characteristic.");
            }
        }
    };

    public void setMtu(int newMtu) {
        currentMtu = newMtu;
    }

    public boolean isBluetoothAdapterOn() {
        return bluetoothAdapterOn;
    }

}
