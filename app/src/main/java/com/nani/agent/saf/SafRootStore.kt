package com.nani.agent.saf

import android.content.Context
import android.net.Uri

object SafRootStore {
    private const val PREFS_NAME = "nani_saf_root"
    private const val KEY_ROOT_URI = "rootUri"

    fun saveRootUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_URI, uri.toString())
            .apply()
    }

    fun getRootUri(context: Context): Uri? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROOT_URI, null)
        return value?.let(Uri::parse)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ROOT_URI)
            .apply()
    }
}
