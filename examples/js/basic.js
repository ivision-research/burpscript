/**
 * CommonJS style module (must be .js)
 */

const RES_FILTER = "(and true)"
const REQ_FILTER = "(and true)"

const HttpParameter = Java.type("burp.api.montoya.http.message.params.HttpParameter")
const HttpParameterType = Java.type("burp.api.montoya.http.message.params.HttpParameterType")

function initialize() {
    log.info("Initialized the JavaScript module");
}

function cleanup() {
    log.info("Cleaning up JavaScript");
}

function onRequest(req) {
    log.info(`${req.method()} - ${req.url()}`)
    return req.withParameter(
        HttpParameter.parameter("__ivision", "injected", HttpParameterType.URL)
    )
}

function onResponse(res) {
    log.info(`${res.statusCode()} - ${res.reasonPhrase()}`);
    return res;
}

module.exports = {
    RES_FILTER,
    REQ_FILTER,
    initialize,
    cleanup,
    onRequest,
    onResponse
}
