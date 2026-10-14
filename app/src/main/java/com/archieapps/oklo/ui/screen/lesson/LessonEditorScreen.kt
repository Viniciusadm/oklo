package com.archieapps.oklo.ui.screen.lesson

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.SegmentedTabs
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Radii
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import com.archieapps.ribalta.ir.Diagnostic
import com.archieapps.ribalta.lesson.CompileResult
import com.archieapps.ribalta.lesson.LessonView
import com.archieapps.ribalta.lesson.compileLesson
import com.archieapps.ribalta.lesson.rememberLessonViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val tabs = listOf("Texto", "Prévia")

@Composable
fun LessonEditorScreen(navigator: AppNavigator, deckId: String) {
    val viewModel = appViewModel(key = "lesson-editor-$deckId") {
        LessonEditorViewModel(it.lessons, it.sync::requestSync, deckId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    var loadedSource by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(state.finished) {
        if (state.finished) navigator.back()
    }
    LaunchedEffect(state.loaded) {
        if (state.loaded && !loadedSource) {
            field = TextFieldValue(state.source)
            loadedSource = true
        }
    }

    val guarded = state.dirty && !state.finished
    BackHandler(enabled = guarded) { confirmLeave = true }
    val leave = { if (guarded) confirmLeave = true else navigator.back() }

    if (!state.loaded || !loadedSource) return Loading()

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        TopBar(title = if (state.isNew) "Escrever aula" else "Editar aula", onBack = leave)
        SegmentedTabs(tabs, tab, { tab = it }, Modifier.padding(horizontal = Spacing.screenH))
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                SourcePane(
                    field = field,
                    onChange = {
                        field = it
                        viewModel.onSourceChange(it.text)
                    },
                    state = state,
                    focus = focus,
                    onDiagnostic = { diagnostic ->
                        field = field.copy(selection = TextRange(lineStart(field.text, diagnostic.line)))
                        focus.requestFocus()
                    },
                )
            } else {
                PreviewPane(state.source, state.hasErrors)
            }
        }
        Hairline()
        Column(
            Modifier.padding(horizontal = Spacing.screenH, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (state.saveFailed) {
                Text("Não foi possível salvar a aula.", style = MaterialTheme.typography.bodySmall, color = okloPalette.danger)
            }
            PrimaryButton("Salvar", onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth())
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Descartar alterações?",
            message = "A aula tem alterações que ainda não foram salvas.",
            confirmLabel = "Descartar",
            destructive = true,
            dismissLabel = "Continuar editando",
            onConfirm = {
                confirmLeave = false
                navigator.back()
            },
            onDismiss = { confirmLeave = false },
        )
    }
}

@Composable
private fun SourcePane(
    field: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    state: LessonEditorState,
    focus: FocusRequester,
    onDiagnostic: (Diagnostic) -> Unit,
) {
    val palette = okloPalette
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenH, vertical = Spacing.sm)) {
        BasicTextField(
            value = field,
            onValueChange = onChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp)
                .focusRequester(focus)
                .background(palette.sheet, Radii.control)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, Radii.control)
                .padding(Spacing.md),
        )
        if (state.tooLarge) {
            Text(
                "A aula passou do limite de 200 KB.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
        if (state.diagnostics.isNotEmpty()) {
            Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                state.diagnostics.forEach { diagnostic ->
                    val error = diagnostic.severity == "error"
                    Notice(
                        message = "Linha ${diagnostic.line}: ${diagnostic.message}",
                        tone = if (error) NoticeTone.Error else NoticeTone.Warning,
                        modifier = Modifier.clickable(role = Role.Button) { onDiagnostic(diagnostic) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(source: String, hasErrors: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth
        val compiled by produceState<Result<CompileResult>?>(null, source, widthPx) {
            value = withContext(Dispatchers.Default) { runCatching { compileLesson(source, widthPx) } }
        }
        val result = compiled
        Column(Modifier.fillMaxSize()) {
            if (hasErrors) {
                Text(
                    "O texto tem erros e a prévia pode aparecer incompleta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = okloPalette.warning,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screenH, vertical = Spacing.sm),
                )
            }
            when {
                result == null -> Loading()
                result.isFailure -> Text(
                    "Não foi possível exibir a prévia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = okloPalette.danger,
                    modifier = Modifier.padding(Spacing.screenH),
                )
                else -> LessonView(result.getOrThrow().document, rememberLessonViewState(), Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

private fun lineStart(text: String, line: Int): Int {
    var offset = 0
    repeat((line - 1).coerceAtLeast(0)) {
        val next = text.indexOf('\n', offset)
        if (next < 0) return text.length
        offset = next + 1
    }
    return offset
}
