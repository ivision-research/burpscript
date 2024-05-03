"""
Conditionally intercept requests and responses
"""

RES_FILTER = REQ_FILTER = """
(path-matches r"^/login$")
"""

def on_request(req):
    log.info("Intercepting login request")
    return req.intercept()


def on_response(res):
    log.info("Intercepting login response")
    return res.intercept()
