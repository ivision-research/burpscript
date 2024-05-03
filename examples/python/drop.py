"""
Conditionally drop requests and responses
"""

REQ_FILTER = """
(path-matches r"^/logout$")
"""

RES_FILTER = """
(path-matches r"^/login$")
"""

def on_request(req):
    log.info("Dropping logout")
    return req.drop()

def on_response(res):
    try:
        obj = res.bodyToJson()
        if obj.get("status") == "failed":
            log.info("Dropping failed login response")
            return res.drop()
    except Exception as e:
        pass

    return res
