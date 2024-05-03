
class Addon:

    REQ_FILTER = "(and true)"
    RES_FILTER = "(and true)"

    def on_request(self, req):
        pass

    def on_response(self, res):
        pass

class AnotherAddon:

    REQ_FILTER = "(and true)"
    RES_FILTER = "(and true)"

    def on_request(self, req):
        pass

    def on_response(self, res):
        pass

# Addons are executed in this order:
addons = [Addon(), AnotherAddon()]
