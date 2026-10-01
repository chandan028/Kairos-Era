package com.kairosera.ai

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import java.io.File

/** Memory and storage readings, behind an interface so the rules can be tested. */
interface DeviceInfo {
    fun totalRamBytes(): Long
    fun availableRamBytes(): Long
    fun freeBytes(dir: File): Long
}

class AndroidDeviceInfo(private val context: Context) : DeviceInfo {
    private fun memory() = ActivityManager.MemoryInfo().also {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }

    override fun totalRamBytes(): Long = runCatching { memory().totalMem }.getOrDefault(0L)
    override fun availableRamBytes(): Long = runCatching { memory().availMem }.getOrDefault(0L)
    override fun freeBytes(dir: File): Long = runCatching {
        var d: File? = dir
        while (d != null && !d.exists()) d = d.parentFile
        StatFs((d ?: dir).path).availableBytes
    }.getOrDefault(0L)
}

/** What this phone can be expected to do with Gemma-4-E2B-it. */
enum class DeviceFit { OK, BELOW_RECOMMENDED, TOO_SMALL }

/**
 * AI Edge Gallery lists Gemma-4-E2B-it with an 8 GB minimum. Phones sold as 8 GB report about
 * 7.2 to 7.6 GB to apps, so 7 GB counts as meeting it. Between 5.5 and 7 GB Kairos warns and
 * still lets the person try (CPU may work); below 5.5 GB it does not try at all.
 */
object ModelRequirements {
    private const val GB = 1024L * 1024 * 1024
    const val RECOMMENDED_RAM_BYTES = 7L * GB
    const val MIN_RAM_BYTES = 5L * GB + GB / 2

    fun fit(totalRam: Long): DeviceFit = when {
        totalRam <= 0L -> DeviceFit.BELOW_RECOMMENDED // unknown: let the engine decide
        totalRam < MIN_RAM_BYTES -> DeviceFit.TOO_SMALL
        totalRam < RECOMMENDED_RAM_BYTES -> DeviceFit.BELOW_RECOMMENDED
        else -> DeviceFit.OK
    }

    fun gb(bytes: Long): Double = bytes / GB.toDouble()
}
