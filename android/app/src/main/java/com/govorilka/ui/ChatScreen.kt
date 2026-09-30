package com.govorilka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.govorilka.R
import com.govorilka.domain.NEW_CONVERSATION_TITLE

private val MicButtonSize = 88.dp
private const val DISABLED_MIC_ALPHA = 0.55f

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = GovorilkaViewModelFactory),
) {
    ChatContent(
        title = viewModel.title.value ?: NEW_CONVERSATION_TITLE,
        draft = viewModel.draft.value,
        inputEnabled = viewModel.inputEnabled.value,
        micEnabled = viewModel.micEnabled.value,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::onSend,
        onMicClick = viewModel::onMicClick,
        onBack = onBack,
    )
}

@Composable
private fun ChatContent(
    title: String,
    draft: String,
    inputEnabled: Boolean,
    micEnabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { BackTopAppBar(title = title, onBack = onBack) },
        bottomBar = {
            ChatBottomBar(
                draft = draft,
                inputEnabled = inputEnabled,
                micEnabled = micEnabled,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onMicClick = onMicClick,
            )
        },
    ) { padding ->
        MessageFeed(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun ChatBottomBar(
    draft: String,
    inputEnabled: Boolean,
    micEnabled: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    enabled = inputEnabled,
                    placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                MicButton(enabled = micEnabled, onClick = onMicClick)
            }
        }
    }
}

@Composable
private fun MicButton(enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(MicButtonSize)
            .shadow(elevation = if (enabled) 8.dp else 2.dp, shape = CircleShape),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MicAmber,
            contentColor = OnMicAmber,
            disabledContainerColor = MicAmber.copy(alpha = DISABLED_MIC_ALPHA),
            disabledContentColor = OnMicAmber.copy(alpha = DISABLED_MIC_ALPHA),
        ),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mic),
            contentDescription = stringResource(R.string.chat_mic),
            modifier = Modifier.size(36.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatPreview() {
    GovorilkaTheme {
        ChatContent(
            title = NEW_CONVERSATION_TITLE,
            draft = "",
            inputEnabled = false,
            micEnabled = false,
            onDraftChange = {},
            onSend = {},
            onMicClick = {},
            onBack = {},
        )
    }
}
