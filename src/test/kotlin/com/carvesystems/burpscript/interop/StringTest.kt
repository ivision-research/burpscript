package com.carvesystems.burpscript.interop


import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class StringTest : StringSpec() {
    init {
        "isHex" {
            "0123456789abcdef".isHex().shouldBeTrue()
            "0123456789ABCDEF".isHex().shouldBeTrue()
            "".isHex().shouldBeTrue() // Is it?
            "0123456789ABCDEFG".isHex().shouldBeFalse()
        }

        "decodeHex" {
            "00010203040506070809".decodeHex().shouldBe(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
            "".decodeHex().shouldBe(byteArrayOf())
        }

        "unsigned decodeHex" {
            "7F808182".decodeHex().shouldBe(byteArrayOf(127, -128, -127, -126))
        }

        "hex asByteArray" {
            "00010203040506070809".asByteArray().shouldBe(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))
            "".asByteArray().shouldBe(byteArrayOf())
        }

        "base64 asByteArray" {
            "AH+A/w==".asByteArray().shouldBe(byteArrayOf(0, 127, -128, -1))
        }
    }
}