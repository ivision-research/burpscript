package com.carvesystems.burpscript

import com.carvesystems.burpscript.internal.testing.matchers.value.shouldBeTrue
import com.carvesystems.burpscript.interop.*
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value

class ScriptCryptoApiTest : StringSpec() {
    private lateinit var ctx: Context

    companion object {
        const val TEST_FUNCTION = "test_func"
    }

    private fun exec(script: String, vararg args: Any): Value {
        val src = Source.newBuilder("python", script, "test-script.py").build()
        val parsed = ctx.eval(src)
        parsed.hasMember(TEST_FUNCTION).shouldBeTrue()
        val value = parsed.getMember(TEST_FUNCTION)
        return value.execute(*args)
    }

    init {

        beforeSpec {
            ctx = PythonContextBuilder()
                .withBindings("helpers" to ScriptHelpers())
                .build()
        }

        "RSA cipher API encrypt" {
            val plaintext = "encrypted rsa message using public key".toByteArray()

            val makeScript = { key: String ->
                """
                |key = '''$key'''
                |
                |def $TEST_FUNCTION(data, enc): 
                |   cryptoHelper = helpers.getCryptoHelper()
                |   cipher = cryptoHelper.newCipher("RSA/ECB/PKCS1Padding")
                |   cipher.setKey(key)
                |   if enc:
                |       return cipher.encrypt(data)
                |   return cipher.decrypt(data)
                """.trimMargin()
            }

            val run = { key: String, data: kotlin.ByteArray, enc: Boolean ->
                val script = makeScript(key)
                val ret = exec(script, data, enc)
                ret.toByteArray()
            }

            var ciphertext = run(RSA_PUB, plaintext, true)
            var decrypted = run(RSA_PRIV, ciphertext, false)
            decrypted.size.shouldBe(plaintext.size)
            decrypted.shouldBe(plaintext)

            ciphertext = run(RSA_CERT, plaintext, true)
            decrypted = run(RSA_PRIV, ciphertext, false)
            decrypted.size.shouldBe(plaintext.size)
            decrypted.shouldBe(plaintext)
        }

        "RSA cipher API decrypt" {
            val pubExpected = "encrypted rsa message using public key".toByteArray()
            val encWithPub =
                "Utbv6YSoEPRHsIzSjv0IQ6Gam4r9ADcJESSX4hnPWKbyDQ9GaKdmmbeLi3HR6ISKaM9BuaNL2XWmIlQezd0lfu9XH6WBs03fl5+LWLmHQqfP4mhTDbIYvZZfRRA7NRjQCjsQJkI6X32d71b7yTRY1FTCS+U+p92zt5lsmqvmCz3DTh2gYpPDa0Q2zg59MiGltvu4JYK1gpiflnbl6EOGgatmpRPhPDWPB9vw9YY7QAjMo4xVUDgBcciGHlYspihJFwUbaBoIqVrkjZG4nbbOPRvjgEu8GZEk0bOmrUkToTibRVdc9Hfu+NHscfGF2vLWl4I3QIJXWzSXgVsULaCS2A=="

            val makeScript = { key: String ->
                """
                |key = '''$key'''
                |
                |def $TEST_FUNCTION(data): 
                |   cryptoHelper = helpers.getCryptoHelper()
                |   cipher = cryptoHelper.newCipher("RSA/ECB/PKCS1Padding")
                |   cipher.setKey(key)
                |   return cipher.decrypt(data)
                """.trimMargin()
            }

            val run = { key: String, data: String ->
                val script = makeScript(key)
                val ret = exec(script, data)
                ret.toByteArray()
            }

            val privRes = run(RSA_PRIV, encWithPub)
            privRes.size.shouldBe(pubExpected.size)
            privRes.shouldBe(pubExpected)
        }

        "setKey trims whitespace from PEM" {
            val containsNewline = """
                KEY = '''
                -----BEGIN PUBLIC KEY-----
                MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmBmqp/OkVQqFD5rvGKKL
                MVAw73eO7+72u/7tQt+AfwSMlqtL+zBvGPx45E6Ia0WoYsDcnS8+usV0C8uc0RUx
                8G8GejF11Z9EKOH/TKklr3g1uYoVzPkNcq4gDMBug+ddqbu0rsNG/Nh3dNsS2NOU
                CeNmBkd09g1CVy+FNSvsbLvlzeMpQgg3BEYI2FqEPiK/yI71WIT3gWE6H9qG3XTh
                H8Ggblm2nX5IUDwgiMk8QHccQyxGxKZxS6xdzb2uUi597ghBmlhuB73SjMifU5gY
                +U7VWyx1YU2GIbbGjI2qp5SXq6eFJ6CfmrG2485D/rURHi0LV8EtsJ3afitk/WwO
                2wIDAQAB
                -----END PUBLIC KEY-----
                '''
                
                def $TEST_FUNCTION():
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("RSA/ECB/PKCS1Padding")
                    cipher.setKey(KEY)
            """.trimIndent()
            exec(containsNewline)

            val containsWhitespace = """
                KEY = '''
                    -----BEGIN PUBLIC KEY-----
                    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmBmqp/OkVQqFD5rvGKKL           
                    MVAw73eO7+72u/7tQt+AfwSMlqtL+zBvGPx45E6Ia0WoYsDcnS8+usV0C8uc0RUx           
                    8G8GejF11Z9EKOH/TKklr3g1uYoVzPkNcq4gDMBug+ddqbu0rsNG/Nh3dNsS2NOU           
                    CeNmBkd09g1CVy+FNSvsbLvlzeMpQgg3BEYI2FqEPiK/yI71WIT3gWE6H9qG3XTh           
                    H8Ggblm2nX5IUDwgiMk8QHccQyxGxKZxS6xdzb2uUi597ghBmlhuB73SjMifU5gY           
                    +U7VWyx1YU2GIbbGjI2qp5SXq6eFJ6CfmrG2485D/rURHi0LV8EtsJ3afitk/WwO           
                    2wIDAQAB
                -----END PUBLIC KEY-----
                '''
                
                def $TEST_FUNCTION():
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("RSA/ECB/PKCS1Padding")
                    cipher.setKey(KEY)
            """.trimIndent()
            exec(containsWhitespace)
        }

        "AES 128 cipher API accepts hex and base64 strings" {
            val enc = "D19slxzpj25Nz2EQHRoyOTBzx6FGbbaix7HGAvLgA8mHL3FthThZZ/Jr+vNJmhJY"
            val dec = "testencryptionshouldbelongerthananaesblock\n".toByteArray()

            val makeScript = { key: String ->
                """
                |key = $key
                |iv = key
                |
                |def $TEST_FUNCTION(data): 
                |   cryptoHelper = helpers.getCryptoHelper()
                |   cipher = cryptoHelper.newCipher("AES/CBC/PKCS5Padding")
                |   cipher.setKey(key)
                |   return cipher.decryptWithIV(data, iv)
            """.trimMargin()
            }

            val run = { key: String ->
                val script = makeScript(key)
                val ret = exec(script, enc)
                ret.toByteArray()
            }

            var value = run("\"59167fee6b2f5a2c170f8a165e32313f\"")
            value.size.shouldBe(dec.size)
            value.shouldBe(dec)

            value = run("helpers.unhex(\"59167fee6b2f5a2c170f8a165e32313f\")")
            value.size.shouldBe(dec.size)
            value.shouldBe(dec)

            value = run("helpers.b64(helpers.unhex(\"59167fee6b2f5a2c170f8a165e32313f\"))")
            value.size.shouldBe(dec.size)
            value.shouldBe(dec)
        }

        "AES 128 stream with IV" {
            val enc = "D19slxzpj25Nz2EQHRoyOTBzx6FGbbaix7HGAvLgA8mHL3FthThZZ/Jr+vNJmhJY"
            val dec = "testencryptionshouldbelongerthananaesblock\n"
            val key = "59167fee6b2f5a2c170f8a165e32313f"
            val iv = key

            val encrypt = """
                def $TEST_FUNCTION(key, iv, data):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
                    
                    mid = len(data) // 2
    
                    enc_stream = cipher.getEncryptStreamWithIV(iv)
                    enc_stream.push(data[:mid])
                    enc_stream.push(data[mid:])
                    return enc_stream.finish()
            """.trimIndent()
            var value = exec(encrypt, key, iv, dec.toByteArray()).toByteArray()
            value.shouldBe(enc.decodeAsByteArray())

            val decrypt = """
                def $TEST_FUNCTION(key, iv, data):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
                    
                    mid = len(data) // 2
    
                    dec_stream = cipher.getDecryptStreamWithIV(iv)
                    dec_stream.push(data[:mid])
                    dec_stream.push(data[mid:])
                    return dec_stream.finish()
            """.trimIndent()
            value = exec(decrypt, key, key, enc).toByteArray()
            value.shouldBe(dec.toByteArray())
        }

        "AES 128 stream with random IV round trip" {
            val dec = "testencryptionshouldbelongerthananaesblock\n"
            val key = "59167fee6b2f5a2c170f8a165e32313f"

            val roundTrip = """
                def $TEST_FUNCTION(key, data):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
    
                    enc_stream = cipher.getEncryptStream()
                    enc_stream.push(data)
                    enc_data = enc_stream.finish()
    
                    dec_stream = cipher.getDecryptStream()
                    dec_stream.push(enc_data)
                    return dec_stream.finish()
            """.trimIndent()
            val value = exec(roundTrip, key, dec.toByteArray()).toByteArray()
            value.shouldBe(dec.toByteArray())
        }

        "stream decrypt reads IV from first block" {
            val dec = "testencryptionshouldbelongerthananaesblock\n"
            val key = "59167fee6b2f5a2c170f8a165e32313f"
            val iv = key

            val encrypt = """
                def $TEST_FUNCTION(key, iv, data):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
                    
                    enc_stream = cipher.getEncryptStreamWithIV(iv)
                    enc_stream.push(data)
                    return enc_stream.finish()
            """.trimIndent()
            val enc = exec(encrypt, key, iv, dec.toByteArray()).toByteArray()

            val decrypt = """
                def $TEST_FUNCTION(key, iv, data):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
                    
                    dec_stream = cipher.getDecryptStream()
                    
                    mid = len(iv) // 2
                    dec_stream.push(iv[:mid])
                    dec_stream.push(iv[mid:])
                    dec_stream.push(data)
                    
                    return dec_stream.finish()
            """.trimIndent()
            val value = exec(decrypt, key, iv, enc).toByteArray()
            value.shouldBe(dec.toByteArray())
        }

        "encrypt streams can be reused" {
            val msg1 = "testencryptionshouldbelongerthananaesblock\n"
            val msg2 = "anothermessage"
            val key = "59167fee6b2f5a2c170f8a165e32313f"
            val iv = "e9f8d729c19d3b8f4aef745c8a9bffff"

            val reuseEncStream = """
                def $TEST_FUNCTION(key, iv, data1, data2):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
    
                    enc_stream = cipher.getEncryptStreamWithIV(iv)
    
                    enc_stream.push(data1)
                    enc_data1 = enc_stream.finish()
    
                    enc_stream.push(data2)
                    enc_data2 = enc_stream.finish()
    
                    dec_data1 = cipher.decryptWithIV(enc_data1, iv)
                    dec_data2 = cipher.decryptWithIV(enc_data2, iv)
                    
                    return dec_data1, dec_data2
            """.trimIndent()
            val value = exec(reuseEncStream, key, iv, msg1.toByteArray(), msg2.toByteArray())
            value.getArrayElement(0).toByteArray().shouldBe(msg1.toByteArray())
            value.getArrayElement(1).toByteArray().shouldBe(msg2.toByteArray())
        }

        "decrypt streams can be reused" {
            val msg1 = "testencryptionshouldbelongerthananaesblock\n"
            val msg2 = "anothermessage"
            val key = "59167fee6b2f5a2c170f8a165e32313f"
            val iv1 = "e9f8d729c19d3b8f4aef745c8a9bffff"
            val iv2 = "bc7f9823e4d5a8c4f6892ad7b3e52fea"

            val reuseDecStream = """
                def $TEST_FUNCTION(key, iv1, iv2, data1, data2):
                    helper = helpers.getCryptoHelper()
                    cipher = helper.newCipher("AES/CBC/PKCS5Padding")
                    cipher.setKey(key)
    
                    enc_data1 = cipher.encryptWithIV(data1, iv1)
                    enc_data2 = cipher.encryptWithIV(data2, iv2)
                    
                    dec_stream = cipher.getDecryptStream()
    
                    dec_stream.push(iv1)
                    dec_stream.push(enc_data1)
                    dec_data1 = dec_stream.finish()
    
                    dec_stream.push(iv2)
                    dec_stream.push(enc_data2)
                    dec_data2 = dec_stream.finish()
    
                    return dec_data1, dec_data2
            """.trimIndent()
            val value = exec(reuseDecStream, key, iv1, iv2, msg1.toByteArray(), msg2.toByteArray())
            value.getArrayElement(0).toByteArray().shouldBe(msg1.toByteArray())
            value.getArrayElement(1).toByteArray().shouldBe(msg2.toByteArray())
        }
    }
}

class ScriptSignatureApiTest : StringSpec() {
    private lateinit var ctx: Context

    companion object {
        const val TEST_FUNCTION = "test_func"
    }

    private fun exec(script: String, vararg args: Any): Value {
        val src = Source.newBuilder("python", script, "test-script.py").build()
        val parsed = ctx.eval(src)
        parsed.hasMember(TEST_FUNCTION).shouldBeTrue()
        val value = parsed.getMember(TEST_FUNCTION)
        return value.execute(*args)
    }

    init {
        beforeSpec {
            ctx = PythonContextBuilder()
                .withBindings("helpers" to ScriptHelpers())
                .build()
        }

        "sign & verify with rsa" {
            val sign = """
                def $TEST_FUNCTION(key, data):
                    helper = helpers.getCryptoHelper()
                    sign = helper.newSignature("SHA256withRSA")
                    sign.setKey(key)
                    return sign.sign(data)
            """.trimIndent()

            val verify = """
                def $TEST_FUNCTION(key, data, sig):
                    helper = helpers.getCryptoHelper()
                    verify = helper.newVerifySignature("SHA256withRSA")
                    verify.setKey(key)
                    return verify.verify(data, sig)
            """.trimIndent()

            val data = "this is a test message".toByteArray()
            val expectedSig = "I8cCA9lGLkheG9/xeRa55F/+1/hGFkxpxe91WB9Eu7H0A/cnvnQggIhmR+0uRmNp4754aFlvEJ6kkTTyzZ+am46PZdNooHeJ+uVRzE8RSx+z84QhEfW6gZB6XlYRz3gg4sQxc4QIQBTzonjEGXyzbcI6UlJLHPz66qbxFRu6iIrDDCy1hH4BfUb/lstcEbXIaEAXr2zSbz1sctza2tqSmUYrPOVJpeiLMNdRGfO6guYN/Mwnl39z2w+7leG2CzGDTZ2NttbFICL6LE7pUw8XJjJxNZi1MnYZ8+zot4+MFb+ybY6OOH1L/mGX/3HUGZSnqYfiHzC+lP++juaZuvy+6w==".decodeBase64()

            val sig = exec(sign, RSA_PRIV, data.toUnsignedByteArray()).toByteArray()
            sig.shouldBe(expectedSig)

            val verifiedWithPub = exec(verify, RSA_PUB, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verifiedWithPub.shouldBeTrue()

            val verifiedWithCert = exec(verify, RSA_CERT, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verifiedWithCert.shouldBeTrue()
        }

        "sign & verify with dsa" {
            val sign = """
                def $TEST_FUNCTION(key, data):
                    helper = helpers.getCryptoHelper()
                    sign = helper.newSignature("SHA256withDSA")
                    sign.setKey(key)
                    return sign.sign(data)
            """.trimIndent()

            val verify = """
                def $TEST_FUNCTION(key, data, sig):
                    helper = helpers.getCryptoHelper()
                    verify = helper.newVerifySignature("SHA256withDSA")
                    verify.setKey(key)
                    return verify.verify(data, sig)
            """.trimIndent()

            val data = "this is a test message".toByteArray()

            val sig = exec(sign, DSA_PRIV, data.toUnsignedByteArray()).toByteArray()
            val verified = exec(verify, DSA_PUB, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verified.shouldBeTrue()
            val verifiedWithCert = exec(verify, DSA_CERT, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verifiedWithCert.shouldBeTrue()
        }

        "sign & verify with ecdsa" {
            val sign = """
                def $TEST_FUNCTION(key, data):
                    helper = helpers.getCryptoHelper()
                    sign = helper.newSignature("SHA256withECDSA")
                    sign.setKey(key)
                    return sign.sign(data)
            """.trimIndent()

            val verify = """
                def $TEST_FUNCTION(key, data, sig):
                    helper = helpers.getCryptoHelper()
                    verify = helper.newVerifySignature("SHA256withECDSA")
                    verify.setKey(key)
                    return verify.verify(data, sig)
            """.trimIndent()

            val data = "this is a test message".toByteArray()

            val sig = exec(sign, ECDSA_PRIV, data.toUnsignedByteArray()).toByteArray()
            val verified = exec(verify, ECDSA_PUB, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verified.shouldBeTrue()
            val verifiedWithCert = exec(verify, ECDSA_CERT, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verifiedWithCert.shouldBeTrue()
        }

        "stream sign & verify" {
            val signStream = """
                def $TEST_FUNCTION(key, data):
                    helper = helpers.getCryptoHelper()
                    sign = helper.newSignature("SHA256withRSA")
                    sign.setKey(key)

                    sign_stream = sign.getStream()

                    mid = len(data) // 2
                    sign_stream.push(data[:mid])
                    sign_stream.push(data[mid:])

                    return sign_stream.sign()
            """.trimIndent()

            val verifyStream = """
                def $TEST_FUNCTION(key, data, sig):
                    helper = helpers.getCryptoHelper()
                    verify = helper.newVerifySignature("SHA256withRSA")
                    verify.setKey(key)

                    verify_stream = verify.getStream()

                    mid = len(data) // 2
                    verify_stream.push(data[:mid])
                    verify_stream.push(data[mid:])

                    return verify_stream.verify(sig)
           """.trimIndent()

            val data = "this is a test message this is a test message this is a test message".toByteArray()
            val sig = exec(signStream, RSA_PRIV, data.toUnsignedByteArray()).toByteArray()
            val verified = exec(verifyStream, RSA_PUB, data.toUnsignedByteArray(), sig.toUnsignedByteArray())
            verified.shouldBeTrue()
        }

        "streams can be reused" {
            val reuse = """
                def $TEST_FUNCTION(pub_key, priv_key):
                    helper = helpers.getCryptoHelper()
                    sign = helper.newSignature("SHA256withRSA")
                    sign.setKey(priv_key)
                    sign_stream = sign.getStream()
    
                    msg1 = "this is a test message this is a test message this is a test message".encode()
                    msg2 = "this is another test message this is another test message this is another test message".encode()

                    sign_stream.push(msg1)
                    sig1 = sign_stream.sign()

                    sign_stream.push(msg2)
                    sig2 = sign_stream.sign()

                    verify = helper.newVerifySignature("SHA256withRSA")
                    verify.setKey(pub_key)
                    verify_stream = verify.getStream()

                    verify_stream.push(msg1)
                    assert verify_stream.verify(sig1)

                    verify_stream.push(msg2)
                    assert verify_stream.verify(sig2)
            """.trimIndent()

            exec(reuse, RSA_PUB, RSA_PRIV)
        }
    }
}

class ScriptHmacTest : StringSpec() {
    private lateinit var ctx: Context

    companion object {
        const val TEST_FUNCTION = "test_func"
    }

    private fun exec(script: String, vararg args: Any): Value {
        val src = Source.newBuilder("python", script, "test-script.py").build()
        val parsed = ctx.eval(src)
        parsed.hasMember(TEST_FUNCTION).shouldBeTrue()
        val value = parsed.getMember(TEST_FUNCTION)
        return value.execute(*args)
    }

    init {
        beforeSpec {
            ctx = PythonContextBuilder()
                .withBindings("helpers" to ScriptHelpers())
                .build()
        }

        "hmac sha256" {
            val hmac = """
                def $TEST_FUNCTION(key, data):
                    helper = helpers.getCryptoHelper()
                    hmac = helper.newHmac("HmacSHA256")
                    hmac.setKey(key)
                    return hmac.digest(data)
            """.trimIndent()

            val key = "59167fee6b2f5a2c170f8a165e32313f"
            val data = "this is a test message".toByteArray()
            val expectedDigest = "IbVgmMoa1AgjIP0bCawLdO/RrjlmTsL7k4vYx1NbUjs=".decodeBase64()

            val result = exec(hmac, key, data.toUnsignedByteArray()).toByteArray()
            result.shouldBe(expectedDigest)
        }

        "hmac streams can be reused" {
            val reuse = """
                def $TEST_FUNCTION(key):
                    helper = helpers.getCryptoHelper()
                    hmac = helper.newHmac("HmacSHA256")
                    hmac.setKey(key)
                    hmac_stream = hmac.getStream()
    
                    msg1 = "this is a test message this is a test message this is a test message".encode()
                    msg2 = "this is another test message this is another test message this is another test message".encode()

                    hmac_stream.push(msg1)
                    digest1 = hmac_stream.finish()

                    hmac_stream.push(msg2)
                    digest2 = hmac_stream.finish()

                    return digest1, digest2
            """.trimIndent()

            val key = "59167fee6b2f5a2c170f8a165e32313f"
            val expectedDigest1 = "/DiDFxJ5pl3cQmLOoE06A/uCuA75JbQqFPyAlTK4ULI=".decodeBase64()
            val expectedDigest2 = "yp5B/Xbgn44jWLIP4LFyG+CC1KbeVnEti4TMQbZOF9I=".decodeBase64()

            val digests = exec(reuse, key)
            val digest1 = digests.getArrayElement(0).toByteArray()
            val digest2 = digests.getArrayElement(1).toByteArray()
            digest1.shouldBe(expectedDigest1)
            digest2.shouldBe(expectedDigest2)
        }
    }
}

val RSA_CERT = """
    -----BEGIN CERTIFICATE-----
    MIIDZTCCAk2gAwIBAgIUVR/hpDCCABNy71NFQSBaUOKGWt4wDQYJKoZIhvcNAQEL
    BQAwQjELMAkGA1UEBhMCVVMxFTATBgNVBAcMDERlZmF1bHQgQ2l0eTEcMBoGA1UE
    CgwTRGVmYXVsdCBDb21wYW55IEx0ZDAeFw0yNDA0MjIxOTU2NTdaFw0yNDA1MjIx
    OTU2NTdaMEIxCzAJBgNVBAYTAlVTMRUwEwYDVQQHDAxEZWZhdWx0IENpdHkxHDAa
    BgNVBAoME0RlZmF1bHQgQ29tcGFueSBMdGQwggEiMA0GCSqGSIb3DQEBAQUAA4IB
    DwAwggEKAoIBAQCYGaqn86RVCoUPmu8YoosxUDDvd47v7va7/u1C34B/BIyWq0v7
    MG8Y/HjkTohrRahiwNydLz66xXQLy5zRFTHwbwZ6MXXVn0Qo4f9MqSWveDW5ihXM
    +Q1yriAMwG6D512pu7Suw0b82Hd02xLY05QJ42YGR3T2DUJXL4U1K+xsu+XN4ylC
    CDcERgjYWoQ+Ir/IjvVYhPeBYTof2obddOEfwaBuWbadfkhQPCCIyTxAdxxDLEbE
    pnFLrF3Nva5SLn3uCEGaWG4HvdKMyJ9TmBj5TtVbLHVhTYYhtsaMjaqnlJerp4Un
    oJ+asbbjzkP+tREeLQtXwS2wndp+K2T9bA7bAgMBAAGjUzBRMB0GA1UdDgQWBBTa
    44Y0RweCqi5Kd0jSCqg8LaCTbTAfBgNVHSMEGDAWgBTa44Y0RweCqi5Kd0jSCqg8
    LaCTbTAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCWpf83p2B4
    L7zGw9mQk8B40GZScebeinHs5uCVKz9vXyTBWVX6QUUfnEqnnI6UKfjimOXyo6d2
    JHuVGY2BTUbt34DB8CjFqA6LRArz5mMZ9njuoJA86RSJ8R7Fz8RVmubsETF+kPJn
    TRqtx++l7+CZur+77ie04oN0hoOEoMmSJTsEdbmG/vZHDKjGYSUjWjVvyy7nR4gI
    rOSrK2Y7LCYmxypOym5nJ0P1SsG36udmK1Ms3lXsf0tRiRmf18izOo/2ZJewOYTy
    QwleJYPNcK7RXKzolhLTvU52qKNi5LyizIAtxIjFQmezitXDQH5sdYOI+MEmU68R
    1QOwxKKFvXOS
    -----END CERTIFICATE-----
""".trimIndent()

val RSA_PUB = """
    -----BEGIN PUBLIC KEY-----
    MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmBmqp/OkVQqFD5rvGKKL
    MVAw73eO7+72u/7tQt+AfwSMlqtL+zBvGPx45E6Ia0WoYsDcnS8+usV0C8uc0RUx
    8G8GejF11Z9EKOH/TKklr3g1uYoVzPkNcq4gDMBug+ddqbu0rsNG/Nh3dNsS2NOU
    CeNmBkd09g1CVy+FNSvsbLvlzeMpQgg3BEYI2FqEPiK/yI71WIT3gWE6H9qG3XTh
    H8Ggblm2nX5IUDwgiMk8QHccQyxGxKZxS6xdzb2uUi597ghBmlhuB73SjMifU5gY
    +U7VWyx1YU2GIbbGjI2qp5SXq6eFJ6CfmrG2485D/rURHi0LV8EtsJ3afitk/WwO
    2wIDAQAB
    -----END PUBLIC KEY-----
""".trimIndent()

val RSA_PRIV = """
    -----BEGIN PRIVATE KEY-----
    MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCYGaqn86RVCoUP
    mu8YoosxUDDvd47v7va7/u1C34B/BIyWq0v7MG8Y/HjkTohrRahiwNydLz66xXQL
    y5zRFTHwbwZ6MXXVn0Qo4f9MqSWveDW5ihXM+Q1yriAMwG6D512pu7Suw0b82Hd0
    2xLY05QJ42YGR3T2DUJXL4U1K+xsu+XN4ylCCDcERgjYWoQ+Ir/IjvVYhPeBYTof
    2obddOEfwaBuWbadfkhQPCCIyTxAdxxDLEbEpnFLrF3Nva5SLn3uCEGaWG4HvdKM
    yJ9TmBj5TtVbLHVhTYYhtsaMjaqnlJerp4UnoJ+asbbjzkP+tREeLQtXwS2wndp+
    K2T9bA7bAgMBAAECggEACiT0F92NIUrhUwgfWEJHDFPv35jWxLPoauN2yZYEiPQx
    uD7Wg3tYfY8hNQDz4ku0DloUnLsw8N4IflznKZ7DROjywqWX2VaVAjEIiQFjDQ/0
    bVqDV7doqTRp2M/gzxVYTuDBDULi8iwx025lFGcQIZS0EkkjyOFbglseBEzYqOvI
    8gb14je1FkadssdSY8AJY/pQ2OdfM+p7tf8TodrlsEzuvRdFX7E+4rI2woyEzSYu
    D4uNH0CiC1OhWB2ckFoL/tKH3D95jAY1WAdM295xkvLQpZRkENlrvYrtHvwvktXF
    1yqzObtijEeJhEqbm53KTdFU0ZFpFtlZnj1/e5MrEQKBgQDMslvoBVuFiJgCdT+R
    5VOn3BoBh0B8XF++xwjr5F8HwAJw1hdf1oDJMmFmJ3npFBMoWXA7gPfX8gpu91ys
    x575xrXo/mtOZUcN+En/n4MNDuWDN5AjsLREPexrvIZ5rYP+BZoh2UkeKjWVot98
    XQJtTT/4umcGjhCunI4ayjedkwKBgQC+OKReRrawyALVKsMGyGoOQlWg/Vsy+N0x
    DgpDIA998COCpH/O1/BI8d04QZu8Q3eeiYfDLcrAtcdgiE+DVGh9Xg1BMeYbgXIt
    lAwDYEEBSruS5FrrapTjigaGCnX4DDNZlDdMrGyw1yY5Cs8KvLua/s5YjPCTPGmm
    5dMSQ3LWmQKBgHuoV/svmV1u6h26BQA3ILVsMs2vjlZSW4jdplcS7BG7ff36Z75+
    z+g7pjlXKb+TYAtlFHbt70umLYVhq7u5ECHmWCh74glHB4i58MIa88lksWP2of3d
    ltkO648eIcLJ/s3rRnSiVhiB+UL/VLFFYtzy6O1ydiCwnAVQEEzA0p4/AoGAFnTn
    ar3caYhjVTkkJxPX+XD5XPUsJBtfOaBXs88AJTUJbC3xbMDvfB0Zqb+NHC+22n+Q
    CInKau/K5umQwYdggpRs6ipy6QJiMWFN/cQKSJXDCTduSGafxzEPThnEDZGbKlMm
    KCYe+s2blJZjFPhtCYJVZ/zTlf5G1s5BGeHel9kCgYBjhvI9d2yqZZ66fGkQDhFj
    244k1h2rPMq19/AiNjb6WgWZnEhE0YJnE+AxfHEw09t2hPrDPuH+XDsBKhzUMVXo
    hBFyUITkrLhyNHY7RFG06cwDYNK5PAxzC8Jy5lZCB8CoPDMeClwWz2+SNEd1CmKj
    izvvI8GSCdVzqugOIS6ltg==
    -----END PRIVATE KEY-----
""".trimIndent()

val DSA_CERT = """
    -----BEGIN CERTIFICATE-----
    MIIExzCCBHOgAwIBAgIUYsVc8kuQafNZ60JMXpvMUMjwaRAwCwYJYIZIAWUDBAMC
    MEUxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEwHwYDVQQKDBhJ
    bnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwIBcNMjQxMDA0MjA1MDU5WhgPMjI5ODA3
    MjAyMDUwNTlaMEUxCzAJBgNVBAYTAkFVMRMwEQYDVQQIDApTb21lLVN0YXRlMSEw
    HwYDVQQKDBhJbnRlcm5ldCBXaWRnaXRzIFB0eSBMdGQwggNCMIICNQYHKoZIzjgE
    ATCCAigCggEBANPK+o46bK+T81UDgHhTyuMumFUxQCV4sQipYBqNalDaK8aBpZ8F
    GZRAqTYG/ArulkeBBkj7Y3fsAvTXDz+lK/7zjuPO3+WTreGCffbMeXYGoGdTcBqh
    Dt90PE1lTzZNwBIwjLevwc5Y072fzJbtnV01LTWcT70N2NEZNZu3QsT4/+8BlYpx
    msin0JJMb7JBcMsVriqU3Q9eUi79F9eA3BUmrvU3M+0kwzG6RL4Zg8HtXfjDK54S
    J0l3djl1rntOd8+b8rNMnwSCHeYYwhDNGbdveNaShK90sGzhPui8xwmntbONknws
    JjGsKoCi3ElgHvMezJArfJ6G5rhrLlOCqusCHQDooh6z5jmm9JdhaEWUBmdnbpqf
    SqV77oUbmX+LAoIBAG+22hZUkwYGhD5bGZ9a7QhzF1GuQLBqlxO/P/1SyPw97j6u
    /so3lOyq1XvDTE3tOmNxQjk2ULmLQvp8yhUnyanQUs3VzyseXLrsnF5o8QMDuINw
    XSYcLUHYRWKdXjmM6dfoGS5ABsKdqJEKIOVLM90Z7Ux+dViXucXJRrOMGGRdMrK7
    ILV/0/DfreEbLHCrGRPMMTOaHHwkSNTzNAZG68IZgmpDleElxasVTEG6nMirUZlg
    zF1mo60oU6z0SCTxmKD/KdR88tf6avmVVjXnpJFmf5Ja9a47qV6bz7GBRc9Ld5Uu
    1pt1WqWutapNkGWroR568FMGlswVpgdxnLz0nLoDggEFAAKCAQBDC+agZwUnp1h0
    dI077p53Ez21zC41l9eK3oCNpM1t/+haMv23SEDgBzV/B3pQhQWE+rp2E5P+iUEb
    er59L3ZEks6NKi1YrMjdrIsJ51lhKpNs6GCD3u/BpaCVU9ZDeaZBH30OYs3Sid8C
    PtQFVzAloL+ZEIho17YzVFkI5eI91seBleEJeqPgJISISTF7PjO2zCtU9qop6L3m
    7PEub7dJpfrYtVedIJE4o2exOEDrVJAXAQZdE3J7Z8Pv3wGbX/yz8B+Mxjd0JFP2
    IqEjxtnPpanyJpUzQuRNxG165784U3p+aWPEqoGh3VQqFj+pj1pxHRANMGL2vFzR
    v1kFLboAo1MwUTAdBgNVHQ4EFgQUS+OyDzYHREXBie1icTHhc/dGfR8wHwYDVR0j
    BBgwFoAUS+OyDzYHREXBie1icTHhc/dGfR8wDwYDVR0TAQH/BAUwAwEB/zALBglg
    hkgBZQMEAwIDQQAwPgIdANrMjknQyZPEvScVt3Gb8h0YOkKo6znvtvxnAgcCHQDW
    TfsaJ5AXJQtJHXLUGhX2Qb5UMT/x1cnBS8H9
    -----END CERTIFICATE-----
""".trimIndent()

val DSA_PUB = """
    -----BEGIN PUBLIC KEY-----
    MIIDQjCCAjUGByqGSM44BAEwggIoAoIBAQDTyvqOOmyvk/NVA4B4U8rjLphVMUAl
    eLEIqWAajWpQ2ivGgaWfBRmUQKk2BvwK7pZHgQZI+2N37AL01w8/pSv+847jzt/l
    k63hgn32zHl2BqBnU3AaoQ7fdDxNZU82TcASMIy3r8HOWNO9n8yW7Z1dNS01nE+9
    DdjRGTWbt0LE+P/vAZWKcZrIp9CSTG+yQXDLFa4qlN0PXlIu/RfXgNwVJq71NzPt
    JMMxukS+GYPB7V34wyueEidJd3Y5da57TnfPm/KzTJ8Egh3mGMIQzRm3b3jWkoSv
    dLBs4T7ovMcJp7WzjZJ8LCYxrCqAotxJYB7zHsyQK3yehua4ay5TgqrrAh0A6KIe
    s+Y5pvSXYWhFlAZnZ26an0qle+6FG5l/iwKCAQBvttoWVJMGBoQ+WxmfWu0IcxdR
    rkCwapcTvz/9Usj8Pe4+rv7KN5TsqtV7w0xN7TpjcUI5NlC5i0L6fMoVJ8mp0FLN
    1c8rHly67JxeaPEDA7iDcF0mHC1B2EVinV45jOnX6BkuQAbCnaiRCiDlSzPdGe1M
    fnVYl7nFyUazjBhkXTKyuyC1f9Pw363hGyxwqxkTzDEzmhx8JEjU8zQGRuvCGYJq
    Q5XhJcWrFUxBupzIq1GZYMxdZqOtKFOs9Egk8Zig/ynUfPLX+mr5lVY156SRZn+S
    WvWuO6lem8+xgUXPS3eVLtabdVqlrrWqTZBlq6EeevBTBpbMFaYHcZy89Jy6A4IB
    BQACggEAQwvmoGcFJ6dYdHSNO+6edxM9tcwuNZfXit6AjaTNbf/oWjL9t0hA4Ac1
    fwd6UIUFhPq6dhOT/olBG3q+fS92RJLOjSotWKzI3ayLCedZYSqTbOhgg97vwaWg
    lVPWQ3mmQR99DmLN0onfAj7UBVcwJaC/mRCIaNe2M1RZCOXiPdbHgZXhCXqj4CSE
    iEkxez4ztswrVPaqKei95uzxLm+3SaX62LVXnSCROKNnsThA61SQFwEGXRNye2fD
    798Bm1/8s/AfjMY3dCRT9iKhI8bZz6Wp8iaVM0LkTcRteue/OFN6fmljxKqBod1U
    KhY/qY9acR0QDTBi9rxc0b9ZBS26AA==
    -----END PUBLIC KEY-----
""".trimIndent()

val DSA_PRIV = """
    -----BEGIN PRIVATE KEY-----
    MIICXAIBADCCAjUGByqGSM44BAEwggIoAoIBAQDTyvqOOmyvk/NVA4B4U8rjLphV
    MUAleLEIqWAajWpQ2ivGgaWfBRmUQKk2BvwK7pZHgQZI+2N37AL01w8/pSv+847j
    zt/lk63hgn32zHl2BqBnU3AaoQ7fdDxNZU82TcASMIy3r8HOWNO9n8yW7Z1dNS01
    nE+9DdjRGTWbt0LE+P/vAZWKcZrIp9CSTG+yQXDLFa4qlN0PXlIu/RfXgNwVJq71
    NzPtJMMxukS+GYPB7V34wyueEidJd3Y5da57TnfPm/KzTJ8Egh3mGMIQzRm3b3jW
    koSvdLBs4T7ovMcJp7WzjZJ8LCYxrCqAotxJYB7zHsyQK3yehua4ay5TgqrrAh0A
    6KIes+Y5pvSXYWhFlAZnZ26an0qle+6FG5l/iwKCAQBvttoWVJMGBoQ+WxmfWu0I
    cxdRrkCwapcTvz/9Usj8Pe4+rv7KN5TsqtV7w0xN7TpjcUI5NlC5i0L6fMoVJ8mp
    0FLN1c8rHly67JxeaPEDA7iDcF0mHC1B2EVinV45jOnX6BkuQAbCnaiRCiDlSzPd
    Ge1MfnVYl7nFyUazjBhkXTKyuyC1f9Pw363hGyxwqxkTzDEzmhx8JEjU8zQGRuvC
    GYJqQ5XhJcWrFUxBupzIq1GZYMxdZqOtKFOs9Egk8Zig/ynUfPLX+mr5lVY156SR
    Zn+SWvWuO6lem8+xgUXPS3eVLtabdVqlrrWqTZBlq6EeevBTBpbMFaYHcZy89Jy6
    BB4CHFDEvVFSzgtkBc6+yiz+/le9x3p6jPT7j4EdlbA=
    -----END PRIVATE KEY-----
""".trimIndent()

val ECDSA_CERT = """
    -----BEGIN CERTIFICATE-----
    MIIB4jCCAYegAwIBAgIUIssGbgYQxC1vhCZk6Xozr4O3AVIwCgYIKoZIzj0EAwIw
    RTELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxITAfBgNVBAoMGElu
    dGVybmV0IFdpZGdpdHMgUHR5IEx0ZDAgFw0yNDEwMDQyMTM3MzFaGA8yMjk4MDcy
    MDIxMzczMVowRTELMAkGA1UEBhMCQVUxEzARBgNVBAgMClNvbWUtU3RhdGUxITAf
    BgNVBAoMGEludGVybmV0IFdpZGdpdHMgUHR5IEx0ZDBZMBMGByqGSM49AgEGCCqG
    SM49AwEHA0IABPZKIlWdeo/obr2tDOKO3kEF9p0M3+916TndXPfs3f+FWHNEqLuh
    LN035jwqfu5IV1tMS9aNmrkKewRXeFtTzy+jUzBRMB0GA1UdDgQWBBT4V/JvyhI1
    IJ0naZQjxlNQSuO1zTAfBgNVHSMEGDAWgBT4V/JvyhI1IJ0naZQjxlNQSuO1zTAP
    BgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQDyIZ6mNytBDt2TWgsm
    NLtZOk3C7FfAdjFwPsNvvwrphwIhAPLMpg4AdbkmysWICWxX4ijQuisXryPLhv+Y
    74vz+IGl
    -----END CERTIFICATE-----
""".trimIndent()

val ECDSA_PUB = """
    -----BEGIN PUBLIC KEY-----
    MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE9koiVZ16j+huva0M4o7eQQX2nQzf
    73XpOd1c9+zd/4VYc0Sou6Es3TfmPCp+7khXW0xL1o2auQp7BFd4W1PPLw==
    -----END PUBLIC KEY-----
""".trimIndent()

val ECDSA_PRIV = """
    -----BEGIN PRIVATE KEY-----
    MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgC/6RIQIXb3KwlGUC
    d9FD3FrK0XuHNh5jN7OZD6GrMCyhRANCAAT2SiJVnXqP6G69rQzijt5BBfadDN/v
    dek53Vz37N3/hVhzRKi7oSzdN+Y8Kn7uSFdbTEvWjZq5CnsEV3hbU88v
    -----END PRIVATE KEY-----
""".trimIndent()
