package com.archieapps.oklo.ui.common

import com.archieapps.oklo.core.fsrs.IntervalFormat
import com.archieapps.oklo.data.model.Card
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardState
import com.archieapps.oklo.data.model.CardType

fun answerText(content: CardContent): String = when (content.type) {
    CardType.Basic, CardType.Qa -> content.back.orEmpty()
    CardType.TrueFalse -> when (content.answer) {
        true -> "Verdadeiro"
        false -> "Falso"
        null -> "—"
    }
    CardType.MultipleChoice -> content.choices?.firstOrNull { it.correct }?.text ?: "—"
}

fun frontLabel(type: CardType): String = when (type) {
    CardType.Basic -> "Frente"
    CardType.Qa -> "Pergunta"
    CardType.TrueFalse -> "Afirmação"
    CardType.MultipleChoice -> "Enunciado"
}

fun backLabel(type: CardType): String = when (type) {
    CardType.Basic -> "Verso"
    CardType.Qa -> "Resposta"
    CardType.TrueFalse -> "Resposta correta"
    CardType.MultipleChoice -> "Alternativa correta"
}

fun stateLabel(card: Card, now: Long): String {
    if (card.suspended) return "Suspenso"
    val due = card.dueAt
    return when (card.state) {
        CardState.New -> "Novo"
        CardState.Learning, CardState.Relearning -> if (due == null || due <= now) "Aprendendo · agora" else "Aprendendo · em ${IntervalFormat.short(due - now)}"
        CardState.Review -> if (due == null || due <= now) "Revisão · pendente" else "Revisão · em ${IntervalFormat.short(due - now)}"
    }
}
