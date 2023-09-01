package com.atakmap.android.ble_forwarder.takserver_facade;

import android.content.Context;
import android.util.Log;

import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FileManager;
import com.atakmap.android.ble_forwarder.takserver_facade.file_manager.FilesInformation;
import com.atakmap.android.maps.MapView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import fi.iki.elonen.NanoHTTPD;

public class MyRestServer extends NanoHTTPD {

    private final static String TAG = MyRestServer.class.getSimpleName();

    Context context;

    public interface MyRestServerCallbacks {
        void gotSyncSearchRequest();
    }

    private MyRestServerCallbacks callback;

    public MyRestServer(int port, MyRestServerCallbacks callback, Context context) {
        super(port);
        this.callback = callback;
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
            String fileHash = "";
            if (hashList != null && !hashList.isEmpty()) {
                fileHash = hashList.get(0);
            }

            Log.d(TAG, "Got mission upload with hash: " + fileHash);

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
                        "1",
                        fileHash,
                        MapView.getDeviceUid(),
                        MapView.getMapView().getDeviceCallsign(),
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
            }

            return newFixedLengthResponse(Response.Status.OK, "text/plain",
                    "http://127.0.0.1:8080/Marti/sync/content?hash=" + fileHash);
        } else if (session.getUri().equals("/Marti/sync/search")) {
            Log.d(TAG, "got a sync search request, fetching list of files from other device over BLE...");
            callback.gotSyncSearchRequest();
        }
        return newFixedLengthResponse("Hello from your REST endpoint!");
    }
}