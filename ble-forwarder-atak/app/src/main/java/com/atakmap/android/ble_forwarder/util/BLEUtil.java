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

import android.bluetooth.le.ScanResult;
import android.os.ParcelUuid;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BLEUtil {

    public static List<UUID> getServiceUUIDsList(ScanResult scanResult)
    {
        List<ParcelUuid> parcelUuids = scanResult.getScanRecord().getServiceUuids();

        List<UUID> serviceList = new ArrayList<>();

        if (parcelUuids != null) {
            for (int i = 0; i < parcelUuids.size(); i++) {
                UUID serviceUUID = parcelUuids.get(i).getUuid();

                if (!serviceList.contains(serviceUUID))
                    serviceList.add(serviceUUID);
            }
        }

        return serviceList;
    }

}
