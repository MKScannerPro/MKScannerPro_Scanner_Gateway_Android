package com.moko.support.scannergw.callback;

import com.moko.support.scannergw.entity.DeviceInfo;

public interface MokoScanDeviceCallback {
    void onStartScan();

    void onScanDevice(DeviceInfo device);

    void onStopScan();
}
