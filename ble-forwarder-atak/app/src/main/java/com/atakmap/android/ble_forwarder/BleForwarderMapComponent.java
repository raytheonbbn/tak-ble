
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
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.text.format.DateFormat;
import android.widget.TextView;

import com.atakmap.android.ble_forwarder.util.BLEUtil;
import com.atakmap.android.ble_forwarder.util.DateUtil;
import com.atakmap.android.ipc.AtakBroadcast;
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
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
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

    private static final String TAG = BleForwarderMapComponent.class.getSimpleName();

    public static Context pluginContext;

    @SuppressLint("MissingPermission")
    public void onCreate(final Context context, Intent intent,
                         final MapView view) {

        Log.d(TAG, "Creating the ble forwarder plugin.");

        // NOTE: R.style.ATAKPluginTheme does not support TabLayout, so
        // I needed to change the theme to AppCompat.
        context.setTheme(R.style.Theme_AppCompat);
        super.onCreate(context, intent, view);
        pluginContext = context;

        MainDropDownReceiver mainDdr = new MainDropDownReceiver(view, context);

        DocumentedIntentFilter ddFilterMain = new DocumentedIntentFilter();
        ddFilterMain.addAction(MainDropDownReceiver.SHOW_PLUGIN);
        ddFilterMain.addAction(MainDropDownReceiver.REFRESH_MAIN_SCREEN);
        registerDropDownReceiver(mainDdr, ddFilterMain);

    }

}
