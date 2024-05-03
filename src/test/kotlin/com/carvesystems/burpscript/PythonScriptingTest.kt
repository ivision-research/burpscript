package com.carvesystems.burpscript

import burp.api.montoya.core.Annotations
import burp.api.montoya.core.ByteArray
import burp.api.montoya.core.ToolType
import burp.api.montoya.http.handler.HttpRequestToBeSent
import burp.api.montoya.internal.ObjectFactoryLocator
import com.carvesystems.burpscript.interop.fromJson
import com.carvesystems.burpscript.interop.toByteArray
import com.carvesystems.burpscript.shouldBe
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import kotlin.io.path.writeText

val RSA_CERT = """
    |-----BEGIN CERTIFICATE-----
    |MIIDZTCCAk2gAwIBAgIUVR/hpDCCABNy71NFQSBaUOKGWt4wDQYJKoZIhvcNAQEL
    |BQAwQjELMAkGA1UEBhMCVVMxFTATBgNVBAcMDERlZmF1bHQgQ2l0eTEcMBoGA1UE
    |CgwTRGVmYXVsdCBDb21wYW55IEx0ZDAeFw0yNDA0MjIxOTU2NTdaFw0yNDA1MjIx
    |OTU2NTdaMEIxCzAJBgNVBAYTAlVTMRUwEwYDVQQHDAxEZWZhdWx0IENpdHkxHDAa
    |BgNVBAoME0RlZmF1bHQgQ29tcGFueSBMdGQwggEiMA0GCSqGSIb3DQEBAQUAA4IB
    |DwAwggEKAoIBAQCYGaqn86RVCoUPmu8YoosxUDDvd47v7va7/u1C34B/BIyWq0v7
    |MG8Y/HjkTohrRahiwNydLz66xXQLy5zRFTHwbwZ6MXXVn0Qo4f9MqSWveDW5ihXM
    |+Q1yriAMwG6D512pu7Suw0b82Hd02xLY05QJ42YGR3T2DUJXL4U1K+xsu+XN4ylC
    |CDcERgjYWoQ+Ir/IjvVYhPeBYTof2obddOEfwaBuWbadfkhQPCCIyTxAdxxDLEbE
    |pnFLrF3Nva5SLn3uCEGaWG4HvdKMyJ9TmBj5TtVbLHVhTYYhtsaMjaqnlJerp4Un
    |oJ+asbbjzkP+tREeLQtXwS2wndp+K2T9bA7bAgMBAAGjUzBRMB0GA1UdDgQWBBTa
    |44Y0RweCqi5Kd0jSCqg8LaCTbTAfBgNVHSMEGDAWgBTa44Y0RweCqi5Kd0jSCqg8
    |LaCTbTAPBgNVHRMBAf8EBTADAQH/MA0GCSqGSIb3DQEBCwUAA4IBAQCWpf83p2B4
    |L7zGw9mQk8B40GZScebeinHs5uCVKz9vXyTBWVX6QUUfnEqnnI6UKfjimOXyo6d2
    |JHuVGY2BTUbt34DB8CjFqA6LRArz5mMZ9njuoJA86RSJ8R7Fz8RVmubsETF+kPJn
    |TRqtx++l7+CZur+77ie04oN0hoOEoMmSJTsEdbmG/vZHDKjGYSUjWjVvyy7nR4gI
    |rOSrK2Y7LCYmxypOym5nJ0P1SsG36udmK1Ms3lXsf0tRiRmf18izOo/2ZJewOYTy
    |QwleJYPNcK7RXKzolhLTvU52qKNi5LyizIAtxIjFQmezitXDQH5sdYOI+MEmU68R
    |1QOwxKKFvXOS
    |-----END CERTIFICATE-----
""".trimMargin()

val RSA_PUB = """
    |-----BEGIN PUBLIC KEY-----
    |MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAmBmqp/OkVQqFD5rvGKKL
    |MVAw73eO7+72u/7tQt+AfwSMlqtL+zBvGPx45E6Ia0WoYsDcnS8+usV0C8uc0RUx
    |8G8GejF11Z9EKOH/TKklr3g1uYoVzPkNcq4gDMBug+ddqbu0rsNG/Nh3dNsS2NOU
    |CeNmBkd09g1CVy+FNSvsbLvlzeMpQgg3BEYI2FqEPiK/yI71WIT3gWE6H9qG3XTh
    |H8Ggblm2nX5IUDwgiMk8QHccQyxGxKZxS6xdzb2uUi597ghBmlhuB73SjMifU5gY
    |+U7VWyx1YU2GIbbGjI2qp5SXq6eFJ6CfmrG2485D/rURHi0LV8EtsJ3afitk/WwO
    |2wIDAQAB
    |-----END PUBLIC KEY-----
""".trimMargin()

val RSA_PRIV = """
    |-----BEGIN PRIVATE KEY-----
    |MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQCYGaqn86RVCoUP
    |mu8YoosxUDDvd47v7va7/u1C34B/BIyWq0v7MG8Y/HjkTohrRahiwNydLz66xXQL
    |y5zRFTHwbwZ6MXXVn0Qo4f9MqSWveDW5ihXM+Q1yriAMwG6D512pu7Suw0b82Hd0
    |2xLY05QJ42YGR3T2DUJXL4U1K+xsu+XN4ylCCDcERgjYWoQ+Ir/IjvVYhPeBYTof
    |2obddOEfwaBuWbadfkhQPCCIyTxAdxxDLEbEpnFLrF3Nva5SLn3uCEGaWG4HvdKM
    |yJ9TmBj5TtVbLHVhTYYhtsaMjaqnlJerp4UnoJ+asbbjzkP+tREeLQtXwS2wndp+
    |K2T9bA7bAgMBAAECggEACiT0F92NIUrhUwgfWEJHDFPv35jWxLPoauN2yZYEiPQx
    |uD7Wg3tYfY8hNQDz4ku0DloUnLsw8N4IflznKZ7DROjywqWX2VaVAjEIiQFjDQ/0
    |bVqDV7doqTRp2M/gzxVYTuDBDULi8iwx025lFGcQIZS0EkkjyOFbglseBEzYqOvI
    |8gb14je1FkadssdSY8AJY/pQ2OdfM+p7tf8TodrlsEzuvRdFX7E+4rI2woyEzSYu
    |D4uNH0CiC1OhWB2ckFoL/tKH3D95jAY1WAdM295xkvLQpZRkENlrvYrtHvwvktXF
    |1yqzObtijEeJhEqbm53KTdFU0ZFpFtlZnj1/e5MrEQKBgQDMslvoBVuFiJgCdT+R
    |5VOn3BoBh0B8XF++xwjr5F8HwAJw1hdf1oDJMmFmJ3npFBMoWXA7gPfX8gpu91ys
    |x575xrXo/mtOZUcN+En/n4MNDuWDN5AjsLREPexrvIZ5rYP+BZoh2UkeKjWVot98
    |XQJtTT/4umcGjhCunI4ayjedkwKBgQC+OKReRrawyALVKsMGyGoOQlWg/Vsy+N0x
    |DgpDIA998COCpH/O1/BI8d04QZu8Q3eeiYfDLcrAtcdgiE+DVGh9Xg1BMeYbgXIt
    |lAwDYEEBSruS5FrrapTjigaGCnX4DDNZlDdMrGyw1yY5Cs8KvLua/s5YjPCTPGmm
    |5dMSQ3LWmQKBgHuoV/svmV1u6h26BQA3ILVsMs2vjlZSW4jdplcS7BG7ff36Z75+
    |z+g7pjlXKb+TYAtlFHbt70umLYVhq7u5ECHmWCh74glHB4i58MIa88lksWP2of3d
    |ltkO648eIcLJ/s3rRnSiVhiB+UL/VLFFYtzy6O1ydiCwnAVQEEzA0p4/AoGAFnTn
    |ar3caYhjVTkkJxPX+XD5XPUsJBtfOaBXs88AJTUJbC3xbMDvfB0Zqb+NHC+22n+Q
    |CInKau/K5umQwYdggpRs6ipy6QJiMWFN/cQKSJXDCTduSGafxzEPThnEDZGbKlMm
    |KCYe+s2blJZjFPhtCYJVZ/zTlf5G1s5BGeHel9kCgYBjhvI9d2yqZZ66fGkQDhFj
    |244k1h2rPMq19/AiNjb6WgWZnEhE0YJnE+AxfHEw09t2hPrDPuH+XDsBKhzUMVXo
    |hBFyUITkrLhyNHY7RFG06cwDYNK5PAxzC8Jy5lZCB8CoPDMeClwWz2+SNEd1CmKj
    |izvvI8GSCdVzqugOIS6ltg==
    |-----END PRIVATE KEY-----
""".trimMargin()

class PythonScriptingTest : StringSpec() {
    private lateinit var ctx: Context

    companion object {
        const val TEST_FUNCTION = "test_func"
        const val MESSAGE_ID = 1
    }

    private fun exec(script: String, vararg args: Any): Value {
        val src = Source.newBuilder("python", script, "test-script.py").build()
        val parsed = ctx.eval(src)
        parsed.hasMember(TEST_FUNCTION).shouldBeTrue()
        val value = parsed.getMember(TEST_FUNCTION)
        return value.execute(*args)
    }

    private fun mockRequest(): Pair<ScriptHttpRequest, HttpRequestToBeSent> {
        val req = mockk<HttpRequestToBeSent>(relaxed = true)
        val annotations = mockk<Annotations>(relaxed = true)

        every {
            req.annotations()
        } returns annotations

        every {
            req.messageId()
        } returns MESSAGE_ID

        every {
            req.toolSource()
        } returns SimpleToolSource(ToolType.PROXY)

        val wrapped = ScriptHttpRequestImpl.wrap(req)
        return wrapped to req
    }

    init {

        beforeSpec {
            ctx = PythonContextBuilder()
                .withBindings("helpers" to ScriptHelpers())
                .build()
        }

        "RSA crypto API encrypt" {
            val plaintext = "encrypted rsa message using public key".toByteArray()

            val makeScript = { key: String ->
                """
                |key = '''$key'''
                |
                |def $TEST_FUNCTION(data, enc): 
                |   cryptoHelper = helpers.getCryptoHelper()
                |   crypto = cryptoHelper.getCrypto("RSA/ECB/PKCS1Padding")
                |   crypto.setKey(key)
                |   if enc:
                |       return crypto.encrypt(data)
                |   return crypto.decrypt(data)
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

        "RSA crypto API decrypt" {

            val pubExpected = "encrypted rsa message using public key".toByteArray()
            val encWithPub =
                "Utbv6YSoEPRHsIzSjv0IQ6Gam4r9ADcJESSX4hnPWKbyDQ9GaKdmmbeLi3HR6ISKaM9BuaNL2XWmIlQezd0lfu9XH6WBs03fl5+LWLmHQqfP4mhTDbIYvZZfRRA7NRjQCjsQJkI6X32d71b7yTRY1FTCS+U+p92zt5lsmqvmCz3DTh2gYpPDa0Q2zg59MiGltvu4JYK1gpiflnbl6EOGgatmpRPhPDWPB9vw9YY7QAjMo4xVUDgBcciGHlYspihJFwUbaBoIqVrkjZG4nbbOPRvjgEu8GZEk0bOmrUkToTibRVdc9Hfu+NHscfGF2vLWl4I3QIJXWzSXgVsULaCS2A=="


            val makeScript = { key: String ->
                """
                |key = '''$key'''
                |
                |def $TEST_FUNCTION(data, enc): 
                |   cryptoHelper = helpers.getCryptoHelper()
                |   crypto = cryptoHelper.getCrypto("RSA/ECB/PKCS1Padding")
                |   crypto.setKey(key)
                |   if enc:
                |       return crypto.encrypt(data)
                |   return crypto.decrypt(data)
                """.trimMargin()
            }

            val run = { key: String, data: String ->
                val script = makeScript(key)
                val ret = exec(script, data, false)
                ret.toByteArray()
            }

            val privRes = run(RSA_PRIV, encWithPub)
            privRes.size.shouldBe(pubExpected.size)
            privRes.shouldBe(pubExpected)
        }

        "AES 128 crypto API" {

            val enc = "D19slxzpj25Nz2EQHRoyOTBzx6FGbbaix7HGAvLgA8mHL3FthThZZ/Jr+vNJmhJY"
            val dec = "testencryptionshouldbelongerthananaesblock\n".toByteArray()

            val makeScript = { key: String ->
                """
                |key = $key
                |iv = key
                |
                |def $TEST_FUNCTION(data, enc): 
                |   cryptoHelper = helpers.getCryptoHelper()
                |   crypto = cryptoHelper.getCrypto("AES/CBC/PKCS5Padding")
                |   crypto.setKey(key)
                |   if enc:
                |       return crypto.encryptWithIV(data, iv)
                |   return crypto.decryptWithIV(data, iv)
            """.trimMargin()
            }

            val run = { key: String ->
                val script = makeScript(key)
                val ret = exec(script, enc, false)
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

        "withBytes allows passing byte arrays" {
            val script = """
                |def ${TEST_FUNCTION}(req):
                |   return req.withBytes(b'\x00\x7f\x80\xff')
            """.trimMargin()

            val (wrapped, req) = mockRequest()
            val bytesInput = slot<ByteArray>()

            every {
                req.withBody(capture(bytesInput))
            } returns req

            ObjectFactoryLocator.FACTORY

            exec(script, wrapped)

            verify {
                req.annotations()
                req.toolSource()
                req.messageId()
                req.withBody(capture(bytesInput))
            }
            confirmVerified(req)

            val expected = byteArrayOf(0x00, 0x7F, -128, -1)

            val bytes = bytesInput.captured

            bytes.length().shouldBe(expected.size)

            bytes.forEachIndexed { idx, value ->
                value.shouldBe(expected[idx])
            }
        }

        "can pass Python arrays to withJson" {
            val script = """
                |def ${TEST_FUNCTION}(req):
                |   return req.withJson(
                |       [ True, None, False, { 'inner': 'value' } ]
                |   )
            """.trimMargin()

            val req = mockk<ScriptHttpRequest>()
            val value = slot<Value>()
            every {
                req.withJson(capture(value))
            } returns req

            exec(script, req)
            verify {
                req.withJson(capture(value))
            }

            val json: Value = value.captured
            json.shouldContainExactly(true, null, false, mapOf("inner" to "value"))
        }

        "can pass Python dicts to withJson" {
            val script = """
                |def ${TEST_FUNCTION}(req):
                |   return req.withJson({
                |       'string': 'string',
                |       'dict': {
                |           'double': 1.2,
                |           'number': 12
                |       },
                |       'arr': [ True, None, False, { 'inner': 'value' } ]
                |   })
            """.trimMargin()

            val req = mockk<ScriptHttpRequest>()
            val value = slot<Value>()
            every {
                req.withJson(capture(value))
            } returns req

            exec(script, req)
            verify {
                req.withJson(capture(value))
            }

            val json: Value = value.captured
            json.shouldContainExactly(
                mapOf(
                    "string" to "string",
                    "dict" to mapOf(
                        "double" to 1.2,
                        "number" to 12
                    ),
                    "arr" to listOf(true, null, false, mapOf("inner" to "value"))
                )
            )
        }

        "can use json like dict" {
            val script = """
                def ${TEST_FUNCTION}(req):
                    d = req.bodyToJson()
                    d["obj"]["key"] = "modified"
                    d["obj"]["new"] = "value"
                    return req.withJson(d)
            """.trimIndent()

            val req = mockk<ScriptHttpRequest>()
            val value = slot<Value>()
            every {
                req.bodyToJson()
            } returns fromJson(
                """
                {
                    "foo": "bar",
                    "obj": {
                        "key": "unmodified"
                    }
                }
                """.trimIndent()
            )
            every {
                req.withJson(capture(value))
            } returns req

            exec(script, req)
            verify {
                req.withJson(capture(value))
            }
            value.captured.shouldContainExactly(
                mapOf(
                    "foo" to "bar",
                    "obj" to mapOf(
                        "key" to "modified",
                        "new" to "value"
                    )
                )
            )
        }
    }
}

class PythonContextTest : StringSpec() {
    init {
        "import" {
            tempdir() { importPath ->
                val toImport = importPath.resolve("common.py")
                toImport.writeText(
                    """
                    def do_something():
                        return "did something"
                """.trimIndent()
                )
                val ctx = PythonContextBuilder().withImportPath(importPath).build()

                ctx.eval("python", "import common")
                val ret = ctx.eval("python", "common.do_something()")
                ret.shouldBe("did something")
            }
        }
    }
}

class PythonBindingsTest : StringSpec() {
    init {
        "print" {
            val logger = mockk<ScriptLogger>()
            val msg = slot<Value>()
            every {
                logger.info(capture(msg))
            } returns Unit

            val ctx = PythonContextBuilder().withConsoleLogger(logger).build()

            val script = """print("hello")"""
            ctx.eval("python", script)
            msg.captured.shouldBe("hello")
        }
    }
}
