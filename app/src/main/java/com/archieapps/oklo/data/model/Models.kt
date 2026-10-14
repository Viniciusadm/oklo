package com.archieapps.oklo.data.model

import kotlinx.serialization.Serializable

enum class CardType(val wire: String, val label: String) {
    Basic("basic", "Frente e verso"),
    Qa("qa", "Pergunta e resposta"),
    TrueFalse("true_false", "Verdadeiro ou falso"),
    MultipleChoice("multiple_choice", "Múltipla escolha");

    companion object {
        fun of(wire: String): CardType = entries.firstOrNull { it.wire == wire } ?: Basic
    }
}

enum class CardState(val wire: String, val label: String) {
    New("new", "Novo"),
    Learning("learning", "Aprendendo"),
    Review("review", "Revisão"),
    Relearning("relearning", "Reaprendendo");

    companion object {
        fun of(wire: String): CardState = entries.firstOrNull { it.wire == wire } ?: New
    }
}

@Serializable
data class Choice(val text: String, val correct: Boolean)

data class CardContent(
    val type: CardType,
    val front: String,
    val back: String? = null,
    val explanation: String? = null,
    val answer: Boolean? = null,
    val choices: List<Choice>? = null,
) {
    fun normalized(): CardContent {
        val cleanExplanation = explanation?.trim()?.takeIf { it.isNotEmpty() }
        return when (type) {
            CardType.Basic, CardType.Qa -> copy(
                front = front.trim(),
                back = back?.trim(),
                explanation = cleanExplanation,
                answer = null,
                choices = null,
            )

            CardType.TrueFalse -> copy(
                front = front.trim(),
                back = null,
                explanation = cleanExplanation,
                choices = null,
            )

            CardType.MultipleChoice -> copy(
                front = front.trim(),
                back = null,
                explanation = cleanExplanation,
                answer = null,
                choices = choices?.map { it.copy(text = it.text.trim()) }?.filter { it.text.isNotEmpty() },
            )
        }
    }

    fun validationError(): String? {
        val content = normalized()
        if (content.front.isEmpty()) {
            return when (type) {
                CardType.Basic -> "Preencha a frente do card."
                CardType.Qa, CardType.MultipleChoice -> "Preencha o enunciado."
                CardType.TrueFalse -> "Preencha a afirmação."
            }
        }
        if (content.front.length > MAX_TEXT) return "O texto principal passa de $MAX_TEXT caracteres."
        if ((content.explanation?.length ?: 0) > MAX_TEXT) return "A explicação passa de $MAX_TEXT caracteres."
        return when (type) {
            CardType.Basic, CardType.Qa -> when {
                content.back.isNullOrEmpty() -> if (type == CardType.Basic) "Preencha o verso do card." else "Preencha a resposta."
                content.back.length > MAX_TEXT -> "A resposta passa de $MAX_TEXT caracteres."
                else -> null
            }

            CardType.TrueFalse -> if (content.answer == null) "Escolha se a afirmação é verdadeira ou falsa." else null

            CardType.MultipleChoice -> {
                val list = content.choices.orEmpty()
                when {
                    list.size < 2 -> "Informe de duas a quatro alternativas."
                    list.size > 4 -> "Use no máximo quatro alternativas."
                    list.count { it.correct } != 1 -> "Marque exatamente uma alternativa correta."
                    list.any { it.text.length > MAX_TEXT } -> "Uma alternativa passa de $MAX_TEXT caracteres."
                    else -> null
                }
            }
        }
    }

    companion object {
        const val MAX_TEXT = 5000
    }
}

data class Deck(
    val id: String,
    val parentId: String?,
    val name: String,
    val position: Int,
    val lastStudiedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Card(
    val id: String,
    val deckId: String,
    val content: CardContent,
    val state: CardState,
    val step: Int?,
    val stability: Double?,
    val difficulty: Double?,
    val dueAt: Long?,
    val lastReviewAt: Long?,
    val reps: Int,
    val lapses: Int,
    val suspended: Boolean,
    val position: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lessonTarget: String? = null,
)

data class Lesson(
    val id: String,
    val deckId: String,
    val source: String,
    val version: Int,
    val ribaltaVersion: String,
    val origin: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CardStat(
    val id: String,
    val deckId: String,
    val state: String,
    val dueAt: Long?,
    val suspended: Boolean,
    val createdAt: Long,
)

data class ReviewStat(
    val cardId: String,
    val rating: Int,
    val stateBefore: String,
    val reviewedAt: Long,
    val durationMs: Long,
)

enum class LibrarySort(val wire: String, val label: String) {
    Name("name", "Nome"),
    Recent("recent", "Atividade recente"),
    Cards("cards", "Quantidade de cards");

    companion object {
        fun of(wire: String): LibrarySort = entries.firstOrNull { it.wire == wire } ?: Name
    }
}

enum class AiModel(val wire: String, val label: String) {
    Flash("flash", "Flash (rápido)"),
    Pro("pro", "Pro (mais cuidadoso)");

    companion object {
        fun of(wire: String): AiModel = entries.firstOrNull { it.wire == wire } ?: Flash
    }
}

data class StudySettings(
    val newPerDay: Int = 20,
    val reviewsPerDay: Int = 200,
    val desiredRetention: Double = 0.9,
    val learningStepsMinutes: List<Int> = listOf(1, 10),
    val relearningStepsMinutes: List<Int> = listOf(10),
    val maximumIntervalDays: Int = 36500,
    val enableFuzz: Boolean = true,
    val librarySort: LibrarySort = LibrarySort.Name,
    val aiModel: AiModel = AiModel.Flash,
    val updatedAt: Long = 0,
)
