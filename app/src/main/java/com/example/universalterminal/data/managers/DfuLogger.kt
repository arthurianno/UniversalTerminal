package com.example.universalterminal.data.managers

import android.util.Log
import no.nordicsemi.android.dfu.DfuProgressListener

abstract class DfuLogger : DfuProgressListener {
    override fun onDeviceConnecting(deviceAddress: String) {
        Log.i("DfuProgressListener","DeviceConnecting")
    }

    override fun onDeviceConnected(deviceAddress: String) {
        Log.i("DfuProgressListener","DeviceConnect")
    }

    override fun onDfuProcessStarting(deviceAddress: String) {
        Log.i("DfuProgressListener","ProcessStarting")
    }

    override fun onDfuProcessStarted(deviceAddress: String) {
        Log.i("DfuProgressListener","ProcessStarted")
    }

    override fun onEnablingDfuMode(deviceAddress: String) {
        Log.i("DfuProgressListener","EnableDFUMode")
    }

    override fun onProgressChanged(
        deviceAddress: String,
        percent: Int,
        speed: Float,
        avgSpeed: Float,
        currentPart: Int,
        partsTotal: Int
    ) {
        Log.i("DfuProgressListener","Progress change $deviceAddress percent $percent")
    }

    override fun onFirmwareValidating(deviceAddress: String) {
        Log.i("DfuProgressListener","FirmwareValidating")
    }

    override fun onDeviceDisconnecting(deviceAddress: String?) {
        Log.i("DfuProgressListener","DeviceDisconnecting")
    }

    override fun onDeviceDisconnected(deviceAddress: String) {
        Log.i("DfuProgressListener","DeviceDisconnected")
    }

    override fun onDfuCompleted(deviceAddress: String) {
        Log.i("DfuProgressListener","DfuCompleted")
    }

    override fun onDfuAborted(deviceAddress: String) {
        Log.i("DfuProgressListener","DfuAborted")
    }

    override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
        Log.i("DfuProgressListener","Error")
    }
}