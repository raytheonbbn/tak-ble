package com.atakmap.android.ble_forwarder.util;

import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Utils {

    public static String encodeToBase64(byte[] data) {
        // Encode the byte array to a Base64 string
        return Base64.encodeToString(data, Base64.DEFAULT);
    }

    public static byte[] decodeFromBase64(String base64String) {
        // Decode the Base64 string to a byte array
        return Base64.decode(base64String, Base64.DEFAULT);
    }


    public static byte[] convertFileToByteArray(File file) throws IOException {
        FileInputStream fileInputStream = null;
        byte[] byteArray = null;

        try {
            // Initialize the FileInputStream with the file
            fileInputStream = new FileInputStream(file);

            // Determine the length of the file (in bytes)
            long fileLength = file.length();

            // Create a byte array with the same length as the file
            byteArray = new byte[(int) fileLength];

            // Read the file content into the byte array
            int bytesRead = fileInputStream.read(byteArray);

            // Ensure that the entire file is read
            if (bytesRead != fileLength) {
                throw new IOException("Failed to read the entire file");
            }
        } catch (IOException e) {
            // Handle exceptions, e.g., file not found or read errors
            e.printStackTrace();
        } finally {
            // Close the FileInputStream
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return byteArray;
    }

}
