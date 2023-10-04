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

    public static String byteArrayToHexString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : bytes) {
            stringBuilder.append(String.format("%02X", b));
        }
        return stringBuilder.toString();
    }

    public static byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] byteArray = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            byteArray[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return byteArray;
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
