package com.archieapps.oklo.data.repo

import java.util.UUID

fun interface TimeSource {
    fun now(): Long

    companion object {
        val System = TimeSource { java.lang.System.currentTimeMillis() }
    }
}

object Stamp {
    fun next(previous: Long?, now: Long): Long = if (previous == null) now else maxOf(now, previous + 1)
}

fun newId(): String = UUID.randomUUID().toString()
