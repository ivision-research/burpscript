"""
Set highlight color of a request, add notes. These are visible in the Proxy history.
"""

REQ_FILTER = """
(path-matches r"/api/endpoint")
"""

def on_request(req):
    helpers.setHighlight(req, "yellow")
    return req

def on_response(res):
    if res.statusCode() == 200:
        helpers.setNotes(res, "Yep")
    else:
        helpers.setNotes(res, "Nope")
    return res
