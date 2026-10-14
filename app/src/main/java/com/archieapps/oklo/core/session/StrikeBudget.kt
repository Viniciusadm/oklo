package com.archieapps.oklo.core.session

data class StrikeBudget(
    val serverBlocked: Int = 0,
    val localStrikes: Int = 0,
) {
    val remainingShown: Int
        get() = (LOCKOUT - serverBlocked).coerceAtLeast(0)

    val canAttempt: Boolean
        get() = serverBlocked < SERVER_CEILING && localStrikes < LOCAL_CEILING

    val hardStopped: Boolean
        get() = !canAttempt

    val permanentlyBlocked: Boolean
        get() = serverBlocked >= LOCKOUT

    fun afterRejection(attemptsLeft: Int?): StrikeBudget {
        val reported = attemptsLeft?.let { (LOCKOUT - 1 - it).coerceAtLeast(0) }

        return copy(
            serverBlocked = maxOf(serverBlocked + 1, reported ?: 0),
            localStrikes = localStrikes + 1,
        )
    }

    fun afterSuccess(): StrikeBudget = StrikeBudget(0, 0)

    fun withServerBlocked(value: Int): StrikeBudget = copy(serverBlocked = value)

    companion object {
        const val LOCKOUT = 5
        const val SERVER_CEILING = 3
        const val LOCAL_CEILING = 2
    }
}
