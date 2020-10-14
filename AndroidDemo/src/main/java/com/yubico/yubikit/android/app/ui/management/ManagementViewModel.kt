package com.yubico.yubikit.android.app.ui.management

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.yubico.yubikit.android.app.ui.YubiKeyViewModel
import com.yubico.yubikit.core.Logger
import com.yubico.yubikit.core.YubiKeyDevice
import com.yubico.yubikit.core.otp.OtpConnection
import com.yubico.yubikit.core.smartcard.SmartCardConnection
import com.yubico.yubikit.management.DeviceInfo
import com.yubico.yubikit.management.ManagementSession
import java.io.IOException

class NonClosingManagementSession(connection: OtpConnection) : ManagementSession(connection) {
    override fun close() {
        Logger.d("Keeping session open")
    }

    fun doClose() {
        Logger.d("Closing session")
        super.close()
    }
}

class ManagementViewModel : YubiKeyViewModel<ManagementSession>() {
    private val _deviceInfo = MutableLiveData<DeviceInfo?>()
    val deviceInfo: LiveData<DeviceInfo?> = _deviceInfo
    private var sessionRef: NonClosingManagementSession? = null

    override fun getSession(device: YubiKeyDevice): ManagementSession = when {
        device.supportsConnection(SmartCardConnection::class.java) -> {
            val connection = device.openConnection(SmartCardConnection::class.java)
            try {
                ManagementSession(connection)
            } catch (e: Exception) {
                connection.close()
                throw e
            }
        }
        // Keep the application open over OTP, as closing it causes the device to re-enumerate
        device.supportsConnection(OtpConnection::class.java) -> {
            val connection = device.openConnection(OtpConnection::class.java)
            try {
                NonClosingManagementSession(connection).apply { sessionRef = this }
            } catch (e: Exception) {
                connection.close()
                throw e
            }
        }
        else -> throw IOException("No interface available for Management")
    }

    override fun ManagementSession.updateState() {
        _deviceInfo.postValue(deviceInfo)
    }

    fun releaseYubiKey() {
        sessionRef?.doClose()
        sessionRef = null
    }
}