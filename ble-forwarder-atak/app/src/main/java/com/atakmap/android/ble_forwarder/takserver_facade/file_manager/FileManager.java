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
