package com.carvesystems.burpscript

import com.carvesystems.burpscript.interop.*
import org.graalvm.polyglot.Value
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.security.*
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wrapper around some commonly used javax.crypto.* classes to make them easier
 * to use from scripts. We wrap [Cipher]s in [CipherApi] objects to make them more
 * ergonomic as well.
 */
class ScriptCryptoHelper {
    enum class AesPadding {
        NoPadding, PKCS5
    }

    companion object {
        @ScriptApi
        @JvmField
        val AES_NO_PADDING = AesPadding.NoPadding

        @ScriptApi
        @JvmField
        val AES_PKCS5 = AesPadding.PKCS5
    }

    /**
     * Get a new cipher object for the given algorithm
     *
     * @see https://docs.oracle.com/javase/8/docs/api/javax/crypto/Cipher.html
     * @param algo the algorithm to use
     *      AES/CBC/NoPadding (128)
     *      AES/CBC/PKCS5Padding (128)
     *      AES/ECB/NoPadding (128)
     *      AES/ECB/PKCS5Padding (128)
     *      DES/CBC/NoPadding (56)
     *      DES/CBC/PKCS5Padding (56)
     *      DES/ECB/NoPadding (56)
     *      DES/ECB/PKCS5Padding (56)
     *      DESede/CBC/NoPadding (168)
     *      DESede/CBC/PKCS5Padding (168)
     *      DESede/ECB/NoPadding (168)
     *      DESede/ECB/PKCS5Padding (168)
     *      RSA/ECB/PKCS1Padding (1024, 2048)
     *      RSA/ECB/OAEPWithSHA-1AndMGF1Padding (1024, 2048)
     *      RSA/ECB/OAEPWithSHA-256AndMGF1Padding (1024, 2048)
     */
    @ScriptApi
    fun newCipher(algo: String): CipherApi = CipherApi(algo)

    /**
     * Get a new signature object for the given algorithm
     *
     * @see https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature
     * @param algo the algorithm to use
     *      SHA1withRSA
     *      SHA224withRSA
     *      SHA256withRSA
     *      SHA384withRSA
     *      SHA512withRSA
     *      SHA512/224withRSA
     *      SHA512/256withRSA
     *      SHA1withDSA
     *      SHA224withDSA
     *      SHA256withDSA
     *      SHA384withDSA
     *      SHA512withDSA
     *      SHA1withECDSA
     *      SHA224withECDSA
     *      SHA256withECDSA
     *      SHA384withECDSA
     *      SHA512withECDSA
     */
    @ScriptApi
    fun newSignature(algo: String): SignatureApi = SignatureApi(algo)

    /**
     * Get a new signature verification object for the given algorithm
     *
     * @see https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Signature
     * @param algo the algorithm to use
     *      SHA1withRSA
     *      SHA224withRSA
     *      SHA256withRSA
     *      SHA384withRSA
     *      SHA512withRSA
     *      SHA512/224withRSA
     *      SHA512/256withRSA
     *      SHA1withDSA
     *      SHA224withDSA
     *      SHA256withDSA
     *      SHA384withDSA
     *      SHA512withDSA
     *      SHA1withECDSA
     *      SHA224withECDSA
     *      SHA256withECDSA
     *      SHA384withECDSA
     *      SHA512withECDSA
     */
    @ScriptApi
    fun newVerifySignature(algo: String): VerifySignatureApi = VerifySignatureApi(algo)

    /**
     *
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#Mac
     * @param algo the algorithm to use
     *      HmacSHA1
     *      HmacSHA224
     *      HmacSHA256
     *      HmacSHA384
     *      HmacSHA512
     */
    @ScriptApi
    fun newHmac(algo: String): HmacApi = HmacApi(algo)

    @ScriptApi
    fun newAesGcm(padding: AesPadding = AesPadding.NoPadding): CipherApi = newAes("GCM", padding)

    @ScriptApi
    fun newAesEcb(padding: AesPadding): CipherApi = newAes("ECB", padding)

    @ScriptApi
    fun newAesCbc(padding: AesPadding): CipherApi = newAes("CBC", padding)

    @ScriptApi
    fun md5sum(value: AnyBinary): UnsignedByteArray =
        newMd5Digest().digest(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    @ScriptApi
    fun sha1sum(value: AnyBinary): UnsignedByteArray =
        newSha1Digest().digest(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    @ScriptApi
    fun sha256sum(value: AnyBinary): UnsignedByteArray =
        newSha256Digest().digest(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    @ScriptApi
    fun sha512Sum(value: AnyBinary): UnsignedByteArray =
        newSha512Digest().digest(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    /**
     * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html#MessageDigest
     */
    @ExperimentalScriptApi
    fun newMessageDigest(name: String): MessageDigest = MessageDigest.getInstance(name)

    @ExperimentalScriptApi
    fun newMd5Digest(): MessageDigest = MessageDigest.getInstance("MD5")

    @ExperimentalScriptApi
    fun newSha1Digest(): MessageDigest = MessageDigest.getInstance("SHA-1")

    @ExperimentalScriptApi
    fun newSha256Digest(): MessageDigest = MessageDigest.getInstance("SHA-256")

    @ExperimentalScriptApi
    fun newSha512Digest(): MessageDigest = MessageDigest.getInstance("SHA-512")

    private fun newAes(algo: String, padding: AesPadding): CipherApi {
        val padString = when (padding) {
            AesPadding.NoPadding -> "NoPadding"
            AesPadding.PKCS5 -> "PKCSPadding"
        }
        return newCipher("AES/$algo/$padString")
    }
}

/**
 *
 *
 * Encryption/decryption API
 *
 *
 */

class CipherApi(
    private val algo: String
) {
    private var builder = CipherBuilder(algo)

    /**
     * The block size (in bytes) of the algorithm.
     */
    @ScriptApi
    @JvmField
    val blockSize = builder.blockSize

    /**
     * Whether the algorithm requires an IV to be used during encryption/decryption
     */
    @ScriptApi
    @JvmField
    val requiresIV = builder.requiresIV

    /**
     * Set the key material to use for encryption/decryption.
     *
     * @param key The interpretation of the key value depends on the algorithm in use:
     *      - For AES and DES, the key can be a byte array, integer array,
     *          hex string, or base64 string (@see [AnyBinary]).
     *      - For RSA, the key can a string containing a PKCS#8 PEM, X.509 PEM, or
     *          path to a PEM file path (*.pem, *.key, *.cert)
     */
    @ScriptApi
    fun setKey(key: Value) {
        if (algo.startsWith("AES")) {
            setAesKey(key.asAnyBinaryToByteArray())
        } else if (algo.startsWith("RSA")) {
            setRsaKey(key.asString())
        } else if (algo.startsWith("DESede")) {
            setDesKey(key.asAnyBinaryToByteArray())
        } else {
            throw IllegalArgumentException("Crypto interface only supports RSA, AES, and DESede")
        }
    }

    /**
     * Create a new random IV corresponding the block size of the algorithm
     */
    @ScriptApi
    fun createIV(): UnsignedByteArray {
        val bytes = ByteArray(blockSize)
        SecureRandom.getInstanceStrong().nextBytes(bytes)
        return bytes.toUnsignedByteArray()
    }

    /**
     * Encrypt the given data, note that if no IV is provided, and one is
     * required, a random IV of [blockSize] bytes will be generated
     * and put at the start of the returned byte array.
     *
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of the block size of the algorithm.
     */
    @ScriptApi
    fun encrypt(value: AnyBinary): UnsignedByteArray {
        val cipher = builder.withRandomIV(mode = Cipher.ENCRYPT_MODE).build()
        val iv = cipher.getIV() // null if no IV is required
        val encrypted = cipher.doFinal(value.asAnyBinaryToByteArray())

        return if (iv != null) {
            iv + encrypted
        } else {
            encrypted
        }.toUnsignedByteArray()
    }

    /**
     * Encrypt the message with the provided IV
     * @param value The message to encrypt. Can be a byte array, integer
     *         array, hex string, or base64 string (@see Value.asBinaryArg)
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of the block size of the algorithm.
     */
    @ScriptApi
    fun encryptWithIV(value: AnyBinary, iv: AnyBinary): UnsignedByteArray =
        builder.with(mode = Cipher.ENCRYPT_MODE, iv = iv.asAnyBinaryToByteArray()).build()
            .doFinal(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    /**
     * Decrypt an entire message in one call, note that if an IV is
     * required it is assumed to be the first [blockSize] bytes of
     * the value.
     *
     * @param value The message to decrypt. Can be a byte array, integer
     *         array, hex string, or base64 string (@see Value.asBinaryArg)
     */
    @ScriptApi
    fun decrypt(value: AnyBinary): UnsignedByteArray {
        if (!requiresIV) {
            return builder.with(mode = Cipher.DECRYPT_MODE).build().doFinal(value.asAnyBinaryToByteArray())
                .toUnsignedByteArray()
        }

        val asBytes = value.asAnyBinaryToByteArray()
        if (asBytes.size < 2 * blockSize) {
            throw IllegalArgumentException("message is too small to contain an IV and encrypted value")
        }

        // Read IV from first block
        val iv = asBytes.slice(0 until blockSize).toByteArray()
        val message = asBytes.slice(blockSize until asBytes.size).toByteArray()
        return builder.with(mode = Cipher.DECRYPT_MODE, iv = iv).build().doFinal(message).toUnsignedByteArray()
    }

    /**
     * Decrypt the message with the provided IV
     * @param value The message to encrypt. Can be a byte array, integer
     *         array, hex string, or base64 string (@see Value.asBinaryArg)
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of the block size of the algorithm.
     */
    @ScriptApi
    fun decryptWithIV(value: AnyBinary, iv: AnyBinary): UnsignedByteArray =
        builder.with(mode = Cipher.DECRYPT_MODE, iv = iv.asAnyBinaryToByteArray()).build()
            .doFinal(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    /**
     * Return an object suitable for encrypting streamed data. Note that if
     * the algorithm requires an IV, a random IV will be generated, and returned
     * as the first [blockSize] bytes when `finish()` is called
     */
    @ScriptApi
    fun getEncryptStream(): StreamCipher = EncryptStreamCipher(
        builder.withRandomIV(mode = Cipher.ENCRYPT_MODE).build(), writeIVToStream = requiresIV
    )

    /**
     * Returns an object suitable for encrypting streamed data.
     *
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of the block size of the algorithm.
     */
    @ScriptApi
    fun getEncryptStreamWithIV(iv: AnyBinary): StreamCipher = EncryptStreamCipher(
        builder.with(mode = Cipher.ENCRYPT_MODE, iv = iv.asAnyBinaryToByteArray()).build(), writeIVToStream = false
    )

    /**
     * Returns an object suitable for decrypting streamed data, note that if
     * the algorithm requires an IV, it will be assumed to be the first
     * [blockSize] bytes pushed to the stream.
     */
    @ScriptApi
    fun getDecryptStream(): StreamCipher = DecryptStreamCipher(
        builder.with(mode = Cipher.DECRYPT_MODE), readIVFromStream = requiresIV
    )

    /**
     * Returns an object suitable for decrypting streamed data.
     *
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of the block size of the algorithm.
     */
    @ScriptApi
    fun getDecryptStreamWithIV(iv: AnyBinary): StreamCipher = DecryptStreamCipher(
        builder.with(mode = Cipher.DECRYPT_MODE, iv = iv.asAnyBinaryToByteArray()), readIVFromStream = false
    )

    /**
     * Construct and return a new native decryption-mode [Cipher], initialized with key and algorithm
     * of this [CipherApi] object.
     *
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of [getBlockSize]. If not given, one will be generated if needed.
     */
    @ScriptApi
    fun getDecryptCipher(iv: AnyBinary? = null): Cipher =
        builder.with(mode = Cipher.DECRYPT_MODE, iv = iv?.asAnyBinaryToByteArray()).build()

    /**
     * Construct and return a new native encryption-mode [Cipher], initialized with key and algorithm
     * of this [CipherApi] object.
     *
     * @param iv The initialization vector. Can be a byte array, integer array,
     *        hex string, or base64 string (@see Value.asBinaryArg). Must be
     *        multiple of [getBlockSize]. If not given, one will be generated if needed.
     */
    @ScriptApi
    fun getEncryptCipher(iv: AnyBinary? = null): Cipher =
        builder.with(mode = Cipher.ENCRYPT_MODE, iv = iv?.asAnyBinaryToByteArray()).build()

    private fun setRsaKey(key: String) {
        builder = builder.with(key = rsaKeyFromString(key))
    }

    private fun setDesKey(key: ByteArray) {
        builder = builder.with(key = SecretKeySpec(key, "DESede"))
    }

    private fun setAesKey(key: ByteArray) {
        builder = builder.with(key = SecretKeySpec(key, "AES"))
    }
}

interface StreamCipher {
    /**
     * The block size (in bytes) of the algorithm.
     * (This is part of the interface even though it is commented out below)
     */
    //@ScriptApi
    //val blockSize: Int

    /**
     * Push a part of the data into the stream for encryption or decryption.
     *
     * Data does not need to be pushed in block-sized chunks, but the total
     * amount of data pushed must be a multiple of the block size.
     *
     * If this stream was created with [CipherApi.getDecryptStream], and the
     * algorithm requires an IV, the IV must be the first [blockSize] bytes
     * pushed to the stream.
     */
    @ScriptApi
    fun push(part: AnyBinary)

    /**
     * Finish the encryption or decryption process and return the result. The
     * stream is reset and can be used again.
     */
    @ScriptApi
    fun finish(): UnsignedByteArray
}

private class EncryptStreamCipher(
    private val cipher: Cipher, private val writeIVToStream: Boolean
) : StreamCipher {
    private var input: ByteArray = ByteArray(0)

    @ScriptApi
    @JvmField
    val blockSize = cipher.blockSize

    @ScriptApi
    override fun push(part: AnyBinary) {
        val asBytes = part.asAnyBinaryToByteArray()
        this.input += asBytes
    }

    @ScriptApi
    override fun finish(): UnsignedByteArray {
        val result = if (writeIVToStream) {
            cipher.getIV() + cipher.doFinal(input)
        } else {
            cipher.doFinal(input)
        }
        reset()
        return result.toUnsignedByteArray()
    }

    private fun reset() {
        input = ByteArray(0)
    }
}

private class DecryptStreamCipher(
    private var builder: CipherBuilder,
    private val readIVFromStream: Boolean,
) : StreamCipher {
    private var ivFromStream: ByteArray? = null
    private var input: ByteArray = ByteArray(0)

    @ScriptApi
    @JvmField
    val blockSize = builder.blockSize

    @ScriptApi
    override fun push(part: AnyBinary) {
        var asBytes = part.asAnyBinaryToByteArray()

        if (readIVFromStream && (ivFromStream == null || ivFromStream!!.size < blockSize)) {
            val needed = blockSize - (ivFromStream?.size ?: 0)
            if (asBytes.size < needed) {
                ivFromStream = (ivFromStream ?: ByteArray(0)) + asBytes
                return
            }

            ivFromStream = (ivFromStream ?: ByteArray(0)) + asBytes.slice(0 until needed).toByteArray()
            asBytes = asBytes.slice(needed until asBytes.size).toByteArray()
        }

        this.input += asBytes
    }

    @ScriptApi
    override fun finish(): UnsignedByteArray {
        val result = builder.with(iv = ivFromStream).build().doFinal(input)
        reset()
        return result.toUnsignedByteArray()
    }

    private fun reset() {
        ivFromStream = null
        input = ByteArray(0)
    }
}

private class CipherBuilder(
    private val algo: String,
    private var mode: Int = -1,
    private var key: Key? = null,
    private var iv: ByteArray? = null,

    ) {
    val blockSize: Int = Cipher.getInstance(algo).blockSize
    val requiresIV = needIV(algo)

    fun with(mode: Int? = null, key: Key? = null, iv: ByteArray? = null) = CipherBuilder(
        algo = algo, mode = mode ?: this.mode, key = key ?: this.key, iv = iv ?: this.iv
    )

    fun withRandomIV(mode: Int? = null, key: Key? = null): CipherBuilder {
        val iv = ByteArray(blockSize)
        SecureRandom.getInstanceStrong().nextBytes(iv)
        return with(mode, key, iv)
    }

    fun build(): Cipher {
        if (mode == -1) {
            throw IllegalStateException("no mode set")
        }
        if (key == null) {
            throw IllegalStateException("no key set")
        }
        if (requiresIV && iv == null) {
            throw IllegalStateException("no IV set")
        }

        val cipher = Cipher.getInstance(algo)
        if (requiresIV && iv != null) {
            val ivSpec = IvParameterSpec(iv)
            cipher.init(mode, key, ivSpec)
        } else {
            cipher.init(mode, key)
        }

        return cipher
    }

    private fun needIV(algo: String): Boolean {
        return algo.startsWith("AES") || algo.startsWith("DES")
    }
}


/**
 *
 *
 * Signature API
 *
 *
 */

class SignatureApi(
    private val algo: String
) {
    val sig = Signature.getInstance(algo)

    /**
     * PKCS#8 PEM, X.509 PEM, or path to a PEM file path (*.pem, *.key, *.cert)
     */
    @ScriptApi
    fun setKey(key: String) = initSignature(sig, key, forSigning = true)

    @ScriptApi
    fun sign(value: AnyBinary): UnsignedByteArray {
        sig.update(value.asAnyBinaryToByteArray())
        return sig.sign().toUnsignedByteArray()
    }

    @ScriptApi
    fun getStream(): StreamSigning = StreamSigning(sig)
}

class VerifySignatureApi(
    private val algo: String
) {
    val sig = Signature.getInstance(algo)

    /**
     * PKCS#8 PEM, X.509 PEM, or path to a PEM file path (*.pem, *.key, *.cert)
     */
    @ScriptApi
    fun setKey(key: String) = initSignature(sig, key, forSigning = false)

    @ScriptApi
    fun verify(value: AnyBinary, signature: AnyBinary): Boolean {
        sig.update(value.asAnyBinaryToByteArray())
        return sig.verify(signature.asAnyBinaryToByteArray())
    }

    @ScriptApi
    fun getStream(): StreamVerify = StreamVerify(sig)
}

class StreamSigning(
    private val sig: Signature,
) {
    @ScriptApi
    fun push(part: AnyBinary) = sig.update(part.asAnyBinaryToByteArray())

    @ScriptApi
    fun sign(): UnsignedByteArray = sig.sign().toUnsignedByteArray()
}

class StreamVerify(
    private val sig: Signature,
) {
    @ScriptApi
    fun push(part: AnyBinary) = sig.update(part.asAnyBinaryToByteArray())

    @ScriptApi
    fun verify(signature: AnyBinary): Boolean = sig.verify(signature.asAnyBinaryToByteArray())
}

private fun initSignature(sig: Signature, key: String, forSigning: Boolean) {
    val algo = sig.algorithm

    val k = if (algo.contains("RSA")) {
        rsaKeyFromString(key)
    } else if (algo.contains("ECDSA")) {
        ecdsaKeyFromString(key)
    } else if (algo.contains("DSA")) {
        dsaKeyFromString(key)
    } else {
        throw IllegalArgumentException("Signature interface only supports RSA, DSA, or ECDSA algorithms")
    }

    if (forSigning) {
        if (k !is PrivateKey) {
            throw IllegalArgumentException("Signing requires a private key")
        }
        sig.initSign(k)
    } else {
        if (k !is PublicKey) {
            throw IllegalArgumentException("Verification requires a public key")
        }
        sig.initVerify(k)
    }
}

/**
 *
 *
 * HMAC
 *
 *
 */

class HmacApi(
    private val algo: String
) {
    private val mac = Mac.getInstance(algo)

    /**
     * The length of the digest produced by this HMAC algorithm
     */
    @ScriptApi
    @JvmField
    val digestSize = mac.macLength

    /**
     * Set the key material to use for HMAC.
     *
     * @param key can be a byte array, integer array, hex string, or base64
     *        string (@see [AnyBinary]).
     */
    @ScriptApi
    fun setKey(key: AnyBinary) {
        val k = SecretKeySpec(key.asAnyBinaryToByteArray(), algo)
        mac.init(k)
    }

    /**
     * Compute the HMAC digest of the given value.
     *
     * @param value can be a byte array, integer array, hex string, or base64
     *        string (@see [AnyBinary]).
     */
    @ScriptApi
    fun digest(value: AnyBinary): UnsignedByteArray = mac.doFinal(value.asAnyBinaryToByteArray()).toUnsignedByteArray()

    /**
     * Return an object suitable for computing HMAC digests of streamed data.
     */
    @ScriptApi
    fun getStream(): StreamHmac = StreamHmac(mac.clone() as Mac)
}

class StreamHmac(
    private val mac: Mac
) {
    /**
     * The length of the digest produced by this HMAC algorithm
     */
    @ScriptApi
    @JvmField
    val digestSize = mac.macLength

    /**
     * Push a part of the data into the stream for HMAC computation.
     *
     * @param part can be a byte array, integer array, hex string, or base64
     *        string (@see [AnyBinary]).
     */
    @ScriptApi
    fun push(part: AnyBinary) = mac.update(part.asAnyBinaryToByteArray())

    /**
     * Finish the HMAC computation and return the result. The stream is reset
     */
    @ScriptApi
    fun finish(): UnsignedByteArray = mac.doFinal().toUnsignedByteArray()
}

/**
 *
 *
 * Utilities
 *
 *
 */

private fun rsaKeyFromString(key: String) = readPem(
    key, readPriv = ::rsaKeyFromPrivatePem, readPub = ::rsaKeyFromPublicPem, readCert = ::keyFromCertificatePem
)

private fun dsaKeyFromString(key: String) = readPem(
    key, readPriv = ::dsaKeyFromPrivatePem, readPub = ::dsaKeyFromPublicPem, readCert = ::keyFromCertificatePem
)

private fun ecdsaKeyFromString(key: String) = readPem(
    key, readPriv = ::ecdsaKeyFromPrivatePem, readPub = ::ecdsaKeyFromPublicPem, readCert = ::keyFromCertificatePem
)

private fun readPem(key: String, readPriv: (String) -> Key, readPub: (String) -> Key, readCert: (String) -> Key): Key {
    if (key.endsWith(".pem") || key.endsWith(".key") || key.endsWith(".cert")) {
        return readPem(
            Files.readString(Paths.get(key)), readPriv, readPub, readCert
        )
    }
    return when {
        key.contains("PRIVATE KEY") -> readPriv(key)
        key.contains("PUBLIC KEY") -> readPub(key)
        key.contains("CERTIFICATE") -> readCert(key)
        else -> throw IllegalArgumentException("${key.substring(0, 5)}... does not appear to be a PEM")
    }
}

private fun unPem(pem: String): ByteArray {
    val trimmed = pem.trim()

    // Remove header and footer
    val start = trimmed.indexOf('\n') + 1
    val end = trimmed.lastIndexOf('\n')

    val b64 = trimmed.substring(start, end)
        .replace("\r\n", "\n")
        .split("\n")
        .map { it.trim() }
        .joinToString("")
    return Base64.getDecoder().decode(b64)
}

private fun keyFromCertificatePem(pem: String): Key {
    val factory = CertificateFactory.getInstance("X.509")
    val cert = factory.generateCertificate(ByteArrayInputStream(pem.toByteArray()))
    return cert.publicKey
}

private fun rsaKeyFromPrivatePem(pem: String): Key {
    val spec = PKCS8EncodedKeySpec(unPem(pem))
    return KeyFactory.getInstance("RSA").generatePrivate(spec)
}

private fun rsaKeyFromPublicPem(pem: String): Key {
    val spec = X509EncodedKeySpec(unPem(pem))
    return KeyFactory.getInstance("RSA").generatePublic(spec)
}

private fun dsaKeyFromPrivatePem(pem: String): Key {
    val spec = PKCS8EncodedKeySpec(unPem(pem))
    return KeyFactory.getInstance("DSA").generatePrivate(spec)
}

private fun dsaKeyFromPublicPem(pem: String): Key {
    val spec = X509EncodedKeySpec(unPem(pem))
    return KeyFactory.getInstance("DSA").generatePublic(spec)
}

private fun ecdsaKeyFromPrivatePem(pem: String): Key {
    val spec = PKCS8EncodedKeySpec(unPem(pem))
    return KeyFactory.getInstance("EC").generatePrivate(spec)
}

private fun ecdsaKeyFromPublicPem(pem: String): Key {
    val spec = X509EncodedKeySpec(unPem(pem))
    return KeyFactory.getInstance("EC").generatePublic(spec)
}