package com.atakmap.android.ble_forwarder.takserver_facade;

import com.atakmap.coremap.log.Log;

import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class NewCotDequeuer implements Runnable {

    public interface NewCotDequeuedCallback {
        void newCotSubstringDequeued(String newCotSubstring);
    }

    public static final int READ_SIZE = 20;

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
                        Thread.sleep(100);
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
                    callback.newCotSubstringDequeued(newCot.substring(i, lastIndex));

                }

            }
        }
    }
}

