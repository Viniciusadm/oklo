package com.archieapps.oklo.ui.screen.deck

import com.archieapps.oklo.data.repo.LessonRepository
import com.archieapps.oklo.ui.nav.LessonRouteMode
import com.archieapps.oklo.ui.nav.Routes

class StudyEntry(private val lessons: LessonRepository) {
    suspend fun route(deckId: String): String =
        if (lessons.needsGate(deckId)) Routes.lesson(deckId, LessonRouteMode.Gate) else Routes.study(deckId)
}
