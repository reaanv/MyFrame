package com.myframe.tv.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import com.myframe.tv.core.AppJson
import com.myframe.tv.core.MediaKind
import com.myframe.tv.core.PlaybackConfig
import kotlinx.coroutines.flow.Flow

/** One artwork or photograph in the library. */
@Entity(tableName = "media", indices = [Index(value = ["sha256"], unique = true), Index("kind")])
data class Media(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: MediaKind,
    val title: String,
    val artist: String = "",
    val dateText: String = "",
    val credit: String = "",
    val sourceUrl: String = "",
    /** "asset:art/name.jpg" for bundled artwork, otherwise an absolute path inside app storage. */
    val uri: String,
    val width: Int,
    val height: Int,
    val takenAt: Long = 0,
    val addedAt: Long = 0,
    val favorite: Boolean = false,
    val hidden: Boolean = false,
    /** Content hash; unique, so the same picture is never stored twice. */
    val sha256: String,
    val sizeBytes: Long = 0,
    val origin: String = "",
    val fileName: String = "",
    /** Per-item display time in seconds; 0 uses the preset default. */
    val durationSec: Int = 0,
    val alignX: Float = 0.5f,
    val alignY: Float = 0.5f,
    val zoom: Float = 1f,
) {
    val isAsset: Boolean get() = uri.startsWith("asset:")
    val isPortrait: Boolean get() = height > width
}

/** A named, ordered set of pictures. Called an album for photos and a collection elsewhere. */
@Entity(tableName = "albums")
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val systemKey: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
)

@Entity(
    tableName = "album_items",
    primaryKeys = ["albumId", "mediaId"],
    foreignKeys = [
        ForeignKey(entity = Album::class, parentColumns = ["id"], childColumns = ["albumId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Media::class, parentColumns = ["id"], childColumns = ["mediaId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("mediaId")],
)
data class AlbumItem(val albumId: Long, val mediaId: Long, val position: Int)

data class AlbumInfo(@Embedded val album: Album, val itemCount: Int, val coverId: Long?)

/** A saved slideshow: a name plus its [PlaybackConfig] as JSON. */
@Entity(tableName = "presets")
data class Preset(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** "art" or "photos" for the two built-in modes, empty for user presets. */
    val builtin: String = "",
    val configJson: String,
    val sortOrder: Int = 0,
    val lastMediaId: Long = 0,
) {
    val config: PlaybackConfig
        get() = runCatching { AppJson.decodeFromString(PlaybackConfig.serializer(), configJson) }.getOrDefault(PlaybackConfig())

    fun with(config: PlaybackConfig): Preset = copy(configJson = AppJson.encodeToString(PlaybackConfig.serializer(), config))
}

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE kind = 'ART' AND hidden = 0 ORDER BY id")
    fun observeArt(): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE kind = 'ART' ORDER BY id")
    fun observeArtAll(): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE kind = 'PHOTO' ORDER BY addedAt DESC, id DESC")
    fun observePhotos(): Flow<List<Media>>

    @Query(
        "SELECT * FROM media WHERE kind = :kind AND hidden = 0 AND (:favOnly = 0 OR favorite = 1) " +
            "ORDER BY CASE WHEN kind = 'PHOTO' THEN -addedAt ELSE 0 END, CASE WHEN kind = 'PHOTO' THEN -id ELSE id END"
    )
    suspend fun list(kind: MediaKind, favOnly: Boolean): List<Media>

    /** Every stored work of this kind, hidden ones included (used for storage limits and duplicate checks). */
    @Query("SELECT * FROM media WHERE kind = :kind")
    suspend fun everyOfKind(kind: MediaKind): List<Media>

    @Query("SELECT * FROM media WHERE origin = 'bundled'")
    suspend fun bundled(): List<Media>

    @Query("SELECT * FROM media WHERE id = :id")
    suspend fun byId(id: Long): Media?

    @Query("SELECT * FROM media WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<Media>

    @Query("SELECT * FROM media WHERE sha256 = :sha")
    suspend fun bySha(sha: String): Media?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: Media): Long

    @Update
    suspend fun update(m: Media)

    @Query("DELETE FROM media WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE media SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

    @Query("UPDATE media SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Query("SELECT COUNT(*) FROM media WHERE kind = :kind")
    suspend fun count(kind: MediaKind): Int

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM media WHERE kind = 'PHOTO'")
    suspend fun photoBytes(): Long
}

private const val ALBUM_INFO =
    "SELECT a.*, (SELECT COUNT(*) FROM album_items i WHERE i.albumId = a.id) AS itemCount, " +
        "(SELECT i.mediaId FROM album_items i WHERE i.albumId = a.id ORDER BY i.position, i.mediaId LIMIT 1) AS coverId " +
        "FROM albums a ORDER BY a.sortOrder, a.id"

private const val ALBUM_ITEMS =
    "SELECT m.* FROM media m JOIN album_items i ON i.mediaId = m.id WHERE i.albumId = :albumId ORDER BY i.position, i.mediaId"

@Dao
abstract class AlbumDao {
    @Query(ALBUM_INFO)
    abstract fun observeAll(): Flow<List<AlbumInfo>>

    @Query(ALBUM_INFO)
    abstract suspend fun listAll(): List<AlbumInfo>

    @Query("SELECT * FROM albums WHERE id = :id")
    abstract suspend fun byId(id: Long): Album?

    @Query("SELECT * FROM albums WHERE systemKey = :key LIMIT 1")
    abstract suspend fun bySystem(key: String): Album?

    @Insert
    abstract suspend fun insert(a: Album): Long

    @Query("UPDATE albums SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM albums WHERE id = :id AND systemKey = ''")
    abstract suspend fun delete(id: Long)

    @Query(ALBUM_ITEMS)
    abstract fun observeItems(albumId: Long): Flow<List<Media>>

    @Query(ALBUM_ITEMS)
    abstract suspend fun items(albumId: Long): List<Media>

    @Query("SELECT mediaId FROM album_items WHERE albumId = :albumId ORDER BY position, mediaId")
    abstract suspend fun itemIds(albumId: Long): List<Long>

    @Query("SELECT mediaId FROM album_items WHERE albumId = :albumId")
    abstract fun observeItemIds(albumId: Long): Flow<List<Long>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM album_items WHERE albumId = :albumId")
    abstract suspend fun maxPosition(albumId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertItem(item: AlbumItem): Long

    @Query("DELETE FROM album_items WHERE albumId = :albumId AND mediaId IN (:mediaIds)")
    abstract suspend fun removeItems(albumId: Long, mediaIds: List<Long>)

    @Query("DELETE FROM album_items WHERE albumId = :albumId")
    abstract suspend fun clear(albumId: Long)

    @Query("UPDATE album_items SET position = :position WHERE albumId = :albumId AND mediaId = :mediaId")
    abstract suspend fun setPosition(albumId: Long, mediaId: Long, position: Int)

    /** Appends in the given order; items already present keep their place. */
    @Transaction
    open suspend fun addItems(albumId: Long, mediaIds: List<Long>) {
        var p = maxPosition(albumId)
        for (id in mediaIds) {
            if (insertItem(AlbumItem(albumId, id, p + 1)) != -1L) p++
        }
    }

    /** Moves one item [delta] places and renumbers the album so positions stay dense. */
    @Transaction
    open suspend fun move(albumId: Long, mediaId: Long, delta: Int) {
        val ids = itemIds(albumId).toMutableList()
        val from = ids.indexOf(mediaId)
        val to = from + delta
        if (from < 0 || to !in ids.indices) return
        ids.removeAt(from)
        ids.add(to, mediaId)
        ids.forEachIndexed { index, id -> setPosition(albumId, id, index) }
    }
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Preset>>

    @Query("SELECT * FROM presets ORDER BY sortOrder, id")
    suspend fun listAll(): List<Preset>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun byId(id: Long): Preset?

    @Query("SELECT * FROM presets WHERE builtin = :key LIMIT 1")
    suspend fun byBuiltin(key: String): Preset?

    @Insert
    suspend fun insert(p: Preset): Long

    @Update
    suspend fun update(p: Preset)

    @Query("DELETE FROM presets WHERE id = :id AND builtin = ''")
    suspend fun delete(id: Long)

    @Query("UPDATE presets SET lastMediaId = :mediaId WHERE id = :id")
    suspend fun setLast(id: Long, mediaId: Long)
}

@Database(entities = [Media::class, Album::class, AlbumItem::class, Preset::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun media(): MediaDao
    abstract fun albums(): AlbumDao
    abstract fun presets(): PresetDao

    companion object {
        fun build(context: Context, inMemory: Boolean = false): AppDatabase =
            if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, "myframe.db").build()
            }
    }
}
