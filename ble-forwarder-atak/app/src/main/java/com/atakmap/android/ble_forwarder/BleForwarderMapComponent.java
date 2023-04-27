
package com.atakmap.android.ble_forwarder;

import static android.content.Context.BLUETOOTH_SERVICE;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.text.format.DateFormat;

import com.atakmap.android.ipc.AtakBroadcast.DocumentedIntentFilter;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.dropdown.DropDownMapComponent;

import com.atakmap.coremap.log.Log;
import com.atakmap.android.ble_forwarder.plugin.R;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class BleForwarderMapComponent extends DropDownMapComponent {

    private static final String TAG = "DinoMapComponent";

    public static Context pluginContext;

    private Timer dueTimeTimer = new Timer();

    private MainDropDownReceiver mainDdr = null;

    private ServerSocket serverSocket;

    public static final int SERVERPORT1 = 8089;

    Thread serverThread = null;
    Thread cotdequeuerThread = null;

    byte[] delimiter = { '<', '/', 'e', 'v', 'e', 'n', 't', '>'};

    boolean includeDelimiter = true;
    boolean ignoreNewlineErrors = true;

    /* Bluetooth API */
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mBluetoothGattServer;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    /* Collection of notification subscribers */
    private Set<BluetoothDevice> mRegisteredDevices = new HashSet<>();
    public static Queue<String> newCotQueue = new ArrayBlockingQueue<>(1000);

    public static String lastCot = "";

    @SuppressLint("MissingPermission")
    public void onCreate(final Context context, Intent intent,
                         final MapView view) {

        Log.d(TAG, "Creating the dino plugin thing.");

        mBluetoothManager = (BluetoothManager) MapView.getMapView().getContext().getSystemService(BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = mBluetoothManager.getAdapter();
        // We can't continue without proper Bluetooth support
        if (!checkBluetoothSupport(bluetoothAdapter)) {
            Log.e(TAG, "NO BLUETOOTH SUPPORT!");
            return;
        } else {
            Log.d(TAG, "found that there was bluetooth support");
        }

        // Register for system Bluetooth events
        DocumentedIntentFilter filter = new DocumentedIntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(context, mBluetoothReceiver, filter);
        if (!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth is currently disabled...enabling");
            bluetoothAdapter.enable();
        } else {
            Log.d(TAG, "Bluetooth enabled...starting services");
            startAdvertising();
            startServer();
        }

        Log.d(TAG, "Finished bluetooth related setup.");

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
        this.cotdequeuerThread = new Thread(new NewCotDequeuer());
        this.cotdequeuerThread.start();

    }

    class NewCotDequeuer implements Runnable {

        @Override
        public void run() {
            while (true) {
                String newCot = newCotQueue.poll();
                if (newCot != null) {
                    Log.d(TAG, "dequeuing new cot: " + newCot);
                    notifyRegisteredDevices(newCot);
                }
            }
        }
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

                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

                while (!Thread.currentThread().isInterrupted()) {

                    Log.d(TAG, "Trying to read string from connection...");
                    try {
                        byte[] msg = readMessage(in);
                        if (msg != null) {
                            String newCot = new String(msg, StandardCharsets.UTF_8);

                            DocumentBuilderFactory factory =
                                    DocumentBuilderFactory.newInstance();
                            DocumentBuilder builder = factory.newDocumentBuilder();
                            StringBuilder xmlStringBuilder = new StringBuilder();
                            xmlStringBuilder.append(newCot);
                            ByteArrayInputStream input =  new ByteArrayInputStream(
                                    xmlStringBuilder.toString().getBytes("UTF-8"));
                            Document doc = builder.parse(input);
                            XPath xPath =  XPathFactory.newInstance().newXPath();
                            String expression = "/event";
                            NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(
                                    doc, XPathConstants.NODESET);
                            for (int i = 0; i < nodeList.getLength(); i++) {
                                if (i == 0) {
                                    Node n = nodeList.item(i);
                                    Element e = (Element) n;
                                    String type = e.getAttribute("type");
                                    Log.d(TAG, "Got CoT with type: " + type);
                                }

                            }

                            newCotQueue.add(newCot);
                            lastCot = newCot;
                            Log.d(TAG, "Read message from connection: " + newCot);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "error reading message from input stream", e);
                    }

                    Thread.sleep(1000);
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
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

    public byte[] readMessage(InputStream in) throws Exception {

        ByteArrayOutputStream msgBuf = new ByteArrayOutputStream();
        int msgByte; // current byte read from input stream
        int dlmIndex = 0; // current byte in the delimiter
        ByteArrayOutputStream dlmBuf = new ByteArrayOutputStream(); // stores the delimiter as we're reading it
        // ByteArrayOutputStream msgBuf = new ByteArrayOutputStream(); // stores the message
        boolean foundDelimiter = false;

        while ((msgByte = in.read()) != -1) {  // throws IOException, blocks
            /*if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Read: '" + (char) msgByte + "'");
                LOGGER.debug("Read String: " + (char) msgByte + ", Hex: " + DatatypeConverter.printByte((byte) msgByte));
            }*/

            //** A rather odd hack. Suppose the byte -66 arrives as an integer in the range [0-255]
            // like this: 00000000 00000000 00000000 10111110 (+190)
            // I then cast it to a byte, which changes the interpretation to a negative number
            // in two's complement: 10111110 (-66)
            // I then cast it back to an int, which causes sign extension:
            // 11111111 11111111 11111111 10111110 (-66)
            msgByte = (byte) msgByte;

            if (msgByte == this.delimiter[dlmIndex]) {
                ++dlmIndex;
                dlmBuf.write(msgByte);
            } else {
                dlmIndex = 0;
                msgBuf.write(dlmBuf.toByteArray());
                msgBuf.write(msgByte);
                dlmBuf.reset();
            }
            if (Arrays.equals(dlmBuf.toByteArray(), this.delimiter)) {
                foundDelimiter = true;
                if (this.includeDelimiter) {
                    msgBuf.write(dlmBuf.toByteArray()); // either dlmBuf or delimiter should be fine here.
                }
                break;
            }
        }

        byte [] result = msgBuf.toByteArray();
        if (!foundDelimiter && result.length == 0) {
            return null;
        } else if (!foundDelimiter) {
            if (this.ignoreNewlineErrors && msgBuf.toByteArray().length == 1 && msgBuf.toByteArray()[0] == 10) {
            // ** if we got just a single newline character and then closed the stream, don't interpret this as an error (if ignoreNewlineErrors==true)
            return null;
        }
            // We hit the end of the stream without finding a delimiter.
            // Considering this an error.
            throw new Exception("No delimiter found. Message size: "
                    + result.length + ", Message (hex): '" + bytesToHex(result) +  "'");
        } else {
            return result;
        }

    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
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
                .addServiceUuid(new ParcelUuid(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")))
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
        mBluetoothGattServer = mBluetoothManager.openGattServer(MapView.getMapView().getContext(), mGattServerCallback);
        if (mBluetoothGattServer == null) {
            Log.w(TAG, "Unable to create GATT server");
            return;
        }

        mBluetoothGattServer.addService(TimeProfile.createTimeService());

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

        Log.i(TAG, "Sending update to " + mRegisteredDevices.size() + " subscribers");
        for (BluetoothDevice device : mRegisteredDevices) {
            BluetoothGattCharacteristic timeCharacteristic = mBluetoothGattServer
                    .getService(TimeProfile.TIME_SERVICE)
                    .getCharacteristic(TimeProfile.CURRENT_TIME);
            timeCharacteristic.setValue(newCot.getBytes(StandardCharsets.UTF_8));
            mBluetoothGattServer.notifyCharacteristicChanged(device, timeCharacteristic, false);
        }
    }

    /**
     * Callback to handle incoming requests to the GATT server.
     * All read/write requests for characteristics and descriptors are handled here.
     */
    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "BluetoothDevice CONNECTED: " + device);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "BluetoothDevice DISCONNECTED: " + device);
                //Remove device from any active subscriptions
                mRegisteredDevices.remove(device);
            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d(TAG, "Detected device in connecting state.");
            }
        }

        @Override
        @SuppressLint("MissingPermission")
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            long now = System.currentTimeMillis();
            if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid())) {
                Log.i(TAG, "Read CurrentTime");
                mBluetoothGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        lastCot.getBytes(StandardCharsets.UTF_8));
            } else if (TimeProfile.LOCAL_TIME_INFO.equals(characteristic.getUuid())) {
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

//    public static CotEventContainer buildProtocolResponse(boolean status, String negotiationUuid) throws DocumentException {
//
//        if (log.isDebugEnabled()) {
//            log.debug("buildProtocolResponse for : " + negotiationUuid + ", " + status);
//        }
//
//        long millis = System.currentTimeMillis();
//        String startAndTime = DateUtil.toCotTime(millis);
//        String stale = DateUtil.toCotTime(millis + TIMEOUT_MILLIS);
//
//        String response = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>" +
//                "<event version='2.0' uid='" + negotiationUuid + "' type='" + TAK_RESPONSE_TYPE +"' time='"
//                + startAndTime + "' start='" + startAndTime + "' stale='" + stale + "' how='m-g'>" +
//                "<point lat='0.0' lon='0.0' hae='0.0' ce='999999' le='999999'/>" +
//                "<detail><TakControl><TakResponse status='" + status + "'/></TakControl></detail>" +
//                "</event>";
//
//        SAXReader reader = new SAXReader();
//        Document doc = reader.read(new ByteArrayInputStream(response.getBytes()));
//        CotEventContainer cotEventContainer = new CotEventContainer(doc);
//        return cotEventContainer;
//    }
}
