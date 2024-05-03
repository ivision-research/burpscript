/**
 * Import from CommonJs module
 */

const { doSomething } = require('./common.js');

function initialize() {
    log.info("Initialized the JavaScript module");
}

function cleanup() {
    log.info("Cleaning up JavaScript");
}

function onRequest(req) {
    doSomething();
    return req;
}

function onResponse(res) {
    return res
}

module.exports = { initialize, cleanup, onRequest, onResponse }

