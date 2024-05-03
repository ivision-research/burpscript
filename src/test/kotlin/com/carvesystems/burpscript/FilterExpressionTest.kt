package com.carvesystems.burpscript

import burp.api.montoya.core.ToolType
import burp.api.montoya.http.HttpService
import burp.api.montoya.http.message.params.HttpParameterType
import burp.api.montoya.http.message.params.ParsedHttpParameter
import burp.api.montoya.utilities.Utilities
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.util.regex.Pattern

private fun parse(s: String): FilterExpression = FilterExpression.parse(s)

class FilterExpressionTest : StringSpec({

    beforeSpec {

        burpUtils = mockk<Utilities>()

        every {
            burpUtils.urlUtils()
        } returns TestUrlUtils()

    }

    "parses simple" {

        FilterExpression.parse("(and (in-scope) false)")

    }

    "parses complex1" {
        val expr = "(and " +
                "(not (has-header \"Authorization\")) " +
                "(header-matches \"X-Header\" \"^header.*value$\") " +
                "(path-contains \"/api/v2/.*\") " +
                ")"


        parse(expr)
    }

    "unquote string" {
        val unquoted = "\"asdf \\x00 \\\\ \\\" \\n \\t \\r qwerty\"".unquote()
        unquoted.shouldBe("asdf \u0000 \\ \" \n \t \r qwerty")
    }

    "raw string" {
        val raw = "r\"asdf \\x00 \\\\ \\\" \\n \\t \\r qwerty\"".extractRaw()
        raw.shouldBe("asdf \\x00 \\\\ \\\" \\n \\t \\r qwerty")
    }

    "body-contains" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.contains(any<Pattern>())
        } returns true

        val res = mockk<ScriptHttpResponse>()
        every {
            res.contains(any<Pattern>())
        } returns true

        val doesNotMatch = mockk<ScriptHttpResponse>()
        every {
            doesNotMatch.contains(any<Pattern>())
        } returns false

        val exp = parse(
            "(body-contains r\"foo\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()
        exp.matches(doesNotMatch).shouldBeFalse()
    }

    "body-matches" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.bodyToString()
        } returns "foobar"

        val res = mockk<ScriptHttpResponse>()
        every {
            res.bodyToString()
        } returns "foobar"

        val exp1 = parse(
            "(body-matches r\"fooba[rz]\")"
        )
        exp1.matches(req).shouldBeTrue()
        exp1.matches(res).shouldBeTrue()

        val exp2 = parse(
            "(body-matches r\"foo\")"
        )
        exp2.matches(req).shouldBeFalse()
        exp2.matches(res).shouldBeFalse()
    }

    "file-ext-eq" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.pathWithoutQuery()
        } returns "/foo/bar/baz.js"

        val reqWithoutPath = mockk<ScriptHttpRequest>()
        every {
            reqWithoutPath.pathWithoutQuery()
        } returns null

        val res = mockk<ScriptHttpResponse>()
        every {
            res.initiatingRequest()
        } returns req

        val resWithoutPath = mockk<ScriptHttpResponse>()
        every {
            resWithoutPath.initiatingRequest()
        } returns reqWithoutPath

        var exp = parse("(file-ext-eq \".html\" \"js\")")
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()
        exp.matches(reqWithoutPath).shouldBeFalse()
        exp.matches(resWithoutPath).shouldBeFalse()

        exp = parse("(file-ext-eq \".html\")")
        exp.matches(req).shouldBeFalse()
        exp.matches(res).shouldBeFalse()
        exp.matches(reqWithoutPath).shouldBeFalse()
        exp.matches(resWithoutPath).shouldBeFalse()
    }

    "has-attachment" {
        val req = ScriptHttpResponseImpl(
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            1,
            mutableMapOf("foo" to "bar"),
            mockk()
        )

        val res = ScriptHttpResponseImpl(
            mockk(),
            mockk(),
            mockk(),
            mockk(),
            1,
            mutableMapOf("foo" to "bar"),
            mockk()
        )

        val exp = parse(
            "(has-attachment \"foo\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()

        val exp2 = parse(
            "(has-attachment \"bar\" \"foo\")"
        )
        exp2.matches(req).shouldBeTrue()
        exp2.matches(res).shouldBeTrue()

        val exp3 = parse(
            "(has-attachment \"bar\")"
        )
        exp3.matches(req).shouldBeFalse()
        exp3.matches(res).shouldBeFalse()
    }

    "has-form-param" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.hasParameter("foo", HttpParameterType.BODY)
        } returns true
        every {
            req.hasParameter(not("foo"), any())
        } returns false

        val res = mockk<ScriptHttpResponse>()
        every {
            res.initiatingRequest()
        } returns req

        val exp = parse(
            "(has-form-param \"foo\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()

        val exp2 = parse(
            "(has-form-param \"bar\" \"foo\")"
        )
        exp2.matches(req).shouldBeTrue()
        exp2.matches(res).shouldBeTrue()

        val exp3 = parse(
            "(has-form-param \"bar\")"
        )
        exp3.matches(req).shouldBeFalse()
        exp3.matches(res).shouldBeFalse()
    }

    "has-header" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.hasHeader("foo")
        } returns true
        every {
            req.hasHeader(not("foo"))
        } returns false

        val res = mockk<ScriptHttpResponse>()
        every {
            res.hasHeader("foo")
        } returns true
        every {
            res.hasHeader(not("foo"))
        } returns false

        val exp = parse(
            "(has-header \"foo\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()

        val exp2 = parse(
            "(has-header \"bar\" \"foo\")"
        )
        exp2.matches(req).shouldBeTrue()
        exp2.matches(res).shouldBeTrue()

        val exp3 = parse(
            "(has-header \"bar\")"
        )
        exp3.matches(req).shouldBeFalse()
        exp3.matches(res).shouldBeFalse()
    }

    "has-json-key" {
        val testJson = """
            |{
            |   "exists": 1,
            |   "has": {
            |       "child": true
            |   }
            |}
        """.trimMargin()

        val req = mockk<ScriptHttpRequest>()
        every {
            req.bodyToString()
        } returns testJson

        val reqNotJson = mockk<ScriptHttpRequest>()
        every {
            reqNotJson.bodyToString()
        } returns "not json object"

        val res = mockk<ScriptHttpResponse>()
        every {
            res.bodyToString()
        } returns testJson

        val resNotJson = mockk<ScriptHttpResponse>()
        every {
            resNotJson.bodyToString()
        } returns "<html>not json object</html>"

        val resNotJsonObject = mockk<ScriptHttpResponse>()
        every {
            resNotJsonObject.bodyToString()
        } returns "[1, 2, 3]"

        var exp = parse(
            "(has-json-key \"exists\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()
        exp.matches(reqNotJson).shouldBeFalse()
        exp.matches(resNotJsonObject).shouldBeFalse()

        exp = parse(
            "(has-json-key \"doesntexist\")"
        )
        exp.matches(req).shouldBeFalse()
        exp.matches(res).shouldBeFalse()
        exp.matches(reqNotJson).shouldBeFalse()
        exp.matches(resNotJsonObject).shouldBeFalse()

        exp = parse(
            "(has-json-key \"has.child\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()
        exp.matches(reqNotJson).shouldBeFalse()
        exp.matches(resNotJsonObject).shouldBeFalse()

        exp = parse(
            "(has-json-key \"has.doesntexist\")"
        )
        exp.matches(req).shouldBeFalse()
        exp.matches(res).shouldBeFalse()
        exp.matches(reqNotJson).shouldBeFalse()
        exp.matches(resNotJsonObject).shouldBeFalse()
    }

    "has-query-param" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.hasParameter("foo", HttpParameterType.URL)
        } returns true
        every {
            req.hasParameter(not("foo"), any())
        } returns false

        val res = mockk<ScriptHttpResponse>()
        every {
            res.initiatingRequest()
        } returns req

        val exp = parse(
            "(has-query-param \"foo\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()

        val exp2 = parse(
            "(has-query-param \"bar\" \"foo\")"
        )
        exp2.matches(req).shouldBeTrue()
        exp2.matches(res).shouldBeTrue()

        val exp3 = parse(
            "(has-query-param \"bar\")"
        )
        exp3.matches(req).shouldBeFalse()
        exp3.matches(res).shouldBeFalse()
    }

    "header-matches" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.headerValue("foo")
        } returns "bar"
        every {
            req.headerValue(not("foo"))
        } returns null

        val resp = mockk<ScriptHttpResponse>()
        every {
            resp.headerValue("foo")
        } returns "bar"
        every {
            resp.headerValue(not("foo"))
        } returns null

        val exp = parse(
            "(header-matches \"foo\" r\"^bar$\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(resp).shouldBeTrue()

        val exp2 = parse(
            "(header-matches \"foo\" r\"^baz$\")"
        )
        exp2.matches(req).shouldBeFalse()
        exp2.matches(resp).shouldBeFalse()

        val exp3 = parse(
            "(header-matches \"bar\" r\"^foo$\")"
        )
        exp3.matches(req).shouldBeFalse()
        exp3.matches(resp).shouldBeFalse()
    }

    "host-matches" {
        val exampleHostReq = mockk<ScriptHttpRequest>()
        every {
            exampleHostReq.url()
        } returns "http://example.com/path/file.htm"

        val exampleHostResp = mockk<ScriptHttpResponse>()
        every {
            exampleHostResp.initiatingRequest()
        } returns exampleHostReq

        val noHostReq = mockk<ScriptHttpRequest>()
        every {
            noHostReq.url()
        } returns ""

        val noHostResp = mockk<ScriptHttpResponse>()
        every {
            noHostResp.initiatingRequest()
        } returns noHostReq

        val exp = parse(
            "(host-matches r\"example.com\")"
        )
        exp.matches(exampleHostResp).shouldBeTrue()
        exp.matches(exampleHostReq).shouldBeTrue()
        exp.matches(noHostResp).shouldBeFalse()
        exp.matches(noHostReq).shouldBeFalse()

        val exp2 = parse(
            "(host-matches r\"example.org\")"
        )
        exp2.matches(exampleHostResp).shouldBeFalse()
        exp2.matches(exampleHostReq).shouldBeFalse()
        exp2.matches(noHostResp).shouldBeFalse()
        exp2.matches(noHostReq).shouldBeFalse()
    }

    "in-scope" {
        val inScopeReq = mockk<ScriptHttpRequest>()
        every {
            inScopeReq.isInScope()
        } returns true

        val notInScopeReq = mockk<ScriptHttpRequest>()
        every {
            notInScopeReq.isInScope()
        } returns false

        val inScopeRes = mockk<ScriptHttpResponse>()
        every {
            inScopeRes.initiatingRequest()
        } returns inScopeReq

        val notInScopeRes = mockk<ScriptHttpResponse>()
        every {
            notInScopeRes.initiatingRequest()
        } returns notInScopeReq

        val exp = parse(
            "(in-scope)"
        )
        exp.matches(inScopeReq).shouldBeTrue()
        exp.matches(notInScopeReq).shouldBeFalse()
        exp.matches(inScopeRes).shouldBeTrue()
        exp.matches(notInScopeRes).shouldBeFalse()
    }

    "listener-port-eq" {
        val service8080 = mockk<HttpService>()
        every {
            service8080.port()
        } returns 8080

        val service8081 = mockk<HttpService>()
        every {
            service8081.port()
        } returns 8081

        val req8080 = mockk<ScriptHttpRequest>()
        every {
            req8080.httpService()
        } returns service8080

        val req8081 = mockk<ScriptHttpRequest>()
        every {
            req8081.httpService()
        } returns service8081

        val res8080 = mockk<ScriptHttpResponse>()
        every {
            res8080.initiatingRequest()
        } returns req8080

        val res8081 = mockk<ScriptHttpResponse>()
        every {
            res8081.initiatingRequest()
        } returns req8081

        val exp = parse(
            "(listener-port-eq 8080)"
        )
        exp.matches(req8080).shouldBeTrue()
        exp.matches(res8080).shouldBeTrue()
        exp.matches(req8081).shouldBeFalse()
        exp.matches(res8081).shouldBeFalse()

        val exp2 = parse(
            "(listener-port-eq 8081)"
        )
        exp2.matches(req8081).shouldBeTrue()
        exp2.matches(res8081).shouldBeTrue()
        exp2.matches(req8080).shouldBeFalse()
        exp2.matches(res8080).shouldBeFalse()

        val exp3 = parse(
            "(listener-port-eq 8080 8081)"
        )
        exp3.matches(req8081).shouldBeTrue()
        exp3.matches(res8081).shouldBeTrue()
        exp3.matches(req8080).shouldBeTrue()
        exp3.matches(res8080).shouldBeTrue()
    }

    "method-eq" {
        val postReq = mockk<ScriptHttpRequest>()
        every {
            postReq.method()
        } returns "POST"

        val postRes = mockk<ScriptHttpResponse>()
        every {
            postRes.initiatingRequest()
        } returns postReq

        val getReq = mockk<ScriptHttpRequest>()
        every {
            getReq.method()
        } returns "GET"

        val getRes = mockk<ScriptHttpResponse>()
        every {
            getRes.initiatingRequest()
        } returns getReq

        val exp = parse(
            "(method-eq \"POST\")"
        )
        exp.matches(postReq).shouldBeTrue()
        exp.matches(postRes).shouldBeTrue()
        exp.matches(getReq).shouldBeFalse()
        exp.matches(getRes).shouldBeFalse()

        val exp2 = parse(
            "(method-eq \"GET\")"
        )
        exp2.matches(getReq).shouldBeTrue()
        exp2.matches(getRes).shouldBeTrue()
        exp2.matches(postReq).shouldBeFalse()
        exp2.matches(postRes).shouldBeFalse()

        val exp3 = parse(
            "(method-eq \"GET\" \"POST\")"
        )
        exp3.matches(getReq).shouldBeTrue()
        exp3.matches(getRes).shouldBeTrue()
        exp3.matches(postReq).shouldBeTrue()
        exp3.matches(postRes).shouldBeTrue()
    }

    "path-matches / path-contains" {
        val req = mockk<ScriptHttpRequest>()
        every {
            req.pathWithoutQuery()
        } returns "/api/v1/widgets/10"

        val res = mockk<ScriptHttpResponse>()
        every {
            res.initiatingRequest()
        } returns req

        val reqWithoutPath = mockk<ScriptHttpRequest>()
        every {
            reqWithoutPath.pathWithoutQuery()
        } returns null

        val resWithoutPath = mockk<ScriptHttpResponse>()
        every {
            resWithoutPath.initiatingRequest()
        } returns reqWithoutPath

        var exp = parse(
            "(path-matches r\"^/api/v\\d+/widgets/\\d+$\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()
        exp.matches(reqWithoutPath).shouldBeFalse()

        exp = parse(
            "(path-matches r\"v\\d+/widgets/\\d+$\")"
        )
        exp.matches(req).shouldBeFalse()
        exp.matches(res).shouldBeFalse()
        exp.matches(reqWithoutPath).shouldBeFalse()

        exp = parse(
            "(path-contains r\"/widgets/\")"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(req).shouldBeTrue()
        exp.matches(reqWithoutPath).shouldBeFalse()

        exp = parse(
            "(path-contains r\"/whatsits/\")"
        )
        exp.matches(req).shouldBeFalse()
        exp.matches(res).shouldBeFalse()
        exp.matches(reqWithoutPath).shouldBeFalse()
    }

    "query-param-matches" {
        val unmatchedName = "unmatched"
        val matchedName = "queryParam"
        val matchedValue = "such+value%3dasdf"

        val matchedParam = mockk<ParsedHttpParameter>()
        every {
            matchedParam.name()
        } returns matchedName
        every {
            matchedParam.value()
        } returns matchedValue

        val unmatchedParam = mockk<ParsedHttpParameter>()
        every {
            unmatchedParam.name()
        } returns unmatchedName

        val req = mockk<ScriptHttpRequest>()
        every {
            req.parameters()
        } returns listOf(unmatchedParam, matchedParam)

        val res = mockk<ScriptHttpResponse>()
        every {
            res.initiatingRequest()
        } returns req

        val unmatchReq = mockk<ScriptHttpRequest>()
        every {
            unmatchReq.parameters()
        } returns listOf(unmatchedParam)

        val unmatchRes = mockk<ScriptHttpResponse>()
        every {
            unmatchRes.initiatingRequest()
        } returns unmatchReq

        val exp = parse("(query-param-matches \"$matchedName\" r\"^such value=asdf$\")")

        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()

        exp.matches(unmatchReq).shouldBeFalse()
        exp.matches(unmatchRes).shouldBeFalse()
    }

    "status-code-eq / status-code-in" {
        val req = mockk<ScriptHttpRequest>()

        val res = mockk<ScriptHttpResponse>()
        every {
            res.statusCode()
        } returns 301

        var exp = parse("(status-code-eq 301)")

        shouldThrow<InvalidTarget> {
            exp.matches(req)
        }
        exp.matches(res).shouldBeTrue()

        exp = parse("(status-code-eq 302)")
        exp.matches(res).shouldBeFalse()

        exp = parse("(status-code-in 300 301)")
        shouldThrow<InvalidTarget> {
            exp.matches(req)
        }
        exp.matches(res).shouldBeTrue()
        exp = parse("(status-code-in 200 299)")
        exp.matches(res).shouldBeFalse()
    }

    "from-proxy" {
        var req = mockk<ScriptHttpRequest>()
        every {
            req.toolSource()
        } returns SimpleToolSource(ToolType.PROXY)

        var res = mockk<ScriptHttpResponse>()
        every {
            res.toolSource()
        } returns SimpleToolSource(ToolType.PROXY)

        val exp = parse(
            "(from-proxy)"
        )
        exp.matches(req).shouldBeTrue()
        exp.matches(res).shouldBeTrue()

        req = mockk<ScriptHttpRequest>()
        every {
            req.toolSource()
        } returns SimpleToolSource(ToolType.REPEATER)


        res = mockk<ScriptHttpResponse>()
        every {
            res.toolSource()
        } returns SimpleToolSource(ToolType.REPEATER)
        exp.matches(req).shouldBeFalse()
        exp.matches(res).shouldBeFalse()
    }

    "tool-source-eq" {
        val proxyReq = mockk<ScriptHttpRequest>()
        every {
            proxyReq.toolSource()
        } returns SimpleToolSource(ToolType.PROXY)

        val repeaterReq = mockk<ScriptHttpRequest>()
        every {
            repeaterReq.toolSource()
        } returns SimpleToolSource(ToolType.REPEATER)

        val proxyRes = mockk<ScriptHttpResponse>()
        every {
            proxyRes.toolSource()
        } returns SimpleToolSource(ToolType.PROXY)

        val repeaterRes = mockk<ScriptHttpResponse>()
        every {
            repeaterRes.toolSource()
        } returns SimpleToolSource(ToolType.REPEATER)


        val exp = parse(
            "(tool-source-eq \"PROXY\")"
        )
        exp.matches(proxyReq).shouldBeTrue()
        exp.matches(proxyRes).shouldBeTrue()
        exp.matches(repeaterReq).shouldBeFalse()
        exp.matches(repeaterRes).shouldBeFalse()

        val exp2 = parse(
            "(tool-source-eq \"RePEaTER\")"
        )
        exp2.matches(repeaterReq).shouldBeTrue()
        exp2.matches(repeaterRes).shouldBeTrue()
        exp2.matches(proxyReq).shouldBeFalse()
        exp2.matches(proxyRes).shouldBeFalse()

        val exp3 = parse(
            "(tool-source-eq \"INTRUDER\" \"REPEATER\")"
        )
        exp3.matches(repeaterReq).shouldBeTrue()
        exp3.matches(repeaterRes).shouldBeTrue()
        exp3.matches(proxyReq).shouldBeFalse()
        exp3.matches(proxyRes).shouldBeFalse()
    }
})