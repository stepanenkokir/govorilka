package com.govorilka.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.govorilka.BuildConfig
import com.govorilka.R
import com.govorilka.domain.Voice

private const val SERVER_URL_PLACEHOLDER = "http://127.0.0.1:3000"

@get:StringRes
private val Voice.label: Int
    get() = when (this) {
        Voice.Gleam -> R.string.settings_voice_gleam
        Voice.Meridian -> R.string.settings_voice_meridian
        Voice.Delta -> R.string.settings_voice_delta
        Voice.Cinder -> R.string.settings_voice_cinder
    }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = GovorilkaViewModelFactory),
) {
    SettingsContent(
        serverBaseUrl = viewModel.serverBaseUrl.value,
        appSecret = viewModel.appSecret.value,
        instructions = viewModel.instructions.value,
        voice = viewModel.voice.value,
        webSearch = viewModel.webSearch.value,
        versionName = BuildConfig.VERSION_NAME,
        onServerBaseUrlChange = viewModel::onServerBaseUrlChange,
        onAppSecretChange = viewModel::onAppSecretChange,
        onInstructionsChange = viewModel::onInstructionsChange,
        onVoiceChange = viewModel::onVoiceChange,
        onWebSearchChange = viewModel::onWebSearchChange,
        onBack = onBack,
    )
}

@Composable
private fun SettingsContent(
    serverBaseUrl: String,
    appSecret: String,
    instructions: String,
    voice: Voice,
    webSearch: Boolean,
    versionName: String,
    onServerBaseUrlChange: (String) -> Unit,
    onAppSecretChange: (String) -> Unit,
    onInstructionsChange: (String) -> Unit,
    onVoiceChange: (Voice) -> Unit,
    onWebSearchChange: (Boolean) -> Unit,
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
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
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
                        VoicePicker(voice = voice, onVoiceChange = onVoiceChange)
                        WebSearchToggle(checked = webSearch, onCheckedChange = onWebSearchChange)
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_version, versionName),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePicker(voice: Voice, onVoiceChange: (Voice) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(voice.label),
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_voice)) },
            supportingText = { Text(stringResource(R.string.settings_voice_hint)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Voice.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    onClick = {
                        onVoiceChange(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

@Composable
private fun WebSearchToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_web_search), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_web_search_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
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
            voice = Voice.DEFAULT,
            webSearch = false,
            versionName = "0.1.0",
            onServerBaseUrlChange = {},
            onAppSecretChange = {},
            onInstructionsChange = {},
            onVoiceChange = {},
            onWebSearchChange = {},
            onBack = {},
        )
    }
}
