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

import com.atakmap.android.ble_forwarder.util.CotUtils;
import com.atakmap.android.ble_forwarder.util.DateUtil;
import com.atakmap.coremap.log.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

public class CoTServerThread implements Runnable {

    public interface NewCotCallback {
        void onNewCotReceived(String newCot);
    }

    private final String TAG = CoTServerThread.class.getSimpleName();

    int port;

    ServerSocket serverSocket;

    private final static String TAK_RESPONSE_TYPE = "t-x-takp-r";
    private final static String TAK_PING_TYPE = "t-x-c-t";
    private final static int TIMEOUT_MILLIS = 60000;

    public ArrayBlockingQueue<String> outgoingCotQueue = new ArrayBlockingQueue<>(1000);
    public Queue<String> centralLogMessages;

    NewCotCallback newCotCallback;

    public CoTServerThread(int port,
                           NewCotCallback newCotCallback,
                           Queue<String> centralLogMessages) {
        this.port = port;
        this.newCotCallback = newCotCallback;
        this.centralLogMessages = centralLogMessages;
    }

    public void addNewOutgoingCot(String outgoingCot) {
        outgoingCotQueue.add(outgoingCot);
    }

    public void run() {

        Log.d(TAG, "Running server thread.");

        Socket socket;
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
                    byte[] msg = CotUtils.readCoTMessage(in);
                    if (msg != null) {
                        String newCot = new String(msg, StandardCharsets.UTF_8);

                        DocumentBuilderFactory factory =
                                DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        ByteArrayInputStream input =  new ByteArrayInputStream(
                                newCot.getBytes(StandardCharsets.UTF_8));
                        Document doc = builder.parse(input);
                        XPath xPath =  XPathFactory.newInstance().newXPath();
                        String expression = "/event";
                        NodeList nodeList = (NodeList) xPath.compile(expression).evaluate(
                                doc, XPathConstants.NODESET);
                        String type = "";
                        String uid;
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
                            newCotCallback.onNewCotReceived(newCot);
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
                        String outgoingCot = outgoingCotQueue.take();
                        if (outgoingCot != null) {
                            os.write(outgoingCot.getBytes(StandardCharsets.UTF_8));
                            centralLogMessages.add("Wrote cot received over BLE to local ATAK.");
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