package com.archieapps.oklo.ui.screen.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.LocalContainer
import com.archieapps.oklo.R
import com.archieapps.oklo.ui.components.CodePromptController
import com.archieapps.oklo.ui.components.CodePromptHost
import com.archieapps.oklo.ui.components.InlineProgress
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.OkloSheet
import com.archieapps.oklo.ui.components.OkloTextField
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.QuietButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.data.repo.AiRepository

@Composable
fun LessonAiSheet(
    deckId: String,
    hasLesson: Boolean,
    onDismiss: () -> Unit,
    onOpenLesson: () -> Unit,
) {
    val container = LocalContainer.current
    val codePrompt = remember { CodePromptController(container.codeGate) }
    val viewModel = appViewModel(key = "lesson-ai-$deckId") { container ->
        LessonAiViewModel(
            deckId = deckId,
            generator = container.ai,
            model = { container.settings.current().aiModel.wire },
            isOnline = container.connectivity::isOnline,
            syncNow = container.sync::syncIfSignedIn,
            finish = { body -> container.lessonAiFinisher.finish(deckId, body) },
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    viewModel.openCodePrompt = { retry -> codePrompt.open(retry) }

    LaunchedEffect(Unit) { viewModel.prepare(hasLesson) }

    OkloSheet(
        onDismiss = onDismiss,
        title = "Gerar aula com IA",
        subtitle = "A IA monta a aula a partir dos cards do deck.",
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenH, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (state.phase) {
                LessonAiPhase.Form -> {
                    OkloTextField(
                        value = state.text,
                        onValueChange = viewModel::setText,
                        label = "Texto de apoio (opcional)",
                        placeholder = "Cole anotações ou trechos que a aula deve considerar...",
                        minLines = 4,
                        maxLines = 8,
                        supporting = "${state.text.length} de ${AiRepository.MAX_TEXT} caracteres",
                        error = if (state.tooLong) "${state.text.length} de ${AiRepository.MAX_TEXT} caracteres" else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.error?.let { Notice(it, tone = NoticeTone.Error, title = "Não foi possível gerar") }
                    PrimaryButton(
                        "Gerar aula",
                        onClick = viewModel::submit,
                        icon = R.drawable.ic_sparkle,
                        enabled = !state.tooLong,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                LessonAiPhase.ConfirmReplace -> {
                    Notice(
                        "Este deck já tem uma aula. Gerar uma nova substitui a aula atual quando ela ficar pronta.",
                        tone = NoticeTone.Warning,
                        title = "Substituir a aula?",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton("Cancelar", onClick = viewModel::cancelReplace, modifier = Modifier.weight(1f))
                        PrimaryButton("Substituir", onClick = viewModel::confirmReplace, modifier = Modifier.weight(1f))
                    }
                }

                LessonAiPhase.Sending, LessonAiPhase.Polling -> {
                    InlineProgress(if (state.phase == LessonAiPhase.Sending) "Enviando pedido…" else "Gerando aula…")
                    Notice(
                        "Pode levar alguns minutos. Se você sair, a aula chega ao app na próxima sincronização.",
                        tone = NoticeTone.Info,
                    )
                    QuietButton("Fechar e esperar", onClick = onDismiss)
                }

                LessonAiPhase.Done -> {
                    Notice("A aula foi gerada e está sincronizando.", tone = NoticeTone.Success, title = "Aula pronta")
                    state.warnings.forEach { Notice(it, tone = NoticeTone.Warning) }
                    PrimaryButton(
                        "Ver aula",
                        onClick = {
                            onDismiss()
                            onOpenLesson()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    QuietButton("Fechar", onClick = onDismiss)
                }

                LessonAiPhase.Failed -> {
                    Notice(state.error.orEmpty(), tone = NoticeTone.Error, title = "A geração falhou")
                    PrimaryButton("Tentar de novo", onClick = viewModel::retry, icon = R.drawable.ic_sparkle, modifier = Modifier.fillMaxWidth())
                    QuietButton("Fechar", onClick = onDismiss)
                }

                LessonAiPhase.Delayed -> {
                    Notice(
                        "A geração está demorando mais que o normal. Se ela terminar, a aula chega na próxima sincronização.",
                        tone = NoticeTone.Warning,
                    )
                    QuietButton("Fechar", onClick = onDismiss)
                }
            }
        }
    }
    CodePromptHost(codePrompt)
}
