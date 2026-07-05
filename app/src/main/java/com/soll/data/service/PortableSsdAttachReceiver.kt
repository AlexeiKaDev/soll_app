package com.soll.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.soll.data.repository.PortableSsdAttachWorkScheduler
import timber.log.Timber

class PortableSsdAttachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        Timber.d("Portable SSD attach probe requested by action=%s", action)
        PortableSsdAttachWorkScheduler.enqueue(context.applicationContext)
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            UsbManager.ACTION_USB_DEVICE_ATTACHED,
            Intent.ACTION_MEDIA_MOUNTED,
            Intent.ACTION_MEDIA_CHECKING,
        )
    }
}
