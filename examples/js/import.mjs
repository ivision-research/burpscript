import { doSomething } from "./common.mjs";

export function onRequest(req) {
    doSomething();
    return req;
}

export function onResponse(res) {
    return res;
}