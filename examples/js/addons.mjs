/**
 * Can also define classes that have all of the same keys and put an instance
 * of them in `addons`
 */

class Addon {

    RES_FILTER = "(and true)"
    REQ_FILTER = "(and true)"

    // Note that methods must be declared this way
    onRequest = function(req) {
        log.info("Addon.onRequest");
    }

    /*
    onRequest(req) {
        log.info("THIS DOESN'T WORK");
    }
    */

    onResponse = function(res) {
        log.info("Addon.onResponse");
    }

}

class AnotherAddon {

    RES_FILTER = "(and true)"
    REQ_FILTER = "(and true)"

    onRequest = function(req) {
        log.info("AnotherAddon.onRequest");
        return req;
    }

    onResponse = function(res) {
        log.info("AnotherAddon.onResponse");
        return res;
    }

}

/**
 * This provides the plugin with your additional addons. Addons are executed in this order
 */
export const addons = [new Addon(), new AnotherAddon()];
