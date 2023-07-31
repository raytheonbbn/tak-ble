package com.atakmap.android.ble_forwarder.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CotUtils {

    public static final String START_DELIMITER_STRING = "<?xml version=\"1.0\"";
    public static final byte[] DELIMITER = { '<', '/', 'e', 'v', 'e', 'n', 't', '>'};
    public static final String DELIMITER_STRING = new String(DELIMITER, StandardCharsets.UTF_8);

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

}
