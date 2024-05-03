# Filter Expressions

Filter expressions are a Lisp-like DSL for selecting requests/responses that should be forwarded to a script. This document should contain most of the defined filter functions, but you can also search [Filter.kt](src/main/kotlin/com/carvesystems/burpscript/Filter.kt) for all potential functions.

Filter expressions are pulled out of scripts via the `REQ_FILTER` and `RES_FILTER` strings exported from that script. See the [example scripts](examples/).

Strings can be provided as raw strings (`r"..."`) or normal strings (`"..."`). Raw strings are not escaped at all and are generally useful for PATTERN args.

-   [and VARARG_STMT](#and-vararg_stmt)
-   [or VARARG_STMT](#or-vararg_stmt)
-   [not STMT](#not-stmt)
-   [in-scope](#in-scope)
-   [host-matches PATTERN](#host-matches-pattern)
-   [method-eq VARARG_STRING](#method-eq-vararg_string)
-   [path-contains PATTERN](#path-contains-pattern)
-   [path-matches PATTERN](#path-matches-pattern)
-   [file-ext-eq VARARG_STRING](#file-ext-eq-vararg_string)
-   [has-header VARARG_STRING](#has-header-vararg_string)
-   [body-contains PATTERN](#body-contains-pattern)
-   [body-matches PATTERN](#body-matches-pattern)
-   [has-json-key VARARG_STRING](#has-json-key-vararg_string)
-   [has-query-param VARARG_STRING](#has-query-param-vararg_string)
-   [has-form-param VARARG_STRING](#has-form-param-vararg_string)
-   [query-param-matches STRING PATTERN](#query-param-matches-string-pattern)
-   [status-code-eq VARARG_INT](#status-code-eq-vararg_int)
-   [status-code-in INT INT](#status-code-in-int-int)
-   [has-attachment VARARG_STRING](#has-attachment-vararg_string)
-   [listener-port-eq VARARG_INT](#listener-port-eq-vararg_int)
-   [tool-source-eq VARARG_STRING](#tool-source-eq-vararg_string)
-   [from-proxy](#from-proxy)



## and VARARG_STMT

Logical and of all passed statements

```
(and
    (path-contains "/api")
    (header-matches "Authorization" r"^Bearer\s+.*$"
        ...
    (in-scope)
)
```

## or VARARG_STMT

Logical or of all passed statements

```
(or
    (in-scope)
    (host-matches r".*\.google\.com")
        ...
    (has-header "X-Secret-Key")
)
```

## not STMT

Negates the given statement

```
(not (has-header "Authorization"))
```

## in-scope

Checks if the request is in scope

```
(in-scope)
```

## host-matches PATTERN

Check if the host portion of the URL matches the provided pattern.

*Note* - This does not inspect the `Host` header directly. Although Burp may construct a URL for a request using the Host header and the URL portion of the request, the host portion of the constructed URL _may differ_ from what the client sends in the Host header. In particular: If a transparent ["Invisible"](https://portswigger.net/burp/documentation/desktop/tools/proxy/invisible) proxy is used, the host in the URL will correspond with a hostname that is specified in the [Redirect settings](https://portswigger.net/burp/documentation/desktop/settings/tools/proxy#request-handling).

*Note* - Following form the above, when used as a response filter (`RES_FILTER`), the URL corresponding to the initiating request is used.

```
(host-matches r".*\.google\.com$")
```

## method-eq VARARG_STRING

Checks if the request method is one of the provided strings. Note this is case-sensitive.

```
(method-eq "PUT" "POST")
```

## path-contains PATTERN

Checks if the request path contains the given pattern

```
(path-contains "foo.*bar")
```

## path-matches PATTERN

Checks if the request path matches the given pattern

```
(path-matches r"^foo.*bar$")
```

## file-ext-eq VARARG_STRING

Checks if the requested file extension is any of the given strings.

```
(file-ext-eq ".php" ".js" ... ".html")
```

## has-header VARARG_STRING

Checks if the request/response has the given header. Header names are case-sensitive.

```
(has-header "Authorization")
```

## header-matches STRING PATTERN

Checks if the request/response has a header that matches the provided pattern. Header names are case-sensitive.

```
(header-matches "Content-Type" r".*application/json.*")
```

## body-contains PATTERN

Checks if the body contains the given pattern

```
(body-contains "\"isAdmin\":\\s+false")
```

## body-matches PATTERN

Checks if the entire body matches the provided pattern

```
(body-matches "^[0-9]+$")
```


## has-json-key VARARG_STRING

Searched for any of the provided JSON keys in the req/res body. This function supported dotted syntax for JSON keys to search for nested keys. Returns true if any of the provided keys match.


```
(has-json-key "user.isSuperAdmin")
```


## has-query-param VARARG_STRING

Checks that the given query parameter exists

```
(has-query-param "id")
```

## has-form-param VARARG_STRING

Checks that the request has the given form parameter

```
(has-form-param "id" "identifier")
```


## query-param-matches STRING PATTERN

Checks that the given query parameter matches the given pattern. If the parameter doesn't exist this evaluates to false.

```
(query-param-matches "id" r"[0-9]+")
```

## status-code-eq VARARG_INT

Only applicable as a Response filter, checks that the status code is one of the provided codes

```
(status-code-eq 200 201)
```

## status-code-in INT INT

Checks that the status code is in the given range (inclusive)

```
(status-code-in 200 299)
```

## has-attachment VARARG_STRING

Attachments are custom pieces of data attached to a request/response by other scripts. This checks to see if the given attachment key exists.

```
(has-attachment "such" "attachments")
```

## listener-port-eq VARARG_INT

Checks that the request was to a listener with one of the provided ports

```
(listener-port-eq 9090)
```

## tool-source-eq VARARG[ScriptsTab.kt](src%2Fmain%2Fkotlin%2Fcom%2Fcarvesystems%2Fburpscript%2Fui%2FScriptsTab.kt)_STRING

Returns true if the tool source is one of the provided sources. Valid sources:

- "Suite"
- "Target"
- "Proxy"
- "Scanner"
- "Intruder"
- "Repeater"
- "Logger"
- "Sequencer"
- "Decoder"
- "Comparer"
- "Extensions"
- "Recorded login replayer"
- "Organizer"

The match is case-insensitive

```
(tool-source-eq "Suite")
```

## from-proxy

Returns true of the request/response is associated with the Proxy tool type. This is also controllable via the UI, but it may be useful to have here too.

```
(from-proxy)
```
