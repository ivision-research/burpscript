/**
 * Everything can be defined at the top level for quick scripts that only
 * perform a single action.
 *
 * ES6-style module (must be .mjs)
 */
export const RES_FILTER = "(and true)"
export const REQ_FILTER = "(and true)"

const HttpParameter = Java.type("burp.api.montoya.http.message.params.HttpParameter")
const HttpParameterType = Java.type("burp.api.montoya.http.message.params.HttpParameterType")

export function initialize() {
    log.info("Initialized the JavaScript module");
}

export function cleanup() {
    log.info("Cleaning up JavaScript");
}

export function onRequest(req) {
    log.info(`${req.method()} - ${req.url()}`)
    return req.withParameter(
        HttpParameter.parameter("__ivision", "injected", HttpParameterType.URL)
    )
}

export function onResponse(res) {
    log.info(`${res.statusCode()} - ${res.reasonPhrase()}`);
    return res;
}
