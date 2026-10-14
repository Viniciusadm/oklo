package com.archieapps.oklo.ui.screen.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.ui.components.EmptyState
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.InlineProgress
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import com.archieapps.ribalta.lesson.CompileResult
import com.archieapps.ribalta.lesson.LessonTarget
import com.archieapps.ribalta.lesson.LessonView
import com.archieapps.ribalta.lesson.compileLesson
import com.archieapps.ribalta.lesson.parse
import com.archieapps.ribalta.lesson.rememberLessonViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LessonScreen(navigator: AppNavigator, deckId: String, mode: LessonMode, target: String?, awaitingSync: Boolean = false) {
    val viewModel = appViewModel(key = "lesson-$deckId-${mode.name}-$target-$awaitingSync") {
        LessonViewModel(it.lessons, it.preferences, deckId, mode, target, awaitingSync = awaitingSync)
    }
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    when (val state = ui) {
        LessonUi.Loading -> Loading()
        LessonUi.Missing -> Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "Aula", onBack = navigator::back)
            EmptyState(title = "Este deck não tem aula", message = "Ela pode ter sido removida em outro aparelho.") {
                SecondaryButton("Voltar", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
            }
        }
        LessonUi.Syncing -> Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            TopBar(title = "Aula", onBack = navigator::back)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                InlineProgress("Sincronizando a aula…")
                Text(
                    "A aula gerada aparece aqui assim que chegar ao aparelho.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = okloPalette.muted,
                )
                SecondaryButton("Voltar", onClick = navigator::back, modifier = Modifier.fillMaxWidth())
            }
        }
        is LessonUi.Ready -> LessonContent(navigator, viewModel, state)
    }
}

@Composable
private fun LessonContent(navigator: AppNavigator, viewModel: LessonViewModel, state: LessonUi.Ready) {
    val scope = rememberCoroutineScope()
    val lesson = state.lesson
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        TopBar(title = lessonTitle(lesson.source, "Aula"), onBack = navigator::back)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val widthPx = constraints.maxWidth
            val compiled by produceState<Result<CompileResult>?>(null, lesson.source, widthPx) {
                value = withContext(Dispatchers.Default) { runCatching { compileLesson(lesson.source, widthPx) } }
            }
            val result = compiled
            when {
                result == null -> Loading()
                result.isFailure -> {
                    LaunchedEffect(lesson.version) { viewModel.onRenderFailed() }
                    Warning("Não foi possível exibir esta aula.")
                }
                else -> key(result) { LessonBody(viewModel, state, result.getOrThrow()) }
            }
        }
        if (state.mode == LessonMode.Gate) {
            GateBar(state) {
                scope.launch {
                    if (viewModel.complete()) navigator.replace(Routes.study(lesson.deckId))
                }
            }
        }
    }
}

@Composable
private fun LessonBody(viewModel: LessonViewModel, state: LessonUi.Ready, result: CompileResult) {
    val viewState = rememberLessonViewState()
    LaunchedEffect(viewState) {
        snapshotFlow { viewState.reachedEnd }.collect(viewModel::onReachedEnd)
    }
    LaunchedEffect(result, state.target) {
        state.target?.let(LessonTarget::parse)?.let { viewState.scrollTo(it) }
    }
    Column(Modifier.fillMaxSize()) {
        if (state.invalid) Warning("Esta aula tem erros e pode aparecer incompleta.")
        LessonView(result.document, viewState, Modifier.weight(1f).fillMaxWidth())
    }
}

@Composable
private fun Warning(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = okloPalette.warning,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
    )
}

@Composable
private fun GateBar(state: LessonUi.Ready, onComplete: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Hairline()
        Column(Modifier.padding(horizontal = Spacing.screenH, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PrimaryButton(
                if (state.invalid) "Estudar mesmo assim" else "Concluir",
                onClick = onComplete,
                enabled = state.canComplete,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!state.canComplete) {
                Text("Role até o fim da aula para concluir.", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
            }
        }
    }
}
