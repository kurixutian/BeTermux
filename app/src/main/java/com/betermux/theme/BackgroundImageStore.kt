package com.betermux.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages optional terminal/app background wallpaper storage and configurable scrim opacity.
 * All bitmap decoding and file copying runs off the main thread and caches the sampled
 * bitmap against a monotonic revision counter.
 */
object BackgroundImageStore {

    private const val PREFS_NAME = "betermux_background"
    private const val KEY_HAS_IMAGE = "has_image"
    private const val KEY_SCRIM_ALPHA = "scrim_alpha"
    private const val KEY_REVISION = "revision"

    const val MIN_SCRIM_ALPHA = 0.25f
    const val MAX_SCRIM_ALPHA = 0.96f
    const val DEFAULT_SCRIM_ALPHA = 0.84f
    private const val MAX_DECODE_DIM_PX = 2048

    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val cacheLock = Any()
    private var cachedRevision: Long = -1L
    private var cachedBitmap: Bitmap? = null

    /** Observable state revision for Compose surfaces (`WarpBackground`). */
    var version by mutableIntStateOf(0)
        private set

    var scrimAlphaState by mutableFloatStateOf(DEFAULT_SCRIM_ALPHA)
        private set

    private var scrimInitialized = false

    @JvmStatic
    fun clampScrimAlpha(alpha: Float): Float =
        alpha.coerceIn(MIN_SCRIM_ALPHA, MAX_SCRIM_ALPHA)

    @JvmStatic
    fun getScrimAlpha(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = clampScrimAlpha(prefs.getFloat(KEY_SCRIM_ALPHA, DEFAULT_SCRIM_ALPHA))
        scrimAlphaState = value
        scrimInitialized = true
        return value
    }

    @JvmStatic
    fun setScrimAlpha(context: Context, alpha: Float) {
        val clamped = clampScrimAlpha(alpha)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_SCRIM_ALPHA, clamped)
            .apply()
        scrimAlphaState = clamped
        scrimInitialized = true
        version++
    }

    @JvmStatic
    fun ensureInitialized(context: Context) {
        if (!scrimInitialized) {
            getScrimAlpha(context)
        }
    }

    @JvmStatic
    fun hasImage(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_HAS_IMAGE, false) && wallpaperFile(context).isFile
    }

    @JvmStatic
    fun clearImage(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextRev = prefs.getLong(KEY_REVISION, 0L) + 1L
        prefs.edit()
            .putBoolean(KEY_HAS_IMAGE, false)
            .putLong(KEY_REVISION, nextRev)
            .apply()
        val file = wallpaperFile(context)
        if (file.exists()) {
            file.delete()
        }
        synchronized(cacheLock) {
            cachedBitmap = null
            cachedRevision = nextRev
        }
        version++
    }

    suspend fun saveFromUri(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        saveFromUriBlocking(context, uri)
    }

    /** Save an already-decoded bitmap directly (no ContentResolver/URI layer). Recycles the bitmap on failure. */
    suspend fun saveFromBitmap(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        if (persistBitmap(context.applicationContext, bitmap)) {
            true
        } else {
            bitmap.recycle()
            false
        }
    }

    suspend fun loadBitmap(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        loadBitmapBlocking(context)
    }

    /** Java-friendly async loader for [com.termux.app.TermuxActivity]. */
    @JvmStatic
    fun loadBitmapAsync(context: Context, callback: java.util.function.Consumer<Bitmap?>) {
        val appContext = context.applicationContext
        val mainHandler = Handler(Looper.getMainLooper())
        if (!hasImage(appContext)) {
            mainHandler.post { callback.accept(null) }
            return
        }
        ioExecutor.execute {
            val bmp = loadBitmapBlocking(appContext)
            mainHandler.post { callback.accept(bmp) }
        }
    }

    @JvmStatic
    fun computeScrimColorInt(baseBackgroundArgb: Int, scrimAlpha: Float): Int {
        val alphaInt = Math.round(clampScrimAlpha(scrimAlpha) * 255f).coerceIn(0, 255)
        return ColorUtils.setAlphaComponent(baseBackgroundArgb or (0xFF shl 24), alphaInt)
    }

    private fun wallpaperFile(context: Context): File =
        File(File(context.filesDir, "backgrounds"), "wallpaper.img")

    private fun saveFromUriBlocking(context: Context, uri: Uri): Boolean {
        return try {
            val appContext = context.applicationContext
            val resolver = appContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            } ?: return false

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var inSampleSize = 1
            while (bounds.outWidth / inSampleSize > MAX_DECODE_DIM_PX ||
                bounds.outHeight / inSampleSize > MAX_DECODE_DIM_PX
            ) {
                inSampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmap = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return false

            if (!persistBitmap(appContext, bitmap)) {
                bitmap.recycle()
                return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Compress + atomically persist bitmap, update prefs/cache. Takes ownership on success (caller recycles on failure). */
    private fun persistBitmap(appContext: Context, bitmap: Bitmap): Boolean {
        return try {
            val target = wallpaperFile(appContext)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "wallpaper.img.tmp")
            FileOutputStream(tmp).use { out ->
                val format = if (bitmap.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                bitmap.compress(format, 92, out)
                out.flush()
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.delete()
                return false
            }

            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val nextRev = prefs.getLong(KEY_REVISION, 0L) + 1L
            prefs.edit()
                .putBoolean(KEY_HAS_IMAGE, true)
                .putLong(KEY_REVISION, nextRev)
                .apply()

            synchronized(cacheLock) {
                cachedBitmap = bitmap
                cachedRevision = nextRev
            }
            Handler(Looper.getMainLooper()).post { version++ }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadBitmapBlocking(context: Context): Bitmap? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_IMAGE, false)) return null
        val rev = prefs.getLong(KEY_REVISION, 0L)
        synchronized(cacheLock) {
            val existing = cachedBitmap
            if (existing != null && !existing.isRecycled && cachedRevision == rev) {
                return existing
            }
        }
        val file = wallpaperFile(appContext)
        if (!file.isFile) return null
        val decoded = try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > MAX_DECODE_DIM_PX ||
                bounds.outHeight / sample > MAX_DECODE_DIM_PX
            ) {
                sample *= 2
            }
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
        synchronized(cacheLock) {
            cachedBitmap = decoded
            cachedRevision = rev
        }
        return decoded
    }
}
