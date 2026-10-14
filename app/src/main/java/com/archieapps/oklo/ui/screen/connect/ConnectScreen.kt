package com.archieapps.oklo.ui.screen.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.BuildConfig
import com.archieapps.oklo.ui.components.Notice
import com.archieapps.oklo.ui.components.NoticeTone
import com.archieapps.oklo.ui.components.OkloTextField
import com.archieapps.oklo.ui.components.PrimaryButton
import com.archieapps.oklo.ui.components.TopBar
import com.archieapps.oklo.ui.nav.AppNavigator
import com.archieapps.oklo.ui.nav.appViewModel
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette

@Composable
fun ConnectScreen(navigator: AppNavigator) {
    val viewModel = appViewModel { ConnectViewModel(it) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.done) {
        if (state.done) navigator.back()
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(title = "Entrar", onBack = navigator::back)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.screenH)
                .padding(top = Spacing.sm, bottom = Spacing.screenBottom),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                "A conta serve para o backup dos seus decks e para criar cards com IA. Sem login, tudo continua funcionando neste aparelho.",
                style = MaterialTheme.typography.bodyMedium,
                color = okloPalette.muted,
            )
            OkloTextField(
                value = state.email,
                onValueChange = viewModel::setEmail,
                label = "E-mail",
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OkloTextField(
                value = state.password,
                onValueChange = viewModel::setPassword,
                label = "Senha",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Notice(it, tone = NoticeTone.Error) }
            PrimaryButton("Entrar", onClick = viewModel::submit, loading = state.busy)
            Text("Servidor: ${BuildConfig.BASE_URL}", style = MaterialTheme.typography.bodySmall, color = okloPalette.faint)
        }
    }
}
