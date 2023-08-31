package com.atakmap.android.ble_forwarder.takserver_facade;

import java.util.HashMap;
import java.util.Map;

public class FileManager {

    public static FileManager INSTANCE;

    Map<String, String> hashToFiles = new HashMap<>();

    public static FileManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FileManager();
        }
        return INSTANCE;
    }

    private FileManager() {

    }

    

}
