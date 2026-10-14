package com.archieapps.oklo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.archieapps.oklo.R
import com.archieapps.oklo.data.model.CardContent
import com.archieapps.oklo.data.model.CardType
import com.archieapps.oklo.data.model.Choice
import com.archieapps.oklo.ui.common.backLabel
import com.archieapps.oklo.ui.common.frontLabel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

fun CardContent.withType(type: CardType): CardContent = when (type) {
    CardType.Basic, CardType.Qa -> copy(type = type)
    CardType.TrueFalse -> copy(type = type)
    CardType.MultipleChoice -> copy(
        type = type,
        choices = choices?.takeIf { it.size >= 2 } ?: listOf(Choice("", true), Choice("", false)),
    )
}

@Composable
fun CardContentEditor(
    content: CardContent,
    onChange: (CardContent) -> Unit,
    modifier: Modifier = Modifier,
    allowTypeChange: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        if (allowTypeChange) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Eyebrow("Formato")
                val types = CardType.entries
                ChoiceRow(
                    options = types.map { it.label },
                    selectedIndex = types.indexOf(content.type),
                    onSelect = { onChange(content.withType(types[it])) },
                    modifier = Modifier.selectableGroup(),
                )
            }
        }

        OkloTextField(
            value = content.front,
            onValueChange = { onChange(content.copy(front = it)) },
            label = frontLabel(content.type),
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        when (content.type) {
            CardType.Basic, CardType.Qa -> OkloTextField(
                value = content.back.orEmpty(),
                onValueChange = { onChange(content.copy(back = it)) },
                label = backLabel(content.type),
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            CardType.TrueFalse -> Column {
                Eyebrow("Resposta correta")
                Column(Modifier.selectableGroup()) {
                    listOf(true to "Verdadeiro", false to "Falso").forEach { (value, label) ->
                        RadioRow(
                            label = label,
                            selected = content.answer == value,
                            onClick = { onChange(content.copy(answer = value)) },
                            horizontalPadding = 0.dp,
                        )
                    }
                }
            }

            CardType.MultipleChoice -> {
                val choices = content.choices.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Eyebrow("Alternativas (marque a correta)")
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        choices.forEachIndexed { index, choice ->
                            Row(verticalAlignment = Alignment.Bottom) {
                                Box(
                                    Modifier
                                        .size(width = 40.dp, height = 50.dp)
                                        .selectable(selected = choice.correct, role = Role.RadioButton) {
                                            onChange(content.copy(choices = choices.mapIndexed { i, c -> c.copy(correct = i == index) }))
                                        },
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    RadioMark(selected = choice.correct)
                                }
                                OkloTextField(
                                    value = choice.text,
                                    onValueChange = { text ->
                                        onChange(content.copy(choices = choices.mapIndexed { i, c -> if (i == index) c.copy(text = text) else c }))
                                    },
                                    label = "Alternativa ${'A' + index}" + if (choice.correct) " (correta)" else "",
                                    modifier = Modifier.weight(1f),
                                )
                                if (choices.size > 2) {
                                    IconAction(
                                        icon = R.drawable.ic_close,
                                        contentDescription = "Remover alternativa ${'A' + index}",
                                        onClick = {
                                            val remaining = choices.filterIndexed { i, _ -> i != index }
                                            val fixed = if (remaining.none { it.correct }) remaining.mapIndexed { i, c -> c.copy(correct = i == 0) } else remaining
                                            onChange(content.copy(choices = fixed))
                                        },
                                        tint = okloPalette.muted,
                                    )
                                }
                            }
                        }
                    }
                    if (choices.size < 4) {
                        SecondaryButton(
                            "Adicionar alternativa",
                            onClick = { onChange(content.copy(choices = choices + Choice("", false))) },
                            icon = R.drawable.ic_plus,
                        )
                    }
                }
            }
        }

        OkloTextField(
            value = content.explanation.orEmpty(),
            onValueChange = { onChange(content.copy(explanation = it)) },
            label = "Explicação (opcional)",
            supporting = "Aparece depois da resposta durante o estudo.",
            minLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
