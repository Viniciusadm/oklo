package com.archieapps.oklo.data.repo

import com.archieapps.oklo.core.net.LessonJobBody
import com.archieapps.oklo.data.db.LessonDao
import com.archieapps.oklo.data.sync.SyncReport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun interface LessonLinkWriter {
    suspend fun applyLessonLinks(deckId: String, links: Map<String, String>, keepTargets: Set<String>?)
}

class LessonAiFinisher(
    private val syncNow: suspend () -> SyncReport?,
    private val lessons: LessonDao,
    private val cards: LessonLinkWriter,
    private val targetsOf: (String) -> Set<String>,
    private val requestSync: () -> Unit,
    private val scope: CoroutineScope,
    private val compileDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    fun finish(deckId: String, body: LessonJobBody): Job = scope.launch { complete(deckId, body) }

    suspend fun complete(deckId: String, body: LessonJobBody) {
        syncNow()
        val lesson = lessons.getByDeck(deckId)?.takeIf { it.id == body.lessonId && it.deletedAt == null }
        val keepTargets = if (body.clearTargets && lesson != null) {
            withContext(compileDispatcher) { targetsOf(lesson.source) }.takeIf { it.isNotEmpty() }
        } else {
            null
        }
        cards.applyLessonLinks(deckId, body.links.associate { it.cardId to it.target }, keepTargets)
        requestSync()
    }
}
