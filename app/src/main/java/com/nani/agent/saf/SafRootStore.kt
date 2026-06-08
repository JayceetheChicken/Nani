package com.nani.agent.saf

import android.content.Context
import android.net.Uri

object SafRootStore {
    private const val PREFS_NAME = "nani_saf_root"
    private const val KEY_ROOT_URI = "rootUri"
    private const val KEY_ROOT_URIS = "rootUris"

    fun saveRootUri(context: Context, uri: Uri) {
        val existing = getRootUris(context).toMutableList()
        if (existing.none { it == uri }) {
            existing += uri
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROOT_URI, uri.toString())
            .putStringSet(KEY_ROOT_URIS, existing.map { it.toString() }.toSet())
            .apply()
    }

    fun getRootUri(context: Context): Uri? {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ROOT_URI, null)
        return value?.let(Uri::parse)
    }

    fun getRootUris(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val values = prefs.getStringSet(KEY_ROOT_URIS, null)
        if (values != null) return values.map(Uri::parse)
        return prefs.getString(KEY_ROOT_URI, null)?.let { listOf(Uri.parse(it)) }.orEmpty()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_ROOT_URI)
            .remove(KEY_ROOT_URIS)
            .apply()
    }
}
