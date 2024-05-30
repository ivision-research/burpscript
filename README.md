# Burpscript

Burpscript adds dynamic scripting abilities to Burp Suite, allowing you to write scripts in Python or Javascript to manipulate HTTP requests and responses.

Features:

- Python 3 and JavaScript support
- Manipulate requests and responses from the Proxy or other tools such as the Repeater
- Conditionally drop requests & responses, or send them to the Intercept tab for manual inspection
- Hot reloading of scripts on file change
- Quickly enable/disable scripts
- Built-in cryptographic utilities
- Filter DSL for easily determining if the plugin's registered handlers should handle a request or response

## Beta Notice

Please note that, while we use this tool internally, it is still _beta software_ and there may be bugs. Please file issues if/when you encounter a bug! Also note that this is _untested on Windows_.

## Installation

The best way to build this project is to enter the `nix` development environment and then use the build script.

```sh
$ nix develop
# Build with Python support
$ ./build.sh
# Build with both Python and JavaScript Support
$ ./build.sh --js --python
```

If you don't want to use `nix`, you'll need to have `gradle` installed and can just run `./build.sh` without it, but this may cause issues with different `gradle` versions.

The resulting jar file will be in `build/libs/burpscript-plugin-<version>.jar`, which you can then install into Burp Suite through the Extensions -> Add window. For more information, see [Managing extensions](https://portswigger.net/burp/documentation/desktop/extensions/managing-extensions).

## Usage

Burpscript supports writing scripts in JavaScript or Python. When a script is added, Burpscript will call specially named handler functions defined in the script when a request or response is received, allowing scripts an opportunity to manipulate them as they pass through the proxy. Scripts can also define filter expressions using a [Lisp-like DSL](FILTER_EXPRESSIONS.md) to determine which requests and responses they should be applied to.

**References**

- The [examples](examples) directory
- The [ScriptHttpRequest](src/main/kotlin/com/carvesystems/burpscript/ScriptHttpRequest.kt) and [ScriptHttpResponse](src/main/kotlin/com/carvesystems/burpscript/ScriptHttpResponse.kt) classes. These define the API that scripts can use to modify requests and responses.
- [Burp Montoya API Javadoc](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html). In particular, [HttpRequestToBeSent](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/http/handler/HttpRequestToBeSent.html) and [HttpResponseReceived](https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/http/handler/HttpResponseReceived.html). 

### Python

Python scripts look like this. Examples can be found in the [examples](examples/python) directory, and for more information about how Python behaves when interacting with Java, see the [GraalVM interop reference](https://www.graalvm.org/latest/reference-manual/python/Interoperability/).

```python
REQ_FILTER = """..."""
RES_FILTER = """..."""

def initialize():
    print("Initialized Python script")


def cleanup():
    print("Cleaning up Python script")


def on_request(req):
    print(f"{req.method()} - {req.url()}")
    return req.withBody("Modified")


def on_response(res):
    print(f"{res.statusCode()} - {res.reasonPhrase()}")
```

### JavaScript

Scripts can be written as either ES6 or CommonJS style modules. Examples can be found in the [examples](examples/js) directory, and for more information about how JavaScript behaves when interacting with Java, see the [GraalVM interop reference](https://www.graalvm.org/latest/reference-manual/js/Interoperability/).

Scripts with the file extension `.mjs`, are treated as ES6 modules, where exported handlers look like this:

```javascript
export const RES_FILTER = "..."
export const REQ_FILTER = "..."

export function initialize() {
    console.log("Initialized the JavaScript module");
}

export function cleanup() {
    console.log("Cleaning up JavaScript");
}

export function onRequest(req) {
    console.log(`${req.method()} - ${req.url()}`)
    return req.withBody("Modified")
}

export function onResponse(res) {
    console.log(`${res.statusCode()} - ${res.reasonPhrase()}`);
    return res;
}
```

Scripts with the extension`.js`, are treated as CommonJS modules, where handlers are exported with `module.exports`:

```javascript
module.exports = {
    RES_FILTER: "...",
    REQ_FILTER: "...",
    initialize: function() {
        ...
    },
    cleanup: function() {
        ...
    },
    onRequest: function(req) {
        ...
    },
    onResponse: function(res) {
        ...
    }
}
```

### Addons

Scripts may also define handlers using an "Addon" style, similar to Mitmproxy. Each addon can define their own filter expressions and handlers. This is useful for organizing complex scripts or sharing addons between different scripts.

**Python**
```python
class AddonOne:
    REQ_FILTER = "..."
    def on_request(self, req):
        ...

class AddonTwo:
    RES_FILTER = "..."
    def on_response(self, res):
        ...

addons = [AddonOne(), AddonTwo()]
```

**JavaScript**
```javascript
class AddonOne {
    // The methods must be declared this way
    onRequest = function(req) {
        ...
    }
}

class AddonTwo {
    RES_FILTER = "..."
    onResponse = function(res) {
        ...
    }
}

export const addons = [new AddonOne(), new AddonTwo()]
```

### Script Globals

Scripts have the following global parameters available:

- `api` - a [MontoyaApi]((https://portswigger.github.io/burp-extensions-montoya-api/javadoc/burp/api/montoya/MontoyaApi.html)) instance
- `helpers` - an instance of [ScriptHelpers](src/main/kotlin/com/carvesystems/burpscript/ScriptHelpers.kt)
- `log` - an instance of [ScriptLogger](src/main/kotlin/com/carvesystems/burpscript/Logger.kt)

### Printing

Scripts can print messages to the Burpscript Extension tab using the `log` object, or with `console.log` in JavaScript, and `print` in Python. Regular messages go to the Output tab, and errors and exceptions go to the Errors tab (see [Managing extensions](https://portswigger.net/burp/documentation/desktop/extensions/managing-extensions))

**Python**
```python
log.info("This is an info message")
log.error("This is an error message", Exception("Oh no!")) # Goes to Errors tab
print("This is an info message")
```

**JavaScript**
```javascript
log.info("This is an info message");
log.error("This is an exception", new Error("On no!")); // Goes to Errors tab
console.log("This is an info message");
console.error("This is an error message"); // Goes to Errors tab
```

### Using Java

Java classes can also be imported and used directly from scripts. In python the `java` module can be imported. In JavaScript, the `Java` global object is available. These can be used to import Java types and use them in scripts. 

**Python**
```python
import java

HttpParameter = java.type("burp.api.montoya.http.message.params.HttpParameter")
HttpParameterType = java.type("burp.api.montoya.http.message.params.HttpParameterType")

def on_request(req):
    return req.withParameter(
        HttpParameter.parameter("__carve", "injected", HttpParameterType.URL)
    )
```

**JavaScript**
```javascript
const HttpParameter = Java.type("burp.api.montoya.http.message.params.HttpParameter")
const HttpParameterType = Java.type("burp.api.montoya.http.message.params.HttpParameterType")

export function onRequest(req) {
    return req.withParameter(
        HttpParameter.parameter("__carve", "injected", HttpParameterType.URL)
    )
}
```

### Importing

Scripts can import other modules that reside in the same directory.

**Python**
```python
# common.py
def do_something():
    ...
```

```python
# script.py
from common import do_something
```

**JavaScript (ES6)**
```javascript
// common.mjs
export function doSomething() {
    ...
}
```

```javascript
// script.mjs
import { doSomething } from './common.mjs'
```

### Limitations

There are some limitations with the polyglot API and how values are handled between the script and JVM. If you run into issues with this, it may be difficult to debug exactly what has gone wrong. We're working on helper functions to make these issues easier to deal with. Also, sometimes `import` statements in Python don't work. If you run into such issues, sometimes it may be easier to use `helpers.exec(...)` or `helpers.execStdin(...)`.

## Filter Expressions

Filter expressions are a Lisp-like DSL for selecting requests/responses that should be forwarded on to a script. See [FILTER_EXPRESSIONS.md](FILTER_EXPRESSIONS.md) for documentation.

## Configuration

Configuration is available via the `${XDG_CONFIG_HOME:-$HOME/.config}/burpscript/conf.json` file. An example config is shown in [the examples dir](examples/conf.json).
