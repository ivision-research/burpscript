/**
 * Conditionally drop requests and responses
 */

export const REQ_FILTER = `(path-matches r"^/logout$")`
export const RES_FILTER = `(path-matches r"^/login$")`

export function onRequest(req) {
    log.info("Dropping logout")
    return req.drop()
}

export function onResponse(res) {
    try {
        const obj = res.bodyToJson();
        if (obj.status == "failed") {
            log.info("Dropping failed login response")
            return res.drop()
        }
    } catch (e) {
        log.error(e)
    }

    return res
}
