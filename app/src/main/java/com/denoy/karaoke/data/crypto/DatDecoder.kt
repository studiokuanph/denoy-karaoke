package com.denoy.karaoke.data.crypto

import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class DatDecoder(private val path: String) {

    companion object {
        private const val PASSPHRASE = "kyUv!nC3nt_m3g@0k3"
        private const val STATIC_IV_RAW = "JtNpObAv6714@*#&"
    }

    private val aesKey: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256").digest(PASSPHRASE.toByteArray())
    }

    fun readChunk(offset: Long, size: Int): ByteArray {
        val file = RandomAccessFile(path, "r")
        file.seek(offset)
        val data = ByteArray(size)
        file.readFully(data)
        file.close()
        return data
    }

    fun decode(offset: Long, size: Int): ByteArray {
        val chunk = readChunk(offset, size)
        return decodeChunk(chunk)
    }

    private fun decodeChunk(chunk: ByteArray): ByteArray {
        // Try methods in order
        tryZlib(chunk)?.let { return it }
        tryZlibSkipHeader(chunk)?.let { return it }
        tryAesDecrypt(chunk)?.let { return it }
        tryAesWithHeader(chunk)?.let { return it }
        throw DecodeException("Cannot decode chunk of ${chunk.size} bytes")
    }

    private fun tryZlib(data: ByteArray): ByteArray? {
        return tryInflate(data)
    }

    private fun tryZlibSkipHeader(data: ByteArray): ByteArray? {
        for (skip in 0 until minOf(64, data.size)) {
            val result = tryInflate(data.copyOfRange(skip, data.size))
            if (result != null && isMidi(result)) return result
        }
        return null
    }

    private fun tryAesDecrypt(data: ByteArray): ByteArray? {
        val trimmed = data.dropLastWhile { it == 0.toByte() }.toByteArray()
        for (iv in getIvCandidates()) {
            val decrypted = aesDecrypt(trimmed, iv) ?: continue
            val result = tryInflate(decrypted)
            if (result != null && isMidi(result)) return result
        }
        return null
    }

    private fun tryAesWithHeader(data: ByteArray): ByteArray? {
        val headerSize = 13
        if (data.size < headerSize + 16) return null
        val payload = data.copyOfRange(headerSize, data.size)
        return tryAesDecrypt(payload)
    }

    private fun getIvCandidates(): List<ByteArray> {
        val candidates = mutableListOf<ByteArray>()
        candidates.add(STATIC_IV_RAW.toByteArray())
        candidates.add(MessageDigest.getInstance("SHA-256")
            .digest(STATIC_IV_RAW.toByteArray()).copyOfRange(0, 16))
        candidates.add(MessageDigest.getInstance("SHA-256")
            .digest(PASSPHRASE.toByteArray()).copyOfRange(0, 16))
        candidates.add(MessageDigest.getInstance("MD5")
            .digest(STATIC_IV_RAW.toByteArray()))
        candidates.add(MessageDigest.getInstance("MD5")
            .digest(PASSPHRASE.toByteArray()))
        return candidates
    }

    private fun aesDecrypt(data: ByteArray, iv: ByteArray): ByteArray? {
        if (data.size % 16 != 0) return null
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val keySpec = SecretKeySpec(aesKey, "AES")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(data)
        } catch (e: Exception) {
            null
        }
    }

    private fun tryInflate(data: ByteArray): ByteArray? {
        return try {
            val inflater = Inflater(true)
            inflater.setInput(data)
            val result = ByteArray(data.size * 4)
            val resultLen = inflater.inflate(result)
            inflater.end()
            if (resultLen <= 0) return null
            result.copyOfRange(0, resultLen)
        } catch (e: Exception) {
            null
        }
    }

    private fun isMidi(data: ByteArray): Boolean {
        if (data.size < 4) return false
        val sig = String(data, 0, 4, Charsets.US_ASCII)
        return sig == "MThd" || sig == "MTrk" || sig == "RIFF"
    }

    class DecodeException(message: String) : Exception(message)
}
