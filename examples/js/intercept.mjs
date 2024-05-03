/**
 * Conditionally intercept requests and responses
 */
export const REQ_FILTER = `(path-matches r"^/login$")`
export const RES_FILTER = REQ_FILTER

export function onRequest(req) {
    log.info("Intercepting login request")
    return req.intercept()
}

export function onResponse(res) {
    log.info("Intercepting login response")
    return res.intercept()
}
