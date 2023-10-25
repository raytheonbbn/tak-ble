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

import com.atakmap.android.ble_forwarder.ble.TAKBLEManager;
import com.atakmap.android.ble_forwarder.plugin.PluginTemplate;
import com.atakmap.coremap.log.Log;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class NewCotDequeuer implements Runnable {

    public interface NewCotDequeuedCallback {
        void newCotSubstringDequeuedForCentrals(String newCotSubstring);
        void newCotDequeuedForPeripheral(String newCot);
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
                        if (deviceMode.equals(PluginTemplate.DEVICE_MODE.PERIPHERAL_MODE)) {
                            peripheralLogMessages.add("Got new cot from local ATAK");
                            Log.d(TAG, "dequeuing new cot: " + newCot);
                            for (int i = 0; i < newCot.length(); i += blePacketAppDataSize) {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    // ignore this - if we are getting data that is not the startDelimiterString and receivedCot is empty,
                                    // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                                    // just ignore all this data
                                    centralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                                    Log.e(TAG, "interrupted", e);
                                }
                                int lastIndex = i + blePacketAppDataSize;
                                if (lastIndex > newCot.length()) {
                                    lastIndex = newCot.length();
                                }
                                String cotSubstring = newCot.substring(i, lastIndex);
                                Log.d(TAG, "Dequeueing new cot substring: " + cotSubstring);
                                callback.newCotSubstringDequeuedForCentrals(cotSubstring);
                            }
                        } else if (deviceMode.equals(PluginTemplate.DEVICE_MODE.CENTRAL_MODE)) {
                            peripheralLogMessages.add("Got new cot from local ATAK");
                            Log.d(TAG, "dequeuing new cot: " + newCot);
                            for (int i = 0; i < newCot.length(); i += blePacketAppDataSize) {
                                try {
                                    Thread.sleep(750);
                                } catch (InterruptedException e) {
                                    // ignore this - if we are getting data that is not the startDelimiterString and receivedCot is empty,
                                    // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                                    // just ignore all this data
                                    centralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                                    Log.e(TAG, "interrupted", e);
                                }
                                int lastIndex = i + blePacketAppDataSize;
                                if (lastIndex > newCot.length()) {
                                    lastIndex = newCot.length();
                                }
                                String cotSubstring = newCot.substring(i, lastIndex);
                                Log.d(TAG, "Dequeueing new cot substring: " + cotSubstring);
                                callback.newCotDequeuedForPeripheral(cotSubstring);
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
            Log.w(TAG, "Error while running cot deqeueuer loop", e);
        }
    }

    public void setMtu(int mtu) {
        blePacketAppDataSize = mtu - TAKBLEManager.BLE_HEADER_SIZE;
    }
}

