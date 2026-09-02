// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.emojikitchen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.net.toUri
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.BreakIterator
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class EmojiKitchenCombo(
    val leftCodepoint: String,
    val rightCodepoint: String,
    val url: String
)

data class TextCombosResult(
    val emojis: List<String>,
    val combos: List<EmojiKitchenCombo>,
    val charsCount: Int
)

object EmojiKitchenHelper {
    private const val TAG = "EmojiKitchenHelper"
    private const val ASSET_FILE = "emojikitchen/emojikitchen_data.bin.gz"

    private val helperScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private var isInitialized = false
    private val initLock = Any()

    private var emojis: Array<String> = emptyArray()
    private var dates: Array<String> = emptyArray()
    private val emojiToIndex = HashMap<String, Int>()
    private var offsets: IntArray = IntArray(0)
    private var counts: IntArray = IntArray(0)
    private var comboBytes: ByteArray = ByteArray(0)

    fun ensureInitialized(context: Context) {
        if (isInitialized) return
        synchronized(initLock) {
            if (isInitialized) return
            try {
                val inputStream: InputStream = context.assets.open(ASSET_FILE)
                val gzipStream = GZIPInputStream(inputStream)
                val data = gzipStream.readBytes()
                gzipStream.close()
                inputStream.close()

                var offset = 0
                val numEmojis = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                offset += 2

                val emojiList = ArrayList<String>(numEmojis)
                for (i in 0 until numEmojis) {
                    val len = data[offset].toInt() and 0xFF
                    offset += 1
                    val str = String(data, offset, len, Charsets.US_ASCII)
                    offset += len
                    emojiList.add(str)
                    emojiToIndex[str] = i
                    val stripped = str.replace("-fe0f", "")
                    if (!emojiToIndex.containsKey(stripped)) {
                        emojiToIndex[stripped] = i
                    }
                }
                emojis = emojiList.toTypedArray()

                val numDates = data[offset].toInt() and 0xFF
                offset += 1
                val dateList = ArrayList<String>(numDates)
                for (i in 0 until numDates) {
                    val len = data[offset].toInt() and 0xFF
                    offset += 1
                    val str = String(data, offset, len, Charsets.US_ASCII)
                    offset += len
                    dateList.add(str)
                }
                dates = dateList.toTypedArray()

                offsets = IntArray(numEmojis)
                counts = IntArray(numEmojis)

                val remainingBytes = data.size - offset
                comboBytes = ByteArray(remainingBytes)
                var comboByteWritePos = 0

                for (i in 0 until numEmojis) {
                    val cnt = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
                    offset += 2
                    offsets[i] = comboByteWritePos / 3
                    counts[i] = cnt
                    val bytesToCopy = cnt * 3
                    System.arraycopy(data, offset, comboBytes, comboByteWritePos, bytesToCopy)
                    offset += bytesToCopy
                    comboByteWritePos += bytesToCopy
                }

                isInitialized = true
                Log.i(TAG, "EmojiKitchen initialized: ${emojis.size} emojis, ${dates.size} dates")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EmojiKitchenHelper from assets", e)
            }
        }
    }

    private fun toU(k: String): String {
        return k.split('-').joinToString("-") { "u$it" }
    }

    fun toCodepoint(emoji: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < emoji.length) {
            val cp = Character.codePointAt(emoji, i)
            if (sb.isNotEmpty()) sb.append("-")
            sb.append(Integer.toHexString(cp).lowercase())
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    fun isEmojiCodePoint(cp: Int): Boolean {
        return (cp in 0x1F000..0x1FAFF)
                || (cp in 0x2600..0x27BF)
                || (cp in 0x2B50..0x2B55)
                || (cp in 0x231A..0x23F3)
                || (cp in 0x2194..0x21AA)
                || (cp in 0x2934..0x2935)
                || (cp in 0x3297..0x3299)
                || cp == 0xFE0F || cp == 0x200D
    }

    fun isEmoji(s: String): Boolean {
        if (s.isEmpty()) return false
        var i = 0
        var hasEmojiChar = false
        while (i < s.length) {
            val cp = Character.codePointAt(s, i)
            if (isEmojiCodePoint(cp)) {
                hasEmojiChar = true
            } else if (!Character.isWhitespace(cp)) {
                return false
            }
            i += Character.charCount(cp)
        }
        return hasEmojiChar
    }

    fun containsEmoji(text: CharSequence?): Boolean {
        if (text.isNullOrEmpty()) return false
        var i = 0
        while (i < text.length) {
            val cp = Character.codePointAt(text, i)
            if (isEmojiCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    private fun findIndexForEmoji(emoji: String): Int? {
        val cp = toCodepoint(emoji)
        emojiToIndex[cp]?.let { return it }
        val stripped = cp.replace("-fe0f", "")
        return emojiToIndex[stripped]
    }

    fun getCombinations(context: Context, emoji: String, limit: Int = 30): List<EmojiKitchenCombo> {
        ensureInitialized(context)
        val idx = findIndexForEmoji(emoji) ?: return emptyList()
        val cnt = counts[idx]
        if (cnt == 0) return emptyList()

        val result = ArrayList<EmojiKitchenCombo>(minOf(cnt, limit))
        val baseOffset = offsets[idx] * 3
        val k1 = emojis[idx]
        val u1 = toU(k1)

        val totalToTake = minOf(cnt, limit)
        for (j in 0 until totalToTake) {
            val offset = baseOffset + j * 3
            val otherId = ((comboBytes[offset].toInt() and 0xFF) shl 8) or (comboBytes[offset + 1].toInt() and 0xFF)
            val info = comboBytes[offset + 2].toInt() and 0xFF
            val folderIsFirst = (info and 0x80) != 0
            val dateId = info and 0x7F

            val k2 = emojis[otherId]
            val u2 = toU(k2)
            val folder = if (folderIsFirst) u1 else u2
            val other = if (folderIsFirst) u2 else u1
            val date = dates[dateId]
            val url = "https://www.gstatic.com/android/keyboard/emojikitchen/$date/$folder/${folder}_$other.png"
            result.add(EmojiKitchenCombo(k1, k2, url))
        }
        return result
    }

    fun getCombination(context: Context, emoji1: String, emoji2: String): EmojiKitchenCombo? {
        ensureInitialized(context)
        val idx1 = findIndexForEmoji(emoji1) ?: return null
        val idx2 = findIndexForEmoji(emoji2) ?: return null

        val cnt1 = counts[idx1]
        val baseOffset = offsets[idx1] * 3
        val k1 = emojis[idx1]
        val k2 = emojis[idx2]
        val u1 = toU(k1)
        val u2 = toU(k2)

        for (j in 0 until cnt1) {
            val offset = baseOffset + j * 3
            val otherId = ((comboBytes[offset].toInt() and 0xFF) shl 8) or (comboBytes[offset + 1].toInt() and 0xFF)
            if (otherId == idx2) {
                val info = comboBytes[offset + 2].toInt() and 0xFF
                val folderIsFirst = (info and 0x80) != 0
                val dateId = info and 0x7F
                val folder = if (folderIsFirst) u1 else u2
                val other = if (folderIsFirst) u2 else u1
                val date = dates[dateId]
                val url = "https://www.gstatic.com/android/keyboard/emojikitchen/$date/$folder/${folder}_$other.png"
                return EmojiKitchenCombo(k1, k2, url)
            }
        }
        return null
    }

    fun findTrailingEmojis(text: CharSequence?): Pair<List<String>, Int> {
        if (text.isNullOrEmpty()) return Pair(emptyList(), 0)
        val sub = if (text.length > 25) text.subSequence(text.length - 25, text.length).toString() else text.toString()

        val it = BreakIterator.getCharacterInstance()
        it.setText(sub)
        val graphemes = ArrayList<String>()
        var start = it.first()
        var end = it.next()
        while (end != BreakIterator.DONE) {
            graphemes.add(sub.substring(start, end))
            start = end
            end = it.next()
        }

        val trailing = ArrayList<String>()
        var charsCount = 0
        for (g in graphemes.reversed()) {
            if (g.isBlank()) {
                if (trailing.isEmpty()) {
                    charsCount += g.length
                    continue
                } else {
                    break
                }
            }
            if (isEmoji(g)) {
                trailing.add(0, g)
                charsCount += g.length
                if (trailing.size == 2) break
            } else {
                break
            }
        }
        return Pair(trailing, charsCount)
    }

    fun getCombinationsForText(context: Context, text: CharSequence?, limit: Int = 30): TextCombosResult? {
        val (trailingEmojis, charsCount) = findTrailingEmojis(text)
        if (trailingEmojis.isEmpty()) return null

        ensureInitialized(context)
        val combos = ArrayList<EmojiKitchenCombo>()

        if (trailingEmojis.size >= 2) {
            val e1 = trailingEmojis[trailingEmojis.size - 2]
            val e2 = trailingEmojis.last()
            val directCombo = getCombination(context, e1, e2)
            if (directCombo != null) {
                combos.add(directCombo)
            }
            val e2Combos = getCombinations(context, e2, limit)
            for (c in e2Combos) {
                if (c.url != directCombo?.url && combos.size < limit) {
                    combos.add(c)
                }
            }
        } else {
            val e = trailingEmojis.first()
            combos.addAll(getCombinations(context, e, limit))
        }

        if (combos.isEmpty()) return null
        return TextCombosResult(trailingEmojis, combos, charsCount)
    }

    suspend fun getOrDownloadStickerFile(context: Context, combo: EmojiKitchenCombo): File? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "stickers/emojikitchen").apply { mkdirs() }
        val fileName = "ek_${combo.leftCodepoint}_${combo.rightCodepoint}.webp"
        val targetFile = File(dir, fileName)
        if (targetFile.exists() && targetFile.length() > 0) {
            return@withContext targetFile
        }

        try {
            val request = Request.Builder().url(combo.url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext null

                FileOutputStream(targetFile).use { fos ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, fos)
                    } else {
                        @Suppress("DEPRECATION")
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 90, fos)
                    }
                }
                bitmap.recycle()
                targetFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/saving sticker from ${combo.url}", e)
            null
        }
    }

    fun getStickerContentUri(context: Context, file: File): Uri {
        return "content://${context.packageName}.stickercontentprovider/stickers/emojikitchen/${file.name}".toUri()
    }

    fun commitSticker(
        context: Context,
        latinIME: LatinIME,
        combo: EmojiKitchenCombo,
        charsCountToDelete: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        helperScope.launch {
            val stickerFile = getOrDownloadStickerFile(context, combo)
            withContext(Dispatchers.Main) {
                if (stickerFile == null) {
                    Toast.makeText(context, "Erro ao carregar figurinha", Toast.LENGTH_SHORT).show()
                    onComplete?.invoke(false)
                    return@withContext
                }
                val contentUri = getStickerContentUri(context, stickerFile)
                val committed = latinIME.commitKlipyContent(contentUri, "Emoji Kitchen", "image/webp.wasticker")
                if (committed) {
                    if (charsCountToDelete > 0) {
                        latinIME.currentInputConnection?.deleteSurroundingText(charsCountToDelete, 0)
                    }
                    latinIME.removeExternalSuggestions()
                }
                onComplete?.invoke(committed)
            }
        }
    }
}
