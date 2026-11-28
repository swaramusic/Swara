package com.swara.nativeapp.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.swara.nativeapp.model.Track
import com.swara.nativeapp.model.UserPlaylist
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight SQLite storage for track collections and user playlists,
 * automatically migrating existing SharedPreferences JSON data on first access.
 */
class SwaraDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS track_collections (
                collection_key TEXT PRIMARY KEY,
                payload_json TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_playlists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                artwork_uri TEXT NOT NULL,
                tracks_json TEXT NOT NULL,
                sort_order INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onCreate(db)
    }

    fun saveTrackList(key: String, list: List<Track>) {
        val json = encodeTracks(list).toString()
        val cv = ContentValues().apply {
            put("collection_key", key)
            put("payload_json", json)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("track_collections", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadTrackList(key: String, legacyPrefs: SharedPreferences): List<Track> {
        readableDatabase.rawQuery(
            "SELECT payload_json FROM track_collections WHERE collection_key = ?",
            arrayOf(key),
        ).use { c ->
            if (c.moveToFirst()) {
                return decodeTracks(c.getString(0))
            }
        }
        val legacyRaw = legacyPrefs.getString(key, null) ?: return emptyList()
        val parsed = decodeTracks(legacyRaw)
        if (parsed.isNotEmpty()) saveTrackList(key, parsed)
        return parsed
    }

    fun savePlaylists(list: List<UserPlaylist>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("user_playlists", null, null)
            list.forEachIndexed { idx, pl ->
                val cv = ContentValues().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    put("artwork_uri", pl.artworkUri)
                    put("tracks_json", encodeTracks(pl.tracks).toString())
                    put("sort_order", idx)
                }
                db.insertWithOnConflict("user_playlists", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun loadPlaylists(legacyPrefs: SharedPreferences): List<UserPlaylist> {
        val out = mutableListOf<UserPlaylist>()
        readableDatabase.rawQuery(
            "SELECT id, name, artwork_uri, tracks_json FROM user_playlists ORDER BY sort_order ASC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1)
                val art = c.getString(2)
                val tracks = decodeTracks(c.getString(3))
                if (id.isNotBlank() && name.isNotBlank()) {
                    out.add(UserPlaylist(id, name, tracks, art.ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }))
                }
            }
        }
        if (out.isNotEmpty()) return out

        val legacyRaw = legacyPrefs.getString("playlists", null) ?: return emptyList()
        val migrated = decodeLegacyPlaylists(legacyRaw)
        if (migrated.isNotEmpty()) savePlaylists(migrated)
        return migrated
    }

    companion object {
        private const val DB_NAME = "swara_library.db"
        private const val DB_VERSION = 1

        internal fun encodeTracks(list: List<Track>): JSONArray {
            val arr = JSONArray()
            list.forEach { t ->
                arr.put(
                    JSONObject()
                        .put("id", t.id)
                        .put("t", t.title)
                        .put("a", t.artist)
                        .put("img", t.artworkUrl),
                )
            }
            return arr
        }

        internal fun decodeTracks(raw: String): List<Track> = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "")
                    if (id.isBlank()) null
                    else Track(id, o.optString("t", ""), o.optString("a", ""), o.optString("img", ""))
                }.getOrNull()
            }
        }.getOrDefault(emptyList())

        internal fun decodeLegacyPlaylists(raw: String): List<UserPlaylist> = runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("id", "")
                    val name = o.optString("name", "")
                    if (id.isBlank() || name.isBlank()) null
                    else {
                        val tracks = decodeTracks(o.optJSONArray("tracks")?.toString() ?: "[]")
                        val art = o.optString("img", "").ifBlank { tracks.firstOrNull()?.artworkUrl.orEmpty() }
                        UserPlaylist(id, name, tracks, art)
                    }
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }
}
