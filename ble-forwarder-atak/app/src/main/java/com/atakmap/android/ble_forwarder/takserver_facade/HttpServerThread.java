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

import com.atakmap.android.ble_forwarder.util.DateUtil;
import com.atakmap.coremap.log.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

public class HttpServerThread implements Runnable {

    private final String TAG = HttpServerThread.class.getSimpleName();

    int port;

    ServerSocket serverSocket = null;

    public static Queue<String> outgoingHttpQueue = new ArrayBlockingQueue<>(1000);
    public Queue<String> peripheralLogMessages;

    public HttpServerThread(int port,
                            Queue<String> peripheralLogMessages) {
        this.port = port;
        this.peripheralLogMessages = peripheralLogMessages;
    }

    public void run() {

        Log.d(TAG, "Running http server thread.");

        Socket socket;
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

        } catch (IOException | InterruptedException e) {
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
