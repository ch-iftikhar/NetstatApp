package com.yos.netstatapp

import android.os.Handler
import android.os.Looper

object NetworkDataBus {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: ((String) -> Unit)? = null

    fun setListener(l: ((String) -> Unit)?) {
        listener = l
    }

    fun postUpdate(text: String) {
        mainHandler.post {
            listener?.invoke(text)
        }
    }
}
