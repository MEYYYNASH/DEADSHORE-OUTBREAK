package com.example.game.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural low-latency PCM sound synthesizer backed by SoundPool.
 * Generates crisp gunshot, melee, reload, zombie, and pickup audio into cache WAVs on init.
 */
class SoundEngine(context: Context) {
  private val soundPool: SoundPool
  private val clipIds = mutableMapOf<Clip, Int>()
  var muted: Boolean = false

  enum class Clip {
    M4_SHOT,
    GLOCK_SHOT,
    MELEE_SWING,
    RELOAD,
    DRY_FIRE,
    ZOMBIE_HIT,
    ZOMBIE_GROAN,
    PLAYER_HURT,
    PICKUP,
    WAVE_HORN,
  }

  init {
    val attrs =
      AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()
    soundPool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attrs).build()
    val dir = File(context.cacheDir, "sfx").apply { mkdirs() }

    clipIds[Clip.M4_SHOT] = loadGenerated(dir, "m4.wav", synthGunshot(0.16f, 180f, 0.95f))
    clipIds[Clip.GLOCK_SHOT] = loadGenerated(dir, "glock.wav", synthGunshot(0.12f, 290f, 0.72f))
    clipIds[Clip.MELEE_SWING] = loadGenerated(dir, "bat.wav", synthWhoosh(0.18f, 140f))
    clipIds[Clip.RELOAD] = loadGenerated(dir, "reload.wav", synthClick(0.22f, 760f))
    clipIds[Clip.DRY_FIRE] = loadGenerated(dir, "dry.wav", synthClick(0.06f, 1100f))
    clipIds[Clip.ZOMBIE_HIT] = loadGenerated(dir, "zhit.wav", synthImpact(0.14f, 110f))
    clipIds[Clip.ZOMBIE_GROAN] = loadGenerated(dir, "zgroan.wav", synthGroan(0.45f, 92f))
    clipIds[Clip.PLAYER_HURT] = loadGenerated(dir, "hurt.wav", synthImpact(0.22f, 75f))
    clipIds[Clip.PICKUP] = loadGenerated(dir, "pickup.wav", synthChime(0.20f))
    clipIds[Clip.WAVE_HORN] = loadGenerated(dir, "horn.wav", synthHorn(0.55f, 146f))
  }

  fun play(clip: Clip, volume: Float = 0.85f, pitch: Float = 1.0f) {
    if (muted) return
    val id = clipIds[clip] ?: return
    soundPool.play(id, volume, volume, 1, 0, pitch.coerceIn(0.6f, 1.5f))
  }

  fun release() {
    soundPool.release()
  }

  private fun loadGenerated(dir: File, name: String, samples: ShortArray): Int {
    val file = File(dir, name)
    writeWav(file, samples, SAMPLE_RATE)
    return soundPool.load(file.absolutePath, 1)
  }

  private fun synthGunshot(durationSec: Float, baseFreq: Float, punch: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    val rng = Random(baseFreq.toInt())
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val env = exp(-t * 28f)
      val snap = sin(2.0 * PI * baseFreq * exp(-t * 14f) * t).toFloat()
      val noise = (rng.nextFloat() * 2f - 1f)
      val v = (noise * 0.65f + snap * 0.55f) * env * punch
      out[i] = (v.coerceIn(-1f, 1f) * 30000f).toInt().toShort()
    }
    return out
  }

  private fun synthWhoosh(durationSec: Float, baseFreq: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    val rng = Random(77)
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val p = t / durationSec
      val env = sin(PI * p).toFloat()
      val tone = sin(2.0 * PI * (baseFreq + p * 180f) * t).toFloat()
      val air = (rng.nextFloat() * 2f - 1f) * 0.35f
      out[i] = ((tone * 0.4f + air) * env * 22000f).toInt().toShort()
    }
    return out
  }

  private fun synthClick(durationSec: Float, freq: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val env = exp(-t * 42f)
      val secondClick = if (t > 0.11f) exp(-(t - 0.11f) * 50f) else 0f
      val v = sin(2.0 * PI * freq * t).toFloat() * (env + secondClick * 0.8f)
      out[i] = (v.coerceIn(-1f, 1f) * 20000f).toInt().toShort()
    }
    return out
  }

  private fun synthImpact(durationSec: Float, freq: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    val rng = Random(19)
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val env = exp(-t * 19f)
      val thud = sin(2.0 * PI * freq * t).toFloat()
      val grit = (rng.nextFloat() * 2f - 1f) * 0.35f
      out[i] = ((thud * 0.7f + grit) * env * 26000f).toInt().toShort()
    }
    return out
  }

  private fun synthGroan(durationSec: Float, freq: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val env = sin(PI * (t / durationSec)).toFloat()
      val f = freq * (1f - 0.18f * (t / durationSec))
      val v = (sin(2.0 * PI * f * t) + 0.45 * sin(2.0 * PI * (f * 1.52) * t)).toFloat()
      out[i] = ((v * 0.45f) * env * 20000f).toInt().toShort()
    }
    return out
  }

  private fun synthChime(durationSec: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val f = if (t < 0.09f) 660f else 990f
      val env = exp(-t * 14f)
      val v = sin(2.0 * PI * f * t).toFloat() * env
      out[i] = (v * 20000f).toInt().toShort()
    }
    return out
  }

  private fun synthHorn(durationSec: Float, freq: Float): ShortArray {
    val count = (SAMPLE_RATE * durationSec).toInt()
    val out = ShortArray(count)
    for (i in 0 until count) {
      val t = i / SAMPLE_RATE.toFloat()
      val env = sin(PI * (t / durationSec)).toFloat()
      val v =
        (sin(2.0 * PI * freq * t) +
            0.5 * sin(2.0 * PI * freq * 2.0 * t) +
            0.25 * sin(2.0 * PI * freq * 3.0 * t))
          .toFloat() * 0.38f * env
      out[i] = (v.coerceIn(-1f, 1f) * 24000f).toInt().toShort()
    }
    return out
  }

  private fun writeWav(file: File, samples: ShortArray, sampleRate: Int) {
    val dataSize = samples.size * 2
    val totalSize = 36 + dataSize
    val header = ByteArray(44)
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    writeIntLE(header, 4, totalSize)
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    writeIntLE(header, 16, 16)
    writeShortLE(header, 20, 1)
    writeShortLE(header, 22, 1)
    writeIntLE(header, 24, sampleRate)
    writeIntLE(header, 28, sampleRate * 2)
    writeShortLE(header, 32, 2)
    writeShortLE(header, 34, 16)
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    writeIntLE(header, 40, dataSize)

    val pcmBytes = ByteArray(dataSize)
    for (i in samples.indices) {
      val s = samples[i].toInt()
      pcmBytes[i * 2] = (s and 0xFF).toByte()
      pcmBytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
    }
    FileOutputStream(file).use { fos ->
      fos.write(header)
      fos.write(pcmBytes)
    }
  }

  private fun writeIntLE(buf: ByteArray, offset: Int, v: Int) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
    buf[offset + 2] = ((v shr 16) and 0xFF).toByte()
    buf[offset + 3] = ((v shr 24) and 0xFF).toByte()
  }

  private fun writeShortLE(buf: ByteArray, offset: Int, v: Int) {
    buf[offset] = (v and 0xFF).toByte()
    buf[offset + 1] = ((v shr 8) and 0xFF).toByte()
  }

  private companion object {
    const val SAMPLE_RATE = 22050
  }
}
