package com.carvesystems.burpscript

import java.util.concurrent.Flow

abstract class BaseSubscriber<T> : Flow.Subscriber<T> {
    private var _sub: Flow.Subscription? = null

    protected val sub: Flow.Subscription
        get() = _sub ?: throw RuntimeException("not subscribed")

    override fun onComplete() {
    }

    override fun onError(throwable: Throwable?) {
    }

    override fun onSubscribe(subscription: Flow.Subscription) {
        _sub = subscription
        subscription.request(1)
    }

    protected fun requestAnother() {
        sub.request(1)
    }

    fun cancel() {
        _sub?.cancel()
    }


}