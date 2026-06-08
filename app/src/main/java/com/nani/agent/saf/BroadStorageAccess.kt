package com.nani.agent.saf

import android.os.Build
import android.os.Environment

object BroadStorageAccess {
    fun isGranted(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    fun statusText(): String {
        return if (isGranted()) {
            "Broad Agent Storage granted"
        } else {
            "Broad file access not granted. Enable manually if you want full Agent-user shared storage access."
        }
    }
}
