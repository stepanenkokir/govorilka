package com.govorilka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.govorilka.R

private const val SERVER_URL_PLACEHOLDER = "http://127.0.0.1:3000"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = GovorilkaViewModelFactory()),
) {
    SettingsContent(
        serverBaseUrl = viewModel.serverBaseUrl.value,
        appSecret = viewModel.appSecret.value,
        instructions = viewModel.instructions.value,
        onServerBaseUrlChange = viewModel::onServerBaseUrlChange,
        onAppSecretChange = viewModel::onAppSecretChange,
        onInstructionsChange = viewModel::onInstructionsChange,
        onBack = onBack,
    )
}

@Composable
private fun SettingsContent(
    serverBaseUrl: String,
    appSecret: String,
    instructions: String,
    onServerBaseUrlChange: (String) -> Unit,
    onAppSecretChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { BackTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = serverBaseUrl,
                        onValueChange = onServerBaseUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_server_url)) },
                        placeholder = { Text(SERVER_URL_PLACEHOLDER) },
                        supportingText = { Text(stringResource(R.string.settings_server_url_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = appSecret,
                        onValueChange = onAppSecretChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_app_secret)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    )
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = onInstructionsChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_instructions)) },
                        minLines = 4,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsPreview() {
    GovorilkaTheme {
        SettingsContent(
            serverBaseUrl = "",
            appSecret = "",
            instructions = "",
            onServerBaseUrlChange = {},
            onAppSecretChange = {},
            onInstructionsChange = {},
            onBack = {},
        )
    }
}
