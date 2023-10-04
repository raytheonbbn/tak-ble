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

import com.atakmap.coremap.log.Log;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class NewCotDequeuer implements Runnable {

    public interface NewCotDequeuedCallback {
        void newCotSubstringDequeued(String newCotSubstring);
    }

    public static final int READ_SIZE = 511;

    public static final String TAG = NewCotDequeuer.class.getSimpleName();

    Queue<String> centralLogMessages;
    Queue<String> peripheralLogMessages;
    NewCotDequeuedCallback callback;
    Queue<String> newCotQueue = new ArrayBlockingQueue<>(1000);

    public NewCotDequeuer(NewCotDequeuedCallback callback,
                          Queue<String> centralLogMessages,
                          Queue<String> peripheralLogMessages) {
        this.callback = callback;
        this.centralLogMessages = centralLogMessages;
        this.peripheralLogMessages = peripheralLogMessages;
    }

    public void addNewCotToQueue(String newCot) {
        newCotQueue.add(newCot);
    }

    @Override
    public void run() {
        while (true) {
            String newCot = newCotQueue.poll();
            if (newCot != null) {
                centralLogMessages.add("Got new cot from local ATAK");
                Log.d(TAG, "dequeuing new cot: " + newCot);
                for (int i = 0; i < newCot.length(); i += READ_SIZE) {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        // ignore this - if we are getting data that is not the startDelimiterString and receivedCot is empty,
                        // then that means we are getting data in the middle of a CoT that we didn't get the start of -
                        // just ignore all this data
                        peripheralLogMessages.add("Ignoring this value, we didn't get start delimiter yet.");

                        Log.e(TAG, "interrupted", e);
                    }
                    int lastIndex = i + READ_SIZE;
                    if (lastIndex > newCot.length()) {
                        lastIndex = newCot.length();
                    }
                    String cotSubstring = newCot.substring(i, lastIndex);
                    Log.d(TAG, "Dequeueing new cot substring: " + cotSubstring);
                    callback.newCotSubstringDequeued(cotSubstring);

                }

            }
        }
    }
}

