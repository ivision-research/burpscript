/**
 * `log` global prints messages to the Output and Errors tab in Extensions -> Burp Scripting
 * console.log, console.error, console.warn, console.info are also available
 *
 * [logging.mjs][INFO] - GET - https://www.ivision.com/
 * [logging.mjs][INFO] - Logs INFO
 * [logging.mjs][DEBUG] - Got a response
 * [logging.mjs][INFO] - {'key': 'value'}
 * [logging.mjs][ERROR] - oh no
 * TypeError: Cannot set property 'foo' of null
 */

export function onRequest(req) {
    log.info(`${req.method()} - ${req.url()}`)
    console.log("Logs INFO")
    return req
}

export function onResponse(res) {
    log.info(`${res.statusCode()} - ${res.reasonPhrase()}`);
    const obj = res.bodyToJson();
    console.log(obj);
    try {
        const obj = null;
        obj.foo = "bar";
    } catch (error) {
        log.error("oh no", error);
    }
    return res;
}
