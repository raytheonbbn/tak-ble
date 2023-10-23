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

package com.atakmap.android.ble_forwarder;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;

import java.util.Calendar;
import java.util.UUID;

/**
 * Implementation of the Bluetooth GATT Time Profile.
 * https://www.bluetooth.com/specifications/adopted-specifications
 */
public class TAKBLEDataTransferProfile {

    /* TAK BLE Data Transfer Service UUID */
    public static UUID TAK_BLE_DATA_TRANSFER_SERVICE = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    /* Peripheral to Central Data Transfer Characteristic */
    public static UUID PERIPHERAL_TO_CENTRAL_TRANSFER_CHARACTERISTIC = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb");
    /* Central to Peripheral Data transfer Characteristic */
    public static UUID CENTRAL_TO_PERIPHERAL_TRANSFER_CHARACTERISTIC = UUID.fromString("00002a0f-0000-1000-8000-00805f9b34fb");
    /* Mandatory Client Characteristic Config Descriptor */
    public static UUID CLIENT_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");


    public static BluetoothGattService createTAKBLEDataTransferService() {
        BluetoothGattService service = new BluetoothGattService(TAK_BLE_DATA_TRANSFER_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic peripheralToCentralDataTransfer = new BluetoothGattCharacteristic(PERIPHERAL_TO_CENTRAL_TRANSFER_CHARACTERISTIC,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ);
        BluetoothGattDescriptor configDescriptor = new BluetoothGattDescriptor(CLIENT_CONFIG,
                //Read/write descriptor
                BluetoothGattDescriptor.PERMISSION_READ | BluetoothGattDescriptor.PERMISSION_WRITE);
        peripheralToCentralDataTransfer.addDescriptor(configDescriptor);

        BluetoothGattCharacteristic centralToPeripheralDataTransfer = new BluetoothGattCharacteristic(CENTRAL_TO_PERIPHERAL_TRANSFER_CHARACTERISTIC,
                BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_WRITE);

        service.addCharacteristic(peripheralToCentralDataTransfer);
        service.addCharacteristic(centralToPeripheralDataTransfer);

        return service;

    }
}