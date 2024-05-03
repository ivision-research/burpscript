/**
 * Re-write request Host header.
 *
 * This can be useful in situations where you are using a transparent "Invisible" proxy
 * but for one reason or another, it isn't convenient or possible to get the client to
 * send requests with the Host header corresponding to the actual target.
 *
 * Similarly, Burp can be configured to forward requests to specific targets, but it
 * does not rewrite the Host header to the forward destination.
 *
 * - https://portswigger.net/burp/documentation/desktop/tools/proxy/invisible
 * - https://portswigger.net/burp/documentation/desktop/settings/tools/proxy#request-handling
 */

export const REQ_FILTER = `(header-matches "Host" ".*not-ivision.com.*")`

export function onRequest(req) {
    console.log(req.url())
    const fromHost = req.header("Host").value()
    const toHost = "ivision.com"
    console.log(`Rewriting Host header from ${fromHost} to ${toHost}`)
    return req.withUpdatedHeader("Host", toHost)
}