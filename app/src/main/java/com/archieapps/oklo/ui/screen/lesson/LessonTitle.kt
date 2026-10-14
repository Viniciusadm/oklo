package com.archieapps.oklo.ui.screen.lesson

private val headingLine = Regex("""^#{1,6}\s+(.+?)(?:\s*\{#[^}]*})?\s*$""")

fun lessonTitle(source: String, fallback: String): String =
    source.lineSequence()
        .mapNotNull { headingLine.matchEntire(it.trim())?.groupValues?.get(1)?.trim() }
        .firstOrNull { it.isNotEmpty() }
        ?: fallback
