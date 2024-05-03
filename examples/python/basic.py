import java

HttpParameter = java.type("burp.api.montoya.http.message.params.HttpParameter")
HttpParameterType = java.type("burp.api.montoya.http.message.params.HttpParameterType")

REQ_FILTER = """
(and
    (in-scope)
    (path-contains "api")
)
"""

RES_FILTER = """
(header-matches "Content-Type" r"application/json")
"""

def initialize():
    log.info("Initialized Python script")


def cleanup():
    log.info("Cleaning up Python script")


def on_request(req):
    log.info(f"{req.method()} - {req.url()}")
    return req.withParameter(
        HttpParameter.parameter("__ivision", "injected", HttpParameterType.URL)
    )


def on_response(res):
    log.info(f"{res.statusCode()} - {res.reasonPhrase()}")

