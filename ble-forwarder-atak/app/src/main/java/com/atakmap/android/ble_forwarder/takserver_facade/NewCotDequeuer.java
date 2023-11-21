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

package com.atakmap.android.ble_forwarder.takserver_facade;

import static com.atakmap.android.ble_forwarder.util.CotUtils.COT_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.END_DELIMITER_STRING;
import static com.atakmap.android.ble_forwarder.util.CotUtils.START_DELIMITER_STRING;

import com.atakmap.android.ble_forwarder.ble.TAKBLEManager;
import com.atakmap.android.ble_forwarder.plugin.PluginTemplate;
import com.atakmap.android.ble_forwarder.proto.ProtoBufUtils;
import com.atakmap.android.ble_forwarder.proto.generated.Cotevent;
import com.atakmap.coremap.log.Log;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class NewCotDequeuer implements Runnable {

    public interface NewCotDequeuedCallback {
        void newCotSubstringDequeuedForCentrals(byte[] data);

        void newCotDequeuedForPeripheral(byte[] data);
    }

    private int blePacketAppDataSize = -1;

    public static final String TAG = NewCotDequeuer.class.getSimpleName();

    Queue<String> peripheralLogMessages;
    Queue<String> centralLogMessages;
    NewCotDequeuedCallback callback;
    ArrayBlockingQueue<String> newCotQueue = new ArrayBlockingQueue<>(1000);
    PluginTemplate.DEVICE_MODE deviceMode = PluginTemplate.DEVICE_MODE.NONE_SELECTED;

    public NewCotDequeuer(NewCotDequeuedCallback callback,
                          Queue<String> peripheralLogMessages,
                          Queue<String> centralLogMessages) {
        this.callback = callback;
        this.peripheralLogMessages = peripheralLogMessages;
        this.centralLogMessages = centralLogMessages;
    }

    public void setDeviceMode(PluginTemplate.DEVICE_MODE deviceMode) {
        this.deviceMode = deviceMode;
    }

    public void addNewCotToQueue(String newCot) {
        newCotQueue.add(newCot);
    }

    @Override
    public void run() {
        try {
            while (true) {
                if (blePacketAppDataSize != -1) {
                    String newCot = newCotQueue.take();
                    if (newCot != null) {

                        Log.d(TAG, "got newCot: " + newCot);

                        Cotevent.CotEvent cotEvent = ProtoBufUtils.cot2protoBuf(newCot);
                        if (cotEvent == null) {
                            Log.w(TAG, "Failed to convert cot from xml to proto class");
                            continue;
                        }

                        byte[] cotEventByteArrayNoDelimiters = cotEvent.toByteArray();

//                        try {
//                            Cotevent.CotEvent receivedCotEventProtoBuf = Cotevent.CotEvent.parseFrom(cotEventByteArrayNoDelimiters);
//
//                            String cotEventReparsed = ProtoBufUtils.proto2cot(receivedCotEventProtoBuf);
//
//                            Log.d(TAG, "Got cot event reparsed: " + cotEventReparsed);
//
//
//                        } catch (InvalidProtocolBufferException e) {
//                            Log.w(TAG, "Failed to parse protobuf", e);
//                            continue;
//                        }

                        if (cotEventByteArrayNoDelimiters == null) {
                            Log.w(TAG, "cot event byte array was null");
                            //peripheralLogMessages.add("cot event byte array was null.");
                            continue;
                        }

                        // Convert string constants to byte arrays
                        byte[] startDelimiterBytes = START_DELIMITER_STRING.getBytes(StandardCharsets.UTF_8);
                        byte[] delimiterBytes = END_DELIMITER_STRING.getBytes(StandardCharsets.UTF_8);

                        // Create a new byte array containing the concatenated data
                        byte[] cotEventByteArray = new byte[startDelimiterBytes.length +
                                cotEventByteArrayNoDelimiters.length +
                                delimiterBytes.length];

                        // Copy data into the new array
                        System.arraycopy(startDelimiterBytes, 0, cotEventByteArray, 0, startDelimiterBytes.length);
                        System.arraycopy(cotEventByteArrayNoDelimiters, 0, cotEventByteArray, startDelimiterBytes.length, cotEventByteArrayNoDelimiters.length);
                        System.arraycopy(delimiterBytes, 0, cotEventByteArray, startDelimiterBytes.length + cotEventByteArrayNoDelimiters.length, delimiterBytes.length);

                        Log.d(TAG, "Length of cot event byte array: " + cotEventByteArray.length);
                        Log.d(TAG, "Length of cot event string: " + newCot.length());

                        if (deviceMode.equals(PluginTemplate.DEVICE_MODE.PERIPHERAL_MODE)) {
                            peripheralLogMessages.add("Got new cot from local ATAK.\n" +
                                    "Size before protobuf serialization: " + newCot.length() + "\n" +
                                    "Size after protobuf serialization: " + cotEventByteArray.length);
                            Log.d(TAG, "dequeuing new cot: " + newCot);
                            for (int i = 0; i < cotEventByteArray.length; i += blePacketAppDataSize) {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    // ignore this - if we are getting data that is not the startDelimiterString and receivedCotString is empty,
                                    // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                                    // just ignore all this data
                                    //centralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                                    Log.e(TAG, "interrupted", e);
                                }
                                int lastIndex = i + blePacketAppDataSize;
                                if (lastIndex > cotEventByteArray.length) {
                                    lastIndex = cotEventByteArray.length;
                                }
                                byte[] subArray = Arrays.copyOfRange(cotEventByteArray, i, lastIndex);
                                Log.d(TAG, "Dequeueing new cot subarray from index " + i + " to " + lastIndex);
                                callback.newCotSubstringDequeuedForCentrals(subArray);
                            }
                        } else if (deviceMode.equals(PluginTemplate.DEVICE_MODE.CENTRAL_MODE)) {
                            centralLogMessages.add("Got new cot from local ATAK.\n" +
                                    "Size before protobuf serialization: " + newCot.length() + "\n" +
                                    "Size after protobuf serialization: " + cotEventByteArray.length);
                            Log.d(TAG, "dequeuing new cot: " + newCot);
                            for (int i = 0; i < cotEventByteArray.length; i += blePacketAppDataSize) {
                                try {
                                    Thread.sleep(750);
                                } catch (InterruptedException e) {
                                    // ignore this - if we are getting data that is not the startDelimiterString and receivedCotString is empty,
                                    // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                                    // just ignore all this data
                                    //centralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                                    Log.e(TAG, "interrupted", e);
                                }
                                int lastIndex = i + blePacketAppDataSize;
                                if (lastIndex > cotEventByteArray.length) {
                                    lastIndex = cotEventByteArray.length;
                                }
                                byte[] subArray = Arrays.copyOfRange(cotEventByteArray, i, lastIndex);
                                Log.d(TAG, "Dequeueing new cot subarray from index " + i + " to " + lastIndex);
                                callback.newCotDequeuedForPeripheral(subArray);
                            }

                        }

                    }
                } else {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Not trying to deqeue CoT's for sending until mtu negotiated...");
                    }
                }
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "Error in new cot dequeuer loop", e);
        }
    }

    public void setMtu(int mtu) {
        blePacketAppDataSize = mtu - TAKBLEManager.BLE_HEADER_SIZE;
    }
}

