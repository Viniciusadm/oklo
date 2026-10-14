package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.db.CardEntity
import com.archieapps.oklo.data.db.encodeChoices
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardState

object CardWrites {
    const val MAX_TARGET_BYTES = 120

    fun normalizedTarget(raw: String?): String? {
        val target = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (target.toByteArray(Charsets.UTF_8).size > MAX_TARGET_BYTES) {
            throw CardValidationException("O vínculo com a aula é longo demais.")
        }
        return target
    }

    fun fresh(id: String, deckId: String, raw: CardContent, lessonTarget: String?, position: Int, now: Long): CardEntity {
        val content = raw.normalized()
        return CardEntity(
            id = id,
            deckId = deckId,
            type = content.type.wire,
            front = content.front,
            back = content.back,
            explanation = content.explanation,
            answer = content.answer,
            choicesJson = encodeChoices(content.choices),
            state = CardState.New.wire,
            step = null,
            stability = null,
            difficulty = null,
            dueAt = null,
            lastReviewAt = null,
            reps = 0,
            lapses = 0,
            suspended = false,
            position = position,
            createdAt = now,
            updatedAt = now,
            deletedAt = null,
            lessonTarget = normalizedTarget(lessonTarget),
            dirty = true,
        )
    }

    fun edit(current: CardEntity, raw: CardContent, lessonTarget: String?, now: Long): CardEntity {
        val content = raw.normalized()
        return current.copy(
            type = content.type.wire,
            front = content.front,
            back = content.back,
            explanation = content.explanation,
            answer = content.answer,
            choicesJson = encodeChoices(content.choices),
            lessonTarget = normalizedTarget(lessonTarget),
            updatedAt = Stamp.next(current.updatedAt, now),
            dirty = true,
        )
    }
}
