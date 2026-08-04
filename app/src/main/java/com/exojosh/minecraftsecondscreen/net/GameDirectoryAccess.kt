package com.exojosh.minecraftsecondscreen.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract

private const val PREFS = "thorhud_prefs"
private const val KEY_GAME_DIR_URI = "game_dir_uri"

/**
 * Owns the one-time "let the user pick their .minecraft folder" flow and
 * persists the resulting permission so we don't have to ask again.
 *
 * Usage from an Activity:
 *   val launcher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
 *       uri?.let { GameDirectoryAccess.persist(context, it) }
 *   }
 *   // to trigger the picker: launcher.launch(null)
 */
object GameDirectoryAccess {

    /** Call after the user picks a folder via ActivityResultContracts.OpenDocumentTree(). */
    fun persist(context: Context, treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GAME_DIR_URI, treeUri.toString())
            .apply()
    }

    /** Returns the previously-granted folder, or null if the user hasn't picked one yet. */
    fun getSavedTreeUri(context: Context): Uri? {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_GAME_DIR_URI, null) ?: return null
        return Uri.parse(saved)
    }

    /** Sanity check that we still actually have the permission (user could have revoked it). */
    fun hasValidAccess(context: Context): Boolean {
        val uri = getSavedTreeUri(context) ?: return false
        return context.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission }
    }
}
