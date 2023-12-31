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

import java.util.List;

public class FilesInformation {
    private int resultCount;
    private List<FileInfo> results;

    public FilesInformation(int resultCount, List<FileInfo> results) {
        this.resultCount = resultCount;
        this.results = results;
    }

    public int getResultCount() {
        return resultCount;
    }

    public List<FileInfo> getResults() {
        return results;
    }

    public static class FileInfo {
        private String EXPIRATION;
        private String UID;
        private String SubmissionDateTime;
        private List<String> Keywords;
        private String MIMEType;
        private String Size;
        private String SubmissionUser;
        private String PrimaryKey;
        private String Hash;
        private String CreatorUid;
        private String Name;
        private String Tool;

        public FileInfo(
                String EXPIRATION,
                String UID,
                String SubmissionDateTime,
                List<String> Keywords,
                String MIMEType,
                String Size,
                String SubmissionUser,
                String PrimaryKey,
                String Hash,
                String CreatorUid,
                String Name,
                String Tool
        ) {
            this.EXPIRATION = EXPIRATION;
            this.UID = UID;
            this.SubmissionDateTime = SubmissionDateTime;
            this.Keywords = Keywords;
            this.MIMEType = MIMEType;
            this.Size = Size;
            this.SubmissionUser = SubmissionUser;
            this.PrimaryKey = PrimaryKey;
            this.Hash = Hash;
            this.CreatorUid = CreatorUid;
            this.Name = Name;
            this.Tool = Tool;
        }

        public String getEXPIRATION() {
            return EXPIRATION;
        }

        public void setEXPIRATION(String EXPIRATION) {
            this.EXPIRATION = EXPIRATION;
        }

        public String getUID() {
            return UID;
        }

        public void setUID(String UID) {
            this.UID = UID;
        }

        public String getSubmissionDateTime() {
            return SubmissionDateTime;
        }

        public void setSubmissionDateTime(String submissionDateTime) {
            SubmissionDateTime = submissionDateTime;
        }

        public List<String> getKeywords() {
            return Keywords;
        }

        public void setKeywords(List<String> keywords) {
            Keywords = keywords;
        }

        public String getMIMEType() {
            return MIMEType;
        }

        public void setMIMEType(String MIMEType) {
            this.MIMEType = MIMEType;
        }

        public String getSize() {
            return Size;
        }

        public void setSize(String size) {
            Size = size;
        }

        public String getSubmissionUser() {
            return SubmissionUser;
        }

        public void setSubmissionUser(String submissionUser) {
            SubmissionUser = submissionUser;
        }

        public String getPrimaryKey() {
            return PrimaryKey;
        }

        public void setPrimaryKey(String primaryKey) {
            PrimaryKey = primaryKey;
        }

        public String getHash() {
            return Hash;
        }

        public void setHash(String hash) {
            Hash = hash;
        }

        public String getCreatorUid() {
            return CreatorUid;
        }

        public void setCreatorUid(String creatorUid) {
            CreatorUid = creatorUid;
        }

        public String getName() {
            return Name;
        }

        public void setName(String name) {
            Name = name;
        }

        public String getTool() {
            return Tool;
        }

        public void setTool(String tool) {
            Tool = tool;
        }
    }
}
