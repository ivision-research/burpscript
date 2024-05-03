/**
 * Set highlight color of a request, add notes. These are visible in the Proxy history.
 */

export const REQ_FILTER = `(path-matches r"/api/endpoint")`

export function onRequest(req) {
    helpers.setHighlight(req, "yellow")
    return req
}

export function onResponse(res) {
    if (res.statusCode() == 200) {
        helpers.setNotes(res, "Yep")
    } else {
        helpers.setNotes(res, "Nope")
    }
    return res
}
