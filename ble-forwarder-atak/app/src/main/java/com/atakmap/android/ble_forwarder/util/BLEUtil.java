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
