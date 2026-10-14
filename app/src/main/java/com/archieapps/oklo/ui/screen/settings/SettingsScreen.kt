package com.archieapps.oklo.ui.screen.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.BuildConfig
import com.archieapps.oklo.R
import com.archieapps.oklo.core.fsrs.FsrsParameters
import com.archieapps.oklo.data.model.AiModel
import com.archieapps.oklo.data.model.LibrarySort
import com.archieapps.oklo.data.model.StudySettings
import com.archieapps.oklo.data.prefs.ThemeMode
import com.archieapps.oklo.ui.common.PtBr
import com.archieapps.oklo.ui.common.shortDateTime
import com.archieapps.oklo.ui.components.CodePromptHost
import com.archieapps.oklo.ui.components.ConfirmDialog
import com.archieapps.oklo.ui.components.Eyebrow
import com.archieapps.oklo.ui.components.InlineProgress
import com.archieapps.oklo.ui.components.ListGroup
import com.archieapps.oklo.ui.components.ListRow
import com.archieapps.oklo.ui.components.Loading
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.OkloSlider
import com.archieapps.oklo.ui.components.OkloTextField
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.RadioRow
import com.archieapps.oklo.ui.components.SecondaryButton
import com.archieapps.oklo.ui.components.SectionCard
import com.archieapps.oklo.ui.components.ToggleRow
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.components.plural
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.Routes
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.NumeralStyle
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import java.util.Locale

enum class SettingsSection(val key: String, val title: String, val description: String) {
    Appearance("appearance", "Aparência", "Tema claro, escuro ou automático; tamanho do texto."),
    Study("study", "Estudo", "Limites de cards novos e revisões por dia; comportamento da sessão."),
    Algorithm("algorithm", "Algoritmo", "FSRS-5: retenção desejada, passos e intervalo máximo."),
    Ai("ai", "Inteligência artificial", "Modelo DeepSeek, parâmetros de geração e estado da conexão."),
    Library("library", "Biblioteca", "Ordenação padrão e comportamento dos decks."),
    Account("account", "Conta e backup", "Entrar, sincronizar agora e restaurar do servidor."),
    Data("data", "Dados", "Backup, restauração e exportação."),
    About("about", "Sobre", "Versão do aplicativo e informações do projeto.");

    companion object {
        fun of(key: String?): SettingsSection = entries.firstOrNull { it.key == key } ?: Appearance
    }
}

@DrawableRes
private fun SettingsSection.icon(): Int = when (this) {
    SettingsSection.Appearance -> R.drawable.ic_visibility
    SettingsSection.Study -> R.drawable.ic_school
    SettingsSection.Algorithm -> R.drawable.ic_bar_chart
    SettingsSection.Ai -> R.drawable.ic_sparkle
    SettingsSection.Library -> R.drawable.ic_library
    SettingsSection.Account -> R.drawable.ic_sync
    SettingsSection.Data -> R.drawable.ic_folder
    SettingsSection.About -> R.drawable.ic_layers
}

@Composable
fun SettingsScreen(navigator: AppNavigator) {
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Configurações", onBack = navigator::back)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.sm, bottom = Spacing.screenBottom),
        ) {
            ListGroup {
                val sections = SettingsSection.entries
                sections.forEachIndexed { index, section ->
                    ListRow(
                        title = section.title,
                        subtitle = section.description,
                        icon = section.icon(),
                        chevron = true,
                        divider = index < sections.lastIndex,
                        startPadding = Spacing.lg,
                        endPadding = Spacing.md,
                        onClick = { navigator.to(Routes.settingsSection(section)) },
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSectionScreen(navigator: AppNavigator, section: SettingsSection) {
    val viewModel = appViewModel(key = "settings-${section.key}") { SettingsViewModel(it) }
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sectionDirty by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val back = {
        if (sectionDirty) confirmLeave = true else navigator.back()
    }
    BackHandler { back() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(title = section.title, onBack = back)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = Spacing.screenH)
                    .padding(top = Spacing.sm, bottom = Spacing.screenBottom),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                when (section) {
                    SettingsSection.Appearance -> AppearanceSection(viewModel)
                    SettingsSection.Study -> StudySection(viewModel) { sectionDirty = it }
                    SettingsSection.Algorithm -> AlgorithmSection(viewModel) { sectionDirty = it }
                    SettingsSection.Ai -> AiSection(viewModel)
                    SettingsSection.Library -> LibrarySection(viewModel)
                    SettingsSection.Account -> AccountSection(viewModel, navigator)
                    SettingsSection.Data -> DataSection(navigator)
                    SettingsSection.About -> AboutSection()
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().imePadding())
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "Descartar alterações?",
            message = "Você mudou valores nesta seção e ainda não salvou.",
            confirmLabel = "Descartar",
            dismissLabel = "Continuar editando",
            destructive = true,
            onConfirm = navigator::back,
            onDismiss = { confirmLeave = false },
        )
    }
    CodePromptHost(viewModel.codePrompt)
}

@Composable
private fun AppearanceSection(viewModel: SettingsViewModel) {
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val current = appearance ?: return Loading()
    SectionCard {
        Eyebrow("Tema")
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                RadioRow(
                    label = mode.label,
                    selected = current.theme == mode,
                    onClick = { viewModel.setTheme(mode) },
                    supporting = if (mode == ThemeMode.System) "Segue o tema do aparelho" else null,
                    horizontalPadding = 0.dp,
                )
            }
        }
    }
    SectionCard {
        Eyebrow("Tamanho do texto")
        Column(Modifier.selectableGroup()) {
            TextScales.forEach { (scale, label) ->
                RadioRow(label = label, selected = current.textScale == scale, onClick = { viewModel.setTextScale(scale) }, horizontalPadding = 0.dp)
            }
        }
        Text("Exemplo: Qual é a diferença entre uma rota web e uma rota API?", style = MaterialTheme.typography.bodyLarge)
        Text("Tema e tamanho do texto ficam só neste aparelho.", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
    }
}

private val TextScales = listOf(0.85f to "Pequeno", 1f to "Padrão", 1.15f to "Grande", 1.3f to "Muito grande")

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, error: String?, supporting: String? = null) {
    OkloTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == ',' || ch == ' ' || ch == '.' }) },
        label = label,
        singleLine = true,
        error = error,
        supporting = supporting,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun StudySection(viewModel: SettingsViewModel, onDirty: (Boolean) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return Loading()
    var newPerDay by rememberSaveable(current.newPerDay) { mutableStateOf(current.newPerDay.toString()) }
    var reviewsPerDay by rememberSaveable(current.reviewsPerDay) { mutableStateOf(current.reviewsPerDay.toString()) }
    val newValue = newPerDay.trim().toIntOrNull()
    val reviewValue = reviewsPerDay.trim().toIntOrNull()
    val newError = if (newValue == null || newValue !in 0..9999) "Use um número de 0 a 9999." else null
    val reviewError = if (reviewValue == null || reviewValue !in 0..99999) "Use um número de 0 a 99999." else null
    val dirty = newPerDay != current.newPerDay.toString() || reviewsPerDay != current.reviewsPerDay.toString()
    LaunchedEffect(dirty) { onDirty(dirty) }

    SectionCard {
        Eyebrow("Limites diários")
        NumberField("Cards novos por dia", newPerDay, { newPerDay = it }, newError, "Quantos cards nunca vistos entram por dia.")
        NumberField("Revisões por dia", reviewsPerDay, { reviewsPerDay = it }, reviewError, "Teto de cards em revisão por dia.")
        PrimaryButton("Salvar", onClick = {
            viewModel.update { it.copy(newPerDay = newValue!!, reviewsPerDay = reviewValue!!) }
        }, enabled = dirty && newError == null && reviewError == null)
    }
    SectionCard {
        Eyebrow("Comportamento da sessão")
        Text(
            "• O dia de estudo começa à meia-noite no fuso do aparelho.\n" +
                "• Estudar um deck inclui os subdecks dele.\n" +
                "• Cards em aprendizado voltam na mesma sessão quando vencem em até 20 minutos.\n" +
                "• Os limites diários valem para todos os decks juntos.\n" +
                "• Em V/F e múltipla escolha, acertar não muda a avaliação: você escolhe como foi lembrar.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun parseSteps(text: String): List<Int>? {
    val parts = text.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    val values = parts.map { it.toIntOrNull() ?: return null }
    if (values.size > 10 || values.any { it !in 1..1440 }) return null
    return values
}

@Composable
private fun AlgorithmSection(viewModel: SettingsViewModel, onDirty: (Boolean) -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return Loading()
    var retention by rememberSaveable(current.desiredRetention) { mutableFloatStateOf(current.desiredRetention.toFloat()) }
    var learning by rememberSaveable(current.learningStepsMinutes) { mutableStateOf(current.learningStepsMinutes.joinToString(", ")) }
    var relearning by rememberSaveable(current.relearningStepsMinutes) { mutableStateOf(current.relearningStepsMinutes.joinToString(", ")) }
    var maxInterval by rememberSaveable(current.maximumIntervalDays) { mutableStateOf(current.maximumIntervalDays.toString()) }
    var fuzz by rememberSaveable(current.enableFuzz) { mutableStateOf(current.enableFuzz) }
    val learningSteps = parseSteps(learning)
    val relearningSteps = parseSteps(relearning)
    val maxValue = maxInterval.trim().toIntOrNull()
    val retentionRounded = Math.round(retention * 100) / 100.0
    val maxError = if (maxValue == null || maxValue !in 1..36500) "Use de 1 a 36500 dias." else null
    val stepsHint = "Minutos separados por vírgula (até 10 passos, de 1 a 1440). Pode ficar vazio."
    val dirty = retentionRounded != current.desiredRetention ||
        learning != current.learningStepsMinutes.joinToString(", ") ||
        relearning != current.relearningStepsMinutes.joinToString(", ") ||
        maxInterval != current.maximumIntervalDays.toString() ||
        fuzz != current.enableFuzz
    LaunchedEffect(dirty) { onDirty(dirty) }

    Notice(
        "O agendamento usa o FSRS-5 com os 19 pesos padrão, numa porta do py-fsrs 4.x validada contra a biblioteca original. Não é o algoritmo SM-2 do Anki.",
        tone = NoticeTone.Info,
        title = "Algoritmo FSRS-5",
    )
    SectionCard {
        Eyebrow("Retenção desejada")
        Text(
            String.format(PtBr, "%.0f%%", retentionRounded * 100),
            style = NumeralStyle,
        )
        OkloSlider(value = retention, onValueChange = { retention = it }, valueRange = 0.70f..0.99f, steps = 28)
        Text(
            "Probabilidade de lembrar no dia da revisão. Mais alta = revisões mais frequentes.",
            style = MaterialTheme.typography.bodySmall,
            color = okloPalette.muted,
        )
        OkloTextField(
            value = learning,
            onValueChange = { learning = it },
            label = "Passos de aprendizado (min)",
            error = if (learningSteps == null) "Valor inválido. $stepsHint" else null,
            supporting = stepsHint,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OkloTextField(
            value = relearning,
            onValueChange = { relearning = it },
            label = "Passos de reaprendizado (min)",
            error = if (relearningSteps == null) "Valor inválido. $stepsHint" else null,
            supporting = stepsHint,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NumberField("Intervalo máximo (dias)", maxInterval, { maxInterval = it.filter(Char::isDigit) }, maxError)
        ToggleRow(
            label = "Variação aleatória (fuzz)",
            checked = fuzz,
            onCheckedChange = { fuzz = it },
            supporting = "Espalha um pouco as datas para não acumular revisões no mesmo dia.",
            horizontalPadding = 0.dp,
        )
        PrimaryButton(
            "Salvar",
            onClick = {
                viewModel.update {
                    it.copy(
                        desiredRetention = retentionRounded,
                        learningStepsMinutes = learningSteps!!,
                        relearningStepsMinutes = relearningSteps!!,
                        maximumIntervalDays = maxValue!!,
                        enableFuzz = fuzz,
                    )
                }
            },
            enabled = dirty && learningSteps != null && relearningSteps != null && maxError == null,
        )
        Text("As mudanças valem para as próximas avaliações; cards já agendados mantêm a data atual.", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
    }
    SectionCard {
        Eyebrow("Pesos do modelo (somente leitura)")
        Text(
            FsrsParameters.DEFAULT_WEIGHTS.mapIndexed { index, w -> "w$index = ${String.format(Locale.ROOT, "%.4f", w)}" }.joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
        )
        Text("A otimização dos pesos a partir do seu histórico não está implementada.", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
    }
}

@Composable
private fun AiSection(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.aiStatus.collectAsStateWithLifecycle()
    val current = settings ?: return Loading()
    LaunchedEffect(Unit) { viewModel.loadAiStatus() }
    SectionCard {
        Eyebrow("Estado da conexão")
        when (val value = status) {
            Remote.Idle, Remote.Loading -> InlineProgress("Consultando o servidor…")
            is Remote.Failed -> Text(value.message, style = MaterialTheme.typography.bodyMedium, color = okloPalette.danger)
            is Remote.Ready -> {
                StatusLine(
                    configured = value.value.configured,
                    label = if (value.value.configured) "IA configurada no servidor" else "IA não configurada no servidor",
                )
                value.value.models.forEach { (key, name) ->
                    Text("${AiModel.of(key).label}: $name", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
                }
            }
        }
        SecondaryButton("Consultar de novo", onClick = viewModel::loadAiStatus, modifier = Modifier.fillMaxWidth())
    }
    SectionCard {
        Eyebrow("Modelo DeepSeek")
        Column(Modifier.selectableGroup()) {
            AiModel.entries.forEach { model ->
                RadioRow(model.label, current.aiModel == model, onClick = { viewModel.update { it.copy(aiModel = model) } }, horizontalPadding = 0.dp)
            }
        }
    }
    SectionCard {
        Eyebrow("Parâmetros de geração")
        Text(
            "Quantidade, dificuldade e tipos são escolhidos a cada geração, na tela Criar com IA. " +
                "O texto pode ter até 12.000 caracteres e cada geração traz no máximo 40 cards. " +
                "Gerar exige internet, login e o código do dia.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LibrarySection(viewModel: SettingsViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current: StudySettings = settings ?: return Loading()
    SectionCard {
        Eyebrow("Ordenação padrão")
        Column(Modifier.selectableGroup()) {
            LibrarySort.entries.forEach { sort ->
                RadioRow("Por " + sort.label.lowercase(), current.librarySort == sort, onClick = { viewModel.update { it.copy(librarySort = sort) } }, horizontalPadding = 0.dp)
            }
        }
    }
    SectionCard {
        Eyebrow("Comportamento dos decks")
        Text(
            "• Os decks aceitam subdecks em qualquer profundidade.\n" +
                "• Os números de um deck somam os subdecks.\n" +
                "• Excluir um deck exclui também os subdecks e os cards deles, sempre com confirmação.\n" +
                "• Duplicar copia o deck, os subdecks e os cards, com o progresso zerado.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun AccountSection(viewModel: SettingsViewModel, navigator: AppNavigator) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val meta by viewModel.syncMeta.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val server by viewModel.serverStatus.collectAsStateWithLifecycle()
    var restoreStep by remember { mutableIntStateOf(0) }
    var confirmLogout by remember { mutableStateOf(false) }
    val currentSession = session ?: return Loading()
    val syncMeta = meta ?: return Loading()

    LaunchedEffect(currentSession.signedIn) { if (currentSession.signedIn) viewModel.loadServerStatus() }

    if (!currentSession.signedIn) {
        SectionCard {
            Text("Você não está conectado", style = MaterialTheme.typography.titleMedium)
            Text(
                "O app funciona inteiro sem conta. Entrar ativa o backup (sincronização entre aparelhos) e a criação de cards com IA.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrimaryButton("Entrar", onClick = { navigator.to(Routes.CONNECT) })
        }
        pending?.takeIf { it > 0 }?.let {
            Text("${plural(it, "alteração local ainda não enviada", "alterações locais ainda não enviadas")}.", style = MaterialTheme.typography.bodySmall, color = okloPalette.muted)
        }
        return
    }

    SectionCard {
        Eyebrow("Conta")
        Text(currentSession.name ?: "Conectado", style = MaterialTheme.typography.titleMedium)
        currentSession.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted) }
        SecondaryButton("Sair da conta", onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth())
    }

    SectionCard {
        Eyebrow("Sincronização")
        Text(
            syncMeta.lastSyncAt?.let { "Última sincronização: ${shortDateTime(it)}" } ?: "Ainda não sincronizado neste aparelho.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            pending?.let { if (it == 0) "Nenhuma alteração pendente." else "${plural(it, "alteração pendente", "alterações pendentes")} de envio." } ?: "",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (syncMeta.rejectedCount > 0) {
            Text(
                "${plural(syncMeta.rejectedCount, "registro recusado", "registros recusados")} pelo servidor até agora" +
                    (syncMeta.lastRejectedReason?.let { ". Último motivo: $it" } ?: "."),
                style = MaterialTheme.typography.bodySmall,
                color = okloPalette.warning,
            )
        }
        syncMeta.lastError?.let {
            Text("Última falha: $it", style = MaterialTheme.typography.bodySmall, color = okloPalette.danger)
        }
        PrimaryButton("Sincronizar agora", onClick = viewModel::syncNow, loading = sync.running)
        Text(
            "O app também sincroniza ao abrir, ao fim de cada sessão de estudo, depois de salvar cards da IA e periodicamente quando há internet.",
            style = MaterialTheme.typography.bodySmall,
            color = okloPalette.muted,
        )
    }

    SectionCard {
        Eyebrow("No servidor")
        when (val value = server) {
            Remote.Idle, Remote.Loading -> InlineProgress("Consultando…")
            is Remote.Failed -> Text(value.message, style = MaterialTheme.typography.bodyMedium, color = okloPalette.danger)
            is Remote.Ready -> {
                val body = value.value
                Text(
                    "${plural(body.counts.decks, "deck", "decks")} · ${plural(body.counts.cards, "card", "cards")} · ${plural(body.counts.reviews, "avaliação", "avaliações")}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    body.lastSyncAt?.let { "Último envio recebido: $it" } ?: "O servidor ainda não recebeu nenhum envio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = okloPalette.muted,
                )
            }
        }
        SecondaryButton("Atualizar", onClick = viewModel::loadServerStatus, modifier = Modifier.fillMaxWidth())
    }

    SectionCard {
        Eyebrow("Restaurar do servidor")
        Text(
            "Apaga todos os dados deste aparelho e baixa a cópia completa do servidor. Use quando os dados locais estiverem errados. Exige o código do dia.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SecondaryButton(
            if (sync.restoring) "Restaurando…" else "Restaurar do servidor",
            onClick = { restoreStep = 1 },
            enabled = !sync.restoring && !sync.running,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (restoreStep == 1) {
        ConfirmDialog(
            title = "Restaurar do servidor?",
            message = buildString {
                append("Todos os decks, cards, avaliações e configurações deste aparelho serão apagados e substituídos pelos do servidor.")
                pending?.takeIf { it > 0 }?.let { append(" Atenção: ${plural(it, "alteração local ainda não foi enviada e será perdida", "alterações locais ainda não foram enviadas e serão perdidas")}.") }
            },
            confirmLabel = "Continuar",
            destructive = true,
            onConfirm = { restoreStep = 2 },
            onDismiss = { if (restoreStep == 1) restoreStep = 0 },
        )
    }
    if (restoreStep == 2) {
        ConfirmDialog(
            title = "Tem certeza?",
            message = "Esta é a confirmação final. Os dados locais serão apagados e não há como desfazer.",
            confirmLabel = "Apagar dados locais e restaurar",
            destructive = true,
            onConfirm = {
                restoreStep = 0
                viewModel.restore()
            },
            onDismiss = { if (restoreStep == 2) restoreStep = 0 },
        )
    }
    if (confirmLogout) {
        ConfirmDialog(
            title = "Sair da conta?",
            message = "Os dados continuam neste aparelho. Alterações feitas sem login só sobem quando você entrar de novo.",
            confirmLabel = "Sair",
            onConfirm = viewModel::logout,
            onDismiss = { confirmLogout = false },
        )
    }
}

@Composable
private fun DataSection(navigator: AppNavigator) {
    SectionCard {
        Eyebrow("Backup e restauração")
        Text(
            "O backup é a sincronização com o servidor. Para enviar, sincronizar ou restaurar, use Conta e backup.",
            style = MaterialTheme.typography.bodyMedium,
        )
        SecondaryButton(
            "Abrir Conta e backup",
            onClick = { navigator.to(Routes.settingsSection(SettingsSection.Account)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    SectionCard {
        Eyebrow("Exportação")
        Text("Não implementado nesta versão.", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exportar decks para arquivo (por exemplo, para o Anki) ainda não existe. Os dados ficam no aparelho e no backup do servidor.",
            style = MaterialTheme.typography.bodySmall,
            color = okloPalette.muted,
        )
    }
}

@Composable
private fun ColumnScope.AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.md))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AboutSection() {
    SectionCard {
        Text("Oklo", style = MaterialTheme.typography.headlineSmall)
        Text("Flashcards com revisão espaçada e criação de cards com IA.", style = MaterialTheme.typography.bodyMedium, color = okloPalette.muted)
        AboutRow("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        AboutRow("Algoritmo", "FSRS-5")
        AboutRow("IA", "DeepSeek, via servidor")
        AboutRow("Servidor", BuildConfig.BASE_URL)
    }
    SectionCard {
        Text(
            "Os dados ficam primeiro no aparelho, como no Anki: tudo funciona offline. O servidor guarda o backup e gera rascunhos de cards; nada é gravado pela IA sem a sua revisão.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StatusLine(configured: Boolean, label: String) {
    val color = if (configured) okloPalette.success else okloPalette.danger
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(if (configured) R.drawable.ic_check else R.drawable.ic_close),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color)
    }
}
