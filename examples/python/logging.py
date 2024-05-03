"""
`log` global prints messages to the Output and Errors tab in Extensions -> Burp Scripting
print() sends messages there too.

[logging.py][INFO] - GET - https://www.ivision.com/
[logging.py][INFO] - Logs INFO
[logging.py][DEBUG] - Got a response
[logging.py][INFO] - {'key': 'value'}
[logging.py][ERROR] - oh no
ZeroDivisionError: division by zero
"""

# Enable log.debug()
log.enableDebug(True)

def on_request(req):
    log.info(f"{req.method()} - {req.url()}")
    print("Logs INFO")
    return req


def on_response(res):
    log.debug("Got a response")
    obj = res.bodyToJson()
    print(obj)
    try:
        x = 1 / 0
    except Exception as ex:
        # Logs to Errors tab of the extension
        log.error("oh no", ex)
    return res



