
package com.atakmap.android.ble_forwarder;

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
import com.atakmap.android.ble_forwarder.util.BLEUtil;
import com.atakmap.android.ble_forwarder.util.DateUtil;
import com.atakmap.android.dropdown.DropDown;
import com.atakmap.android.dropdown.DropDownReceiver;
import com.atakmap.android.ipc.AtakBroadcast;
import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

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

    public static final int SERVERPORT1 = 8089;

    Thread cotServerThread = null;
    Thread httpServerThread = null;
    Thread cotdequeuerThread = null;
    Thread bleScannerThread = null;

    String startDelimiterString = "<?xml version=\"1.0\"";
    byte[] delimiter = { '<', '/', 'e', 'v', 'e', 'n', 't', '>'};
    String delimiterString = new String(delimiter, StandardCharsets.UTF_8);

    boolean includeDelimiter = true;
    boolean ignoreNewlineErrors = true;

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

    public static Queue<String> newCotQueue = new ArrayBlockingQueue<>(1000);
    public static Queue<String> newHttpQueue = new ArrayBlockingQueue<>(1000);

    public static Queue<String> outgoingCotQueue = new ArrayBlockingQueue<>(1000);
    public static Queue<String> outgoingHttpQueue = new ArrayBlockingQueue<>(1000);

    public static Queue<String> peripheralLogMessages = new ArrayBlockingQueue<>(1000);
    public static Queue<String> centralLogMessages = new ArrayBlockingQueue<>(1000);

    public static String lastCot = "";

    private final static String TAK_RESPONSE_TYPE = "t-x-takp-r";
    private final static String TAK_PING_TYPE = "t-x-c-t";
    private final static int TIMEOUT_MILLIS = 60000;

    public static final int MAX_ATT_MTU = 517;

    public static final int READ_SIZE = 20;

    public static String receivedCot = "";

    /**************************** CONSTRUCTOR *****************************/

    @SuppressLint("MissingPermission")
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

        this.cotServerThread = new Thread(new CoTServerThread(SERVERPORT1));
        this.cotServerThread.start();
        this.httpServerThread = new Thread(new HttpServerThread(8080));
        httpServerThread.start();
        this.cotdequeuerThread = new Thread(new NewCotDequeuer());
        this.cotdequeuerThread.start();


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

        UUID[] serviceUUIDsToScan = new UUID[] { TimeProfile.TIME_SERVICE };

        @SuppressLint("MissingPermission")
        @Override
        public void run() {
            Log.d(TAG, "Starting scan for ble devices");
            peripheralLogMessages.add("Starting scan for BLE devices...");

//            List<ScanFilter> filters = new ArrayList<>();
//            for (UUID uuid : serviceUUIDsToScan) {
//                filters.add(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(uuid)).build());
//            }
//
//            ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build();
//
//            // ScanFilter doesn't work with startScan() if there are too many - more than 63bits - ignore
//            // bits. So we call startScan() without a scan filter for base/mask uuids and match scan results
//            // against it.
//            final ScanFilter matcher = new ScanFilter.Builder().setServiceUuid(mScanBaseUuid, mScanMaskUuid).build();

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

    class NewCotDequeuer implements Runnable {

        @Override
        public void run() {
            while (true) {
                String newCot = newCotQueue.poll();
                if (newCot != null) {
                    centralLogMessages.add("Got new cot from local ATAK");
                    Log.d(TAG, "dequeuing new cot: " + newCot);
                    for (int i = 0; i < lastCot.length(); i += READ_SIZE) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {                // ignore this - if we are getting data that is not the startDelimiterString and receivedCot is empty,
                            // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                            // just ignore all this data
                            peripheralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                            Log.e(TAG, "interrupted", e);
                        }
                        int lastIndex = i + READ_SIZE;
                        if (lastIndex > lastCot.length()) {
                            lastIndex = lastCot.length();
                        }
                        notifyRegisteredDevices(lastCot.substring(i, lastIndex));

                    }

                }
            }
        }
    }

    class CoTServerThread implements Runnable {

        private final String TAG = CoTServerThread.class.getSimpleName();

        int port;

        public CoTServerThread(int port) {
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

                    // input processing

                    //Log.d(TAG, "Trying to read string from connection...");
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
                            String type = "";
                            String uid = "";
                            for (int i = 0; i < nodeList.getLength(); i++) {
                                if (i == 0) {
                                    Node n = nodeList.item(i);
                                    Element e = (Element) n;
                                    type = e.getAttribute("type");
                                    //Log.d(TAG, "Got CoT with type: " + type);
                                    uid = e.getAttribute("uid");
                                    //Log.d(TAG, "Got uid for coT: " + uid);

                                    if (type.equals(TAK_PING_TYPE)) {
                                        long millis = System.currentTimeMillis();
                                        String startAndTime = DateUtil.toCotTime(millis);
                                        String stale = DateUtil.toCotTime(millis + TIMEOUT_MILLIS);

                                        String response = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?>" +
                                                "<event version='2.0' uid='" + uid + "' type='" + TAK_RESPONSE_TYPE +"' time='"
                                                + startAndTime + "' start='" + startAndTime + "' stale='" + stale + "' how='m-g'>" +
                                                "<point lat='0.0' lon='0.0' hae='0.0' ce='999999' le='999999'/>" +
                                                "<detail><TakControl><TakResponse status='" + true + "'/></TakControl></detail>" +
                                                "</event>";

                                        OutputStream os = socket.getOutputStream();
                                        os.write(response.getBytes(StandardCharsets.UTF_8));
                                    }

                                }
                            }

                            if (!type.equals(TAK_PING_TYPE)) {
                                newCotQueue.add(newCot);
                                lastCot = newCot;
                            }
                            Log.d(TAG, "Read message from connection: " + newCot);

                        }
                    } catch (Exception e) {
                        Log.e(TAG, "error reading message from input stream", e);
                    }

                    // output processing

                    if (!outgoingCotQueue.isEmpty()) {
                        OutputStream os = socket.getOutputStream();
                        while (!outgoingCotQueue.isEmpty()) {
                            String outgoingCot = outgoingCotQueue.poll();
                            if (outgoingCot != null) {
                                os.write(outgoingCot.getBytes(StandardCharsets.UTF_8));
                                peripheralLogMessages.add("Wrote cot received over BLE to local ATAK.");
                            }
                        }
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

    class NewHttpDequeuer implements Runnable {

        @Override
        public void run() {
            while (true) {
                String newHttp = newHttpQueue.poll();
                if (newHttp != null) {
                    centralLogMessages.add("Got new http string from local ATAK");
                    Log.d(TAG, "dequeuing new http string: " + newHttp);
                    for (int i = 0; i < lastCot.length(); i += READ_SIZE) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {                // ignore this - if we are getting data that is not the startDelimiterString and receivedCot is empty,
                            // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                            // just ignore all this data
                            peripheralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                            Log.e(TAG, "interrupted", e);
                        }
                        int lastIndex = i + READ_SIZE;
                        if (lastIndex > lastCot.length()) {
                            lastIndex = lastCot.length();
                        }
                        notifyRegisteredDevices(lastCot.substring(i, lastIndex));

                    }

                }
            }
        }
    }

    class HttpServerThread implements Runnable {

        private final String TAG = HttpServerThread.class.getSimpleName();

        int port;

        public HttpServerThread(int port) {
            this.port = port;
        }

        public void run() {

            Log.d(TAG, "Running http server thread.");

            Socket socket = null;
            try {
                serverSocket = new ServerSocket(port);
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }

            Log.d(TAG, "Created server socket on port " + port);

            try {

                socket = serverSocket.accept();

                Log.d(TAG, "Accepted server connection on port " + port);

                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

                while (!Thread.currentThread().isInterrupted()) {

                    // input processing

                    //Log.d(TAG, "Trying to read string from connection...");
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                        StringBuilder result = new StringBuilder();
                        String line;
                        while((line = reader.readLine()) != null) {
                            result.append(line);
                        }
                        if (!result.toString().isEmpty()) {
                            Log.d(TAG, "Read string from http connection: " + result.toString());
                            if (result.toString().startsWith("GET /Marti/api/clientEndPoints")) {
                                outgoingHttpQueue.add(
                                        "HTTP/1.1 200 OK" + "\r\n" +
                                        "Date: " + DateUtil.formatHttpDate(System.currentTimeMillis()) + "\r\n" +
                                        "Content-Type: application/json; charset=utf-8" + "\r\n" +
                                        "Keep-Alive: " + "timeout=60" + "\r\n" +
                                        "Transfer-Encoding: " + "chunked" + "\r\n" +
                                        "Connection: " + "keep-alive" + "\r\n" +
                                        "\r\n" +
                                        "{\"version\":\"3\",\"type\":\"com.bbn.marti.remote.ClientEndpoint\",\"data\":[]," +
                                                "\"nodeId\":\"e6ec3550334a41aeb08b06e9578ea212\"}" + "\r\n\r\n");
                            } else if (result.toString().startsWith("GET /Marti/api/version/config")) {
                                outgoingHttpQueue.add(
                                        "HTTP/1.1 200 OK" + "\r\n" +
                                        "Date: " + DateUtil.formatHttpDate(System.currentTimeMillis()) + "\r\n" +
                                        "Content-Type: application/json; charset=utf-8" + "\r\n" +
                                        "Keep-Alive: " + "timeout=60" + "\r\n" +
                                        "Transfer-Encoding: " + "chunked" + "\r\n" +
                                        "Connection: " + "keep-alive" + "\r\n" +
                                        "\r\n" +
                                        "{\"version\":\"3\",\"type\":\"ServerConfig\",\"data\":{\"version\":\"4.5.38-RELEASE\",\"api\":\"3\"," +
                                                "\"hostname\":\"localhost\"},\"nodeId\":\"e6ec3550334a41aeb08b06e9578ea212\"}" + "\r\n\r\n");
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "error reading message from input stream", e);
                    }

                    // output processing

                    if (!outgoingHttpQueue.isEmpty()) {
                        OutputStream os = socket.getOutputStream();
                        while (!outgoingHttpQueue.isEmpty()) {
                            String outgoingHttp = outgoingHttpQueue.poll();
                            if (outgoingHttp != null) {
                                os.write(outgoingHttp.getBytes(StandardCharsets.UTF_8));
                                peripheralLogMessages.add("Wrote HTTP received over BLE to local ATAK: " + outgoingHttp);
                            }
                        }
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
            if (TimeProfile.CURRENT_TIME.equals(characteristic.getUuid())) {
//                centralLogMessages.add("Got characteristic read request for current time.");
//                Log.i(TAG, "Read CurrentTime");
//                mBluetoothGattServer.sendResponse(device,
//                        requestId,
//                        BluetoothGatt.GATT_SUCCESS,
//                        0,
//                        lastCot.getBytes(StandardCharsets.UTF_8));
//                super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
//                Log.i(TAG, "onCharacteristicReadRequest " + characteristic.getUuid().toString());

//
//                byte[] fullValue = lastCot.getBytes(StandardCharsets.UTF_8);
//                centralLogMessages.add("Length of lastCot: " + fullValue.length);
//
//                //check
//                if (offset > fullValue.length) {
//                    mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, new byte[]{0} );
//                    centralLogMessages.add("Returning response for offset read greater than length of lastCot");
//                    return;
//
//                }
//
//                int size = fullValue.length - offset;
//                byte[] response = new byte[size];
//
//                for (int i = offset; i < fullValue.length; i++) {
//                    response[i - offset] = fullValue[i];
//                }
//
//                centralLogMessages.add("Sending response for read from offset " + offset);
//                mBluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, response);

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

            if (receivedValue.startsWith(startDelimiterString)) {
                peripheralLogMessages.add("Got start of CoT.");
                receivedCot = startDelimiterString + " ";
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

                    if (receivedCot.startsWith(startDelimiterString) && receivedCot.endsWith(delimiterString)) {
                        peripheralLogMessages.add("Received full cot: " + receivedCot);
                        outgoingCotQueue.add(new String(receivedCot));
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

                //gatt.requestMtu(MAX_ATT_MTU);

                // this will get called after the client initiates a 			BluetoothGatt.discoverServices() call
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
//                        mBluetoothGatt.readCharacteristic(characteristic);
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

//        @Override
//        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                peripheralLogMessages.add("Successfully negotiated mtu of " + MAX_ATT_MTU);
//            }
//        }
    };

}
