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

package com.atakmap.android.ble_forwarder.util;

import android.util.Log;
import android.util.Pair;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class CotUtils {

    public static final String TAG = CotUtils.class.getSimpleName();

    public static final String START_DELIMITER_STRING = "<?xml version=\"1.0\"";
    public static final byte[] DELIMITER = { '<', '/', 'e', 'v', 'e', 'n', 't', '>'};
    public static final String DELIMITER_STRING = new String(DELIMITER, StandardCharsets.UTF_8);
    public static final String CONTENT_NODE_NAME = "c";
    public static final String CONTENT_BEGINNING_TAG = "<" + CONTENT_NODE_NAME + ">";
    public static final String CONTENT_ENDING_TAG = "</" + CONTENT_NODE_NAME + ">";
    public static final String DETAIL_BEGINNING_TAG = "<detail>";
    public static final String DETAIL_ENDING_TAG = "</detail>";
    public static final String DETAIL_AND_CONTENT_BEGINNING_TAGS = DETAIL_BEGINNING_TAG + CONTENT_BEGINNING_TAG;
    public static final String DETAIL_AND_CONTENT_ENDING_TAGS = CONTENT_ENDING_TAG + DETAIL_ENDING_TAG;
    public static final String SYNC_SEARCH_REQUEST_HOW = "sync_search_request";
    public static final String SYNC_SEARCH_RESPONSE_HOW = "sync_search_response";
    public static final String CONTENT_REQUEST_HOW = "content_request";
    public static final String CONTENT_RESPONSE_HOW = "content_response";
    public static final String SYNC_SEARCH_FAKE_COT_STRING =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<event version=\"2.0\" how=\"" + SYNC_SEARCH_REQUEST_HOW + "\">" +
                    "  " + DETAIL_AND_CONTENT_BEGINNING_TAGS +
                    "      sync_search\n" +
                    "    " + DETAIL_AND_CONTENT_ENDING_TAGS +
                    "</event>";
    public static final String SYNC_SEARCH_RESPONSE_START_DELIMITER_STRING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" how=\"" + SYNC_SEARCH_RESPONSE_HOW + "\">";
    public static final String CONTENT_REQUEST_START_DELIMITER_STRING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" how=\"" + CONTENT_REQUEST_HOW + "\">";
    public static final String CONTENT_RESPONSE_START_DELIMITER_STRING = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><event version=\"2.0\" how=\"" + CONTENT_RESPONSE_HOW + "\">";

    public static byte[] readCoTMessage(InputStream in) throws Exception {

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

            if (msgByte == DELIMITER[dlmIndex]) {
                ++dlmIndex;
                dlmBuf.write(msgByte);
            } else {
                dlmIndex = 0;
                msgBuf.write(dlmBuf.toByteArray());
                msgBuf.write(msgByte);
                dlmBuf.reset();
            }
            if (Arrays.equals(dlmBuf.toByteArray(), DELIMITER)) {
                foundDelimiter = true;
                msgBuf.write(dlmBuf.toByteArray()); // either dlmBuf or delimiter should be fine here.
                break;
            }
        }

        byte [] result = msgBuf.toByteArray();
        if (!foundDelimiter && result.length == 0) {
            return null;
        } else if (!foundDelimiter) {
            if (msgBuf.toByteArray().length == 1 && msgBuf.toByteArray()[0] == 10) {
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

    public static Pair<String,String> getHowAndContent(String cotEvent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            // Create a ByteArrayInputStream to read the XML string
            ByteArrayInputStream input = new ByteArrayInputStream(cotEvent.getBytes("UTF-8"));

            // Parse the XML string into a Document object
            Document document = builder.parse(input);

            // Get the <event> element
            NodeList eventList = document.getElementsByTagName("event");

            if (eventList.getLength() > 0) {
                Node eventNode = eventList.item(0);

                // Check if the <event> element has the "how" attribute
                if (eventNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eventElement = (Element) eventNode;
                    String howAttributeValue = eventElement.getAttribute("how");

                    // Get the text content of the <c> element
                    NodeList cList = eventElement.getElementsByTagName("c");

                    if (cList.getLength() > 0) {
                        Node cNode = cList.item(0);
                        String cContent = cNode.getTextContent();

                        // Return a pair of values
                        return new Pair<>(howAttributeValue, cContent);
                    } else {
                        Log.w(TAG, "No <c> element found within <event> element.");
                        return null;
                    }
                } else {
                    Log.w(TAG, "<event> element is not an ELEMENT_NODE.");
                    return null;
                }
            } else {
                Log.w(TAG, "No <event> element found in the XML.");
                return null;
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            Log.e(TAG, "Getting contents of cot event failed", e);
            return null;
        }
    }

}
