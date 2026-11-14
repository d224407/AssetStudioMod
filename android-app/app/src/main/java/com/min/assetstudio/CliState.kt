package com.min.assetstudio

import android.os.Handler
import android.os.Looper

/** Shared run state and log buffer between the service and the UI. */
object CliState {
    enum class Phase { IDLE, INSTALLING, RUNNING, DONE, ERROR }

    @Volatile var phase = Phase.IDLE
    @Volatile var progress = 0
    var listener: (() -> Unit)? = null

    private val lines = ArrayDeque<String>()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var pending = false

    fun log(s: String) {
        synchronized(lines) {
            lines.addLast(s)
            while (lines.size > 3000) lines.removeFirst()
        }
        notifyUi()
    }

    fun clear() {
        synchronized(lines) { lines.clear() }
        notifyUi()
    }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }

    fun set(p: Phase) {
        phase = p
        notifyUi()
    }

    // Coalesce bursts of log lines into one UI refresh.
    private fun notifyUi() {
        if (pending) return
        pending = true
        main.postDelayed({
            pending = false
            listener?.invoke()
        }, 150)
    }
}
