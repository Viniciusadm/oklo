package com.archieapps.oklo.core.session

object CodePad {
    const val SLOTS = 4

    fun shuffleKeypad(random: kotlin.random.Random = kotlin.random.Random.Default): List<String> =
        ('0'..'9').shuffled(random)
            .chunked(2)
            .map { pair -> pair.joinToString("") }

    fun assemble(chosen: List<String?>): String? {
        if (chosen.size != SLOTS || chosen.any { it == null || it.length != 2 }) {
            return null
        }

        return chosen.joinToString("")
    }

    fun label(option: String): String = option.toCharArray().joinToString(" ou ")
}
