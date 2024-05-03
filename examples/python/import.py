
from common import do_something

def on_request(req):
    do_something()
    return req


def on_response(res):
    return res

