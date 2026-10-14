package com.archieapps.oklo.data.repo

import com.archieapps.oklo.data.db.CardEntity
import com.archieapps.oklo.data.db.encodeChoices
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardState

object CardWrites {
    const val MAX_TARGET_BYTES = 120

    private val TARGET_GRAMMAR = Regex("^(page|anchor):[A-Za-z0-9_-]+$")

    fun isSupportedTarget(target: String): Boolean = TARGET_GRAMMAR.matches(target)

    fun normalizedTarget(raw: String?): String? {
        val target = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (target.toByteArray(Charsets.UTF_8).size > MAX_TARGET_BYTES) {
            throw CardValidationException("O vínculo com a aula é longo demais.")
        }
        if (!isSupportedTarget(target)) {
            throw CardValidationException("O vínculo com a aula é inválido. Use um alvo com letras, números, _ ou -.")
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

    fun relink(cards: List<CardEntity>, links: Map<String, String>, keepTargets: Set<String>?, now: Long): List<CardEntity> =
        cards.filter { it.deletedAt == null }.mapNotNull { card ->
            val linked = links[card.id]?.let { runCatching { normalizedTarget(it) }.getOrNull() }
            val current = card.lessonTarget
            val target = when {
                linked != null -> linked
                keepTargets != null && current != null && current !in keepTargets -> null
                else -> current
            }
            if (target == current) {
                null
            } else {
                card.copy(lessonTarget = target, updatedAt = Stamp.next(card.updatedAt, now), dirty = true)
            }
        }
}
