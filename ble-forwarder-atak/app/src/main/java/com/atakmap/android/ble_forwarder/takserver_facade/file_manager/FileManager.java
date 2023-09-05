package com.atakmap.android.ble_forwarder.takserver_facade.file_manager;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class FileManager {

    public static FileManager INSTANCE;

    Map<String, File> hashToFileData = new HashMap<>();
    Map<String, FilesInformation.FileInfo> hashToFileInfo = new HashMap<>();
    Gson gson = new Gson();

    public static FileManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FileManager();
        }
        return INSTANCE;
    }

    private FileManager() {

    }

    public void addFile(String hash, File file, FilesInformation.FileInfo fileInfo) {
        hashToFileData.put(hash, file);
        hashToFileInfo.put(hash, fileInfo);
    }

    public File getFileData(String hash) {
        return hashToFileData.get(hash);
    }

    public FilesInformation.FileInfo getFileInfo(String hash) {
        return hashToFileInfo.get(hash);
    }

    public String getJsonStringForCurrentFiles() {
        FilesInformation filesInformation = new FilesInformation(hashToFileInfo.size(), new ArrayList<>(hashToFileInfo.values()));
        for (int i = 0; i < filesInformation.getResults().size(); i++) {
            filesInformation.getResults().get(i).setPrimaryKey(Integer.toString(i + 1));
        }
        return gson.toJson(filesInformation);
    }

}
