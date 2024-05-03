package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.*
import org.graalvm.polyglot.Value
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.Key
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun needIV(algo: String): Boolean {
    return algo.startsWith("AES") || algo.startsWith("DES")
}

private fun needIV(cipher: Cipher): Boolean {
    val algo = cipher.algorithm
    return needIV(algo)
}

/**
 * Wrapper around some commonly used javax.crypto.* classes to make them easier
 * to use from scripts. We wrap [Cipher]s in [Crypto] objects to make them more
 * ergonomic as well.
 */
class ScriptCryptoHelper {

    class StreamCipher(
        private val cipher: Cipher,
        private var iv: ByteArray? = null
    ) {

        private val needIV = needIV(cipher)

        @ScriptApi
        fun push(value: Value) {
            var asBytes = value.asBinaryArg()
            if (this.needIV && (iv == null || iv!!.size < cipher.blockSize)) {
                asBytes = pushIV(asBytes) ?: return
            }
            cipher.update(asBytes)
        }

        private fun pushIV(bytes: ByteArray): ByteArray? {
            if (iv == null) {
                if (bytes.size >= cipher.blockSize) {
                    iv = bytes.slice(0 until cipher.blockSize).toByteArray()
                    return bytes.slice(cipher.blockSize until bytes.size).toByteArray()
                } else {
                    iv = bytes
                    return null
                }
            }

            val currentIV = iv!!
            val needed = cipher.blockSize - currentIV.size
            if (bytes.size <= needed) {
                iv = currentIV + bytes
                return null
            }

            iv = currentIV + bytes.slice(0 until needed).toByteArray()
            return bytes.slice(needed until bytes.size).toByteArray()
        }

        @ScriptApi
        fun finish(): ByteArray = iv?.let {
            it + cipher.doFinal()
        } ?: cipher.doFinal()
    }

    class Crypto(
        private val algo: String
    ) {

        private var key: Key? = null


        @ScriptApi
        fun setKey(key: Value) {
            if (algo.startsWith("AES")) {
                setAesKey(key.asBinaryArg())
            } else if (algo.startsWith("RSA")) {
                setRsaKey(key.asString())
            } else if (algo.startsWith("DESede")) {
                setDesKey(key.asBinaryArg())
            } else {
                throw IllegalArgumentException("Crypto object's interface only supports RSA, AES, and DESede")
            }
        }

        fun setRsaKey(key: String) {
            if (key.endsWith(".pem") || key.endsWith(".key") || key.endsWith(".cert")) {
                setRsaKey(Files.readString(Paths.get(key)))
                return
            }
            when {
                key.contains("PRIVATE KEY") -> setRsaKeyFromPrivatePem(key)
                key.contains("PUBLIC KEY") -> setRsaKeyFromPublicPem(key)
                key.contains("CERTIFICATE") -> setRsaKeyFromCertificatePem(key)
            }
        }

        @ScriptApi
        fun mustGetCipher(mode: Int, iv: ByteArray? = null): Cipher {
            val key = this.key ?: throw IllegalStateException("no key set")
            val cipher = Cipher.getInstance(algo)

            if (iv != null) {
                val ivSpec = IvParameterSpec(iv)
                cipher.init(mode, key, ivSpec)
            } else if (mode == Cipher.ENCRYPT_MODE && (algo.startsWith("AES") || algo.startsWith("DES"))) {
                val bytes = ByteArray(cipher.blockSize)
                SecureRandom.getInstanceStrong().nextBytes(bytes)
                val ivSpec = IvParameterSpec(bytes)
                cipher.init(mode, key, ivSpec)
            } else {
                cipher.init(mode, key)
            }
            return cipher
        }

        /**
         * Returns an object suitable for decrypting streamed data, note that if
         * the algorithm requires an IV, it will be assumed to be the first
         * BLOCK_SIZE bytes pushed
         */
        @ScriptApi
        fun getDecryptStream(): StreamCipher =
            StreamCipher(mustGetCipher(Cipher.DECRYPT_MODE))

        /**
         * Return an object suitable for encrypting streamed data. Note that if
         * the algorithm requires an IV it will be returned as the first
         * BLOCK_SIZE bytes when `finish()` is called
         */
        @ScriptApi
        fun getEncryptStream(): StreamCipher {
            val iv = if (needIV()) {
                createIV()
            } else {
                null
            }
            return StreamCipher(mustGetCipher(Cipher.ENCRYPT_MODE), iv)
        }

        @ScriptApi
        fun getDecryptStreamWithIV(iv: Value): StreamCipher =
            StreamCipher(mustGetCipher(Cipher.DECRYPT_MODE, iv.asBinaryArg()))

        @ScriptApi
        fun getEncryptStreamWithIV(iv: Value): StreamCipher =
            StreamCipher(mustGetCipher(Cipher.ENCRYPT_MODE, iv.asBinaryArg()))

        /**
         * Encrypt the given data, note that if no IV is provided and one is
         * required, it is put at the start of the returned byte array
         */
        @ScriptApi
        fun encrypt(value: Value): ByteArray {
            val iv = if (needIV()) {
                createIV()
            } else {
                null
            }
            val encrypted = mustGetCipher(Cipher.ENCRYPT_MODE, iv).doFinal(value.asBinaryArg())
            if (iv == null) {
                return encrypted
            }

            return iv + encrypted
        }

        /**
         * Create a random IV
         */
        @ScriptApi
        fun createIV(): ByteArray {
            val cipher = Cipher.getInstance(algo)
            val bytes = ByteArray(cipher.blockSize)
            SecureRandom.getInstanceStrong().nextBytes(bytes)
            return bytes
        }

        private fun needIV(): Boolean = needIV(algo)

        /**
         * Decrypt an entire message in one call, note that if an IV is
         * required it is assumed to be the first BLOCK_SIZE bytes of the
         * value
         */
        @ScriptApi
        fun decrypt(value: Value): ByteArray {
            if (!needIV()) {
                return mustGetCipher(Cipher.DECRYPT_MODE).doFinal(value.asBinaryArg())
            }

            val cipher = Cipher.getInstance(algo)

            val asBytes = value.asBinaryArg()
            if (asBytes.size < 2 * cipher.blockSize) {
                throw IllegalArgumentException("message is too small to contain an IV and encrypted value")
            }

            val iv = asBytes.slice(0 until cipher.blockSize).toByteArray()
            val message = asBytes.slice(cipher.blockSize until asBytes.size).toByteArray()
            return mustGetCipher(Cipher.DECRYPT_MODE, iv).doFinal(message)

        }

        /**
         * Encrypt the message with the provided IV
         */
        @ScriptApi
        fun encryptWithIV(value: Value, iv: Value): ByteArray =
            mustGetCipher(Cipher.ENCRYPT_MODE, iv.asBinaryArg()).doFinal(value.asBinaryArg())

        /**
         * Decrypt the message with the provided IV
         */
        @ScriptApi
        fun decryptWithIV(value: Value, iv: Value): ByteArray =
            mustGetCipher(Cipher.DECRYPT_MODE, iv.asBinaryArg()).doFinal(value.asBinaryArg())

        private fun unPem(pem: String): ByteArray {
            val start = pem.indexOf('\n') + 1
            val end = pem.lastIndexOf('\n')
            val noHeader = pem.substring(start, end)
            return Base64.getDecoder().decode(noHeader.replace("\n", ""))
        }

        private fun setRsaKeyFromCertificatePem(pem: String) {
            val factory = CertificateFactory.getInstance("X.509")
            val cert = factory.generateCertificate(ByteArrayInputStream(pem.toByteArray()))
            key = cert.publicKey
        }

        private fun setRsaKeyFromPrivatePem(pem: String) {
            val spec = PKCS8EncodedKeySpec(unPem(pem))
            key = KeyFactory.getInstance("RSA").generatePrivate(spec)
        }

        private fun setRsaKeyFromPublicPem(pem: String) {
            val spec = X509EncodedKeySpec(unPem(pem))
            key = KeyFactory.getInstance("RSA").generatePublic(spec)
        }


        private fun setDesKey(key: ByteArray) {
            this.key = SecretKeySpec(key, "DESede")
        }

        private fun setAesKey(key: ByteArray) {
            this.key = SecretKeySpec(key, "AES")
        }

        @ScriptApi
        fun getDecryptCipher(): Cipher = key?.let {
            getMode(Cipher.DECRYPT_MODE, it)
        } ?: run {
            Cipher.getInstance(algo)
        }

        @ScriptApi
        fun getEncryptCipher(): Cipher = key?.let {
            getMode(Cipher.ENCRYPT_MODE, it)
        } ?: run {
            Cipher.getInstance(algo)
        }

        private fun getMode(mode: Int, key: Key): Cipher {
            val cipher = Cipher.getInstance(algo)
            cipher.init(mode, key)
            return cipher
        }

    }

    enum class AesPadding {
        NoPadding,
        PKCS5
    }

    companion object {
        @ScriptApi
        @JvmField
        val AES_NO_PADDING = AesPadding.NoPadding

        @ScriptApi
        @JvmField
        val AES_PKCS5 = AesPadding.PKCS5

        @ScriptApi
        @JvmField
        val ENCRYPT = Cipher.ENCRYPT_MODE

        @ScriptApi
        @JvmField
        val DECRYPT = Cipher.DECRYPT_MODE
    }

    @ScriptApi
    fun getCrypto(algo: String): Crypto =
        Crypto(algo)

    @ScriptApi
    fun getAesGcm(padding: AesPadding = AesPadding.NoPadding): Crypto =
        getAes("GCM", padding)

    @ScriptApi
    fun getAesEcb(padding: AesPadding): Crypto =
        getAes("ECB", padding)

    @ScriptApi
    fun getAesCbc(padding: AesPadding): Crypto =
        getAes("CBC", padding)

    @ScriptApi
    fun md5sum(value: Value): ByteArray = getMd5Digest().digest(value.asBinaryArg())

    @ScriptApi
    fun sha1sum(value: Value): ByteArray = getSha1Digest().digest(value.asBinaryArg())

    @ScriptApi
    fun sha256sum(value: Value): ByteArray = getSha256Digest().digest(value.asBinaryArg())

    @ScriptApi
    fun getMessageDigest(name: String): MessageDigest = MessageDigest.getInstance(name)

    @ScriptApi
    fun getMd5Digest(): MessageDigest = MessageDigest.getInstance("MD5")

    @ScriptApi
    fun getSha1Digest(): MessageDigest = MessageDigest.getInstance("SHA-1")

    @ScriptApi
    fun getSha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    fun getAes(algo: String, padding: AesPadding): Crypto {
        val padString = when (padding) {
            AesPadding.NoPadding -> "NoPadding"
            AesPadding.PKCS5 -> "PKCSPadding"
        }
        return getCrypto("AES/$algo/$padString")
    }

}