package com.ajsharm.imagen.util

object DurationFormat {
    /** "0.5 s" / "12.4 s" / "1m 24s" */
    fun live(ms: Long): String {
        val totalSec = ms / 1000.0
        return if (totalSec < 60) "%.1f s".format(totalSec)
        else {
            val m = (ms / 60_000).toInt()
            val s = ((ms % 60_000) / 1000).toInt()
            "${m}m ${s}s"
        }
    }

    /** "24s" / "2m 15s" — used for stored duration on completed messages. */
    fun finalDuration(ms: Long): String {
        val totalSec = (ms / 1000).toInt()
        return if (totalSec < 60) "${totalSec}s"
        else {
            val m = totalSec / 60
            val s = totalSec % 60
            "${m}m ${s}s"
        }
    }
}
