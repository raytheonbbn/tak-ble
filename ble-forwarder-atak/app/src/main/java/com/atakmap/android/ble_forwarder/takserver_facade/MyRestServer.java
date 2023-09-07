package com.atakmap.android.ble_forwarder.takserver_facade;

import android.content.Context;
import android.util.Log;

import com.atakmap.android.ble_forwarder.MainDropDownReceiver;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FileManager;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FilesInformation;
import com.atakmap.android.ble_forwarder.util.FileNameAndBytes;
import com.atakmap.android.ble_forwarder.util.Utils;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import fi.iki.elonen.NanoHTTPD;

public class MyRestServer extends NanoHTTPD {

    private final static String TAG = MyRestServer.class.getSimpleName();

    Context context;
    BlockingQueue<String> pendingSyncSearchResults = new ArrayBlockingQueue<>(1000);
    BlockingQueue<FileNameAndBytes> pendingSyncContentResults = new ArrayBlockingQueue<>(1000);

    public MyRestServer(int port, Context context) {
        super(port);
        this.context = context;
    }

    public static String generateTimestamp() {
        // Get the current date and time
        Date currentDate = new Date();

        // Create a SimpleDateFormat object with the desired format
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'");

        // Set the time zone to UTC
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Format the current date and time using the SimpleDateFormat
        String formattedTimestamp = dateFormat.format(currentDate);

        return formattedTimestamp;
    }

    @Override
    public Response serve(IHTTPSession session) {
        // Handle incoming requests here and return appropriate responses
        Log.d(TAG, "Got request: " + session.getMethod() + " " + session.getUri());
        if (session.getUri().equals("/Marti/api/version/config")) {
            Log.d(TAG, "Responding to config request");
            return newFixedLengthResponse(Response.Status.OK, "application/json","{\"version\":\"3\",\"type\":\"ServerConfig\",\"data\":{\"version\":\"4.5.38-RELEASE\",\"api\":\"3\",\"hostname\":\"127.0.0.1\"},\"nodeId\":\"e6ec3550334a41aeb08b06e9578ea212\"}");
        } else if (session.getUri().equals("/Marti/api/clientEndPoints")) {
            Log.d(TAG, "Responding to client endpoints request");
            String currentTimestamp = generateTimestamp();
            return newFixedLengthResponse(Response.Status.OK, "application/json","{\"version\":\"3\",\"type\":\"com.bbn.marti.remote.ClientEndpoint\",\"data\":[{\"callsign\":\"GAINER\",\"uid\":\"ANDROID-357712080935181\",\"lastEventTime\":\"" + currentTimestamp + "\",\"lastStatus\":\"Connected\"},{\"callsign\":\"GRAND SLAM CTL\",\"uid\":\"ANDROID-5517dda15a8eca57\",\"lastEventTime\":\"" + currentTimestamp + "\",\"lastStatus\":\"Connected\"}],\"nodeId\":\"e6ec3550334a41aeb08b06e9578ea212\"}");
        } else if (session.getUri().equals("/Marti/sync/missionquery")) {
            Log.d(TAG, "Responding to missionquery request");
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html","<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n" +
                    "<title>404 TAK Server resource not found</title>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<h3>404 TAK Server resource not found</h3>\n" +
                    "</body>\n" +
                    "</html>");
        } else if (session.getUri().equals("/Marti/sync/missionupload")) {
            Map<String, List<String>> parameters = session.getParameters();
            List<String> hashList = parameters.get("hash");
            List<String> fileNameList = parameters.get("filename");
            List<String> creatorUidList = parameters.get("creatorUid");
            String fileHash = "";
            if (hashList != null && !hashList.isEmpty()) {
                fileHash = hashList.get(0);
            }
            String fileName = "";
            if (fileNameList != null && !fileNameList.isEmpty()) {
                fileName = fileNameList.get(0);
            }
            String creatorUid = "";
            if (creatorUidList != null && !creatorUidList.isEmpty()) {
                creatorUid = creatorUidList.get(0);
            }

            Log.d(TAG, "Got mission upload with hash: " + fileHash + ", fileName: " +
                    fileName + ", creatorUid: " + creatorUid);

            // getting body of POST
            // Get the input stream of the POST request
            InputStream inputStream = session.getInputStream();

            // Create a temporary file to store the received ZIP content
            String filePath = "/sdcard" + "/package_" + fileHash + ".zip";
            File tempFile = new File(filePath);
            Log.d(TAG, "Writing file from POST to path: " + filePath);
            FileOutputStream fileOutputStream = null;
            try {
                FilesInformation.FileInfo fileInfo = new FilesInformation.FileInfo(
                        Integer.toString(-1),
                        fileHash,
                        generateTimestamp(),
                        Arrays.asList("missionpackage"),
                        "application/x-zip-compressed",
                        Long.toString(tempFile.length()),
                        "anonymous",
                        "",
                        fileHash,
                        creatorUid,
                        fileName,
                        "public"
                );

                FileManager.getInstance().addFile(fileHash, tempFile, fileInfo);

                fileOutputStream = new FileOutputStream(tempFile);

                // Read the input stream and write it to the temporary file
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fileOutputStream.write(buffer, 0, bytesRead);
                }

                // Close the output stream and input stream
                fileOutputStream.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
                return newFixedLengthResponse(Response.Status.OK, "text/plain",
                        "http://127.0.0.1:8080/Marti/sync/content?hash=" + fileHash);
            }

            return newFixedLengthResponse(Response.Status.OK, "text/plain",
                    "http://127.0.0.1:8080/Marti/sync/content?hash=" + fileHash);
        } else if (session.getUri().equals("/Marti/sync/search")) {
            Log.d(TAG, "got a sync search request, fetching list of files from other device over BLE...");
            MainDropDownReceiver.sendSyncSearchRequest(new MainDropDownReceiver.SyncSearchCallback() {
                @Override
                public void result(String json) {
                    Log.d(TAG, "Adding json to pending sync search results.");
                    pendingSyncSearchResults.add(json);
                }
            });
            try {
                String syncSearchResult = pendingSyncSearchResults.take();
                Log.d(TAG, "Got sync search result: " + syncSearchResult);
                if (syncSearchResult == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "");
                } else {
                    return newFixedLengthResponse(Response.Status.OK, "text/json", syncSearchResult);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, "Error waiting for sync search result: " + e.getMessage(), e);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "");
        } else if (session.getUri().equals("/Marti/sync/content")) {
            Map<String, List<String>> parameters = session.getParameters();
            List<String> hashList = parameters.get("hash");
            String fileHash = "";
            if (hashList != null && !hashList.isEmpty()) {
                fileHash = hashList.get(0);
            }
            MainDropDownReceiver.sendSyncContentRequest(fileHash,
                    new MainDropDownReceiver.SyncContentCallback() {
                        @Override
                        public void result(FileNameAndBytes fileNameAndBytes) {
                            Log.d(TAG, "Adding sync content result to queue");
                            pendingSyncContentResults.add(fileNameAndBytes);
                        }
                    });
            try {
                FileNameAndBytes fileNameAndBytes = pendingSyncContentResults.take();
                Log.d(TAG, "Got sync content result, responding to request");
                if (fileNameAndBytes == null) {
                    return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "");
                } else {
                    String fileName = fileNameAndBytes.getFileName();
                    String fileBytesString = fileNameAndBytes.getFileBytesString();
                    fileBytesString = fileBytesString.substring(fileBytesString.indexOf("504B0304"));
                    fileBytesString = fileBytesString.substring(0, fileBytesString.length() - "0D0A2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D2D363337396464633735616261323031632D2D0D0A".length());

                    Log.d(TAG, "File bytes string: " + fileBytesString);

                    byte[] fileBytes = Utils.hexStringToByteArray(fileBytesString);

                    try {
                        // Create a FileWriter object with the given file path
                        FileWriter fileWriter = new FileWriter(new File("/sdcard/" + "package_" + fileHash + ".txt"));

                        // Create a BufferedWriter to write efficiently
                        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

                        // Write the string to the file
                        bufferedWriter.write(Utils.byteArrayToHexString(fileBytes));

                        // Close the BufferedWriter to flush and release resources
                        bufferedWriter.close();

                        // Optionally, you can also close the FileWriter
                        fileWriter.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    Log.d(TAG, "Sending sync content bytes with name " + fileName + " and length " + fileBytes.length);
                    Response response = newFixedLengthResponse(Response.Status.OK, "application/x-zip-compressed", new ByteArrayInputStream(fileBytes), fileBytes.length);
                    String contentDisposition = "inline; filename=" + fileName + "\r\n";
                    Log.d(TAG, "Content disposition: " + contentDisposition);
                    response.addHeader("Content-Disposition", contentDisposition);
                    response.addHeader("Content-Length", Long.toString(fileBytes.length));
                    return response;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, "Error waiting for sync search result: " + e.getMessage(), e);
            }
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "");
        }

        return newFixedLengthResponse("Hello from your REST endpoint!");
    }
}