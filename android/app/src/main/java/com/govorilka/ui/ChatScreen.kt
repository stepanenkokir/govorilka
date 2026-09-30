package com.govorilka.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.govorilka.R
import com.govorilka.domain.MessageRole
import com.govorilka.domain.NEW_CONVERSATION_TITLE

private val MicButtonSize = 88.dp
private const val DISABLED_MIC_ALPHA = 0.55f
private const val LISTENING_PULSE_SCALE = 1.06f

@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = GovorilkaViewModelFactory),
) {
    val context = LocalContext.current
    val requestMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.onMicClick() else viewModel.onMicPermissionDenied()
    }
    val phase = viewModel.phase.value
    ChatContent(
        title = viewModel.title.value ?: NEW_CONVERSATION_TITLE,
        feed = viewModel.feed.value,
        draft = viewModel.draft.value,
        inputEnabled = viewModel.inputEnabled.value,
        micEnabled = viewModel.micEnabled.value,
        phase = phase,
        muted = viewModel.muted.value,
        speakerOn = viewModel.speakerOn.value,
        onDraftChange = viewModel::onDraftChange,
        onSend = viewModel::onSend,
        onMicClick = {
            val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (phase.isActive || granted) viewModel.onMicClick() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
        },
        onMuteToggle = viewModel::onMuteToggle,
        onSpeakerToggle = viewModel::onSpeakerToggle,
        onBack = onBack,
    )
}

@Composable
private fun ChatContent(
    title: String,
    feed: List<FeedItem>,
    draft: String,
    inputEnabled: Boolean,
    micEnabled: Boolean,
    phase: CallPhase,
    muted: Boolean,
    speakerOn: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = { BackTopAppBar(title = title, onBack = onBack) },
        bottomBar = {
            ChatBottomBar(
                draft = draft,
                inputEnabled = inputEnabled,
                micEnabled = micEnabled,
                phase = phase,
                muted = muted,
                speakerOn = speakerOn,
                onDraftChange = onDraftChange,
                onSend = onSend,
                onMicClick = onMicClick,
                onMuteToggle = onMuteToggle,
                onSpeakerToggle = onSpeakerToggle,
            )
        },
    ) { padding ->
        MessageFeed(items = feed, modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun ChatBottomBar(
    draft: String,
    inputEnabled: Boolean,
    micEnabled: Boolean,
    phase: CallPhase,
    muted: Boolean,
    speakerOn: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMicClick: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CallStatus(phase)
            AnimatedVisibility(
                visible = phase == CallPhase.Listening,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = muted,
                        onClick = onMuteToggle,
                        label = { Text(stringResource(R.string.chat_mute)) },
                    )
                    FilterChip(
                        selected = speakerOn,
                        onClick = onSpeakerToggle,
                        label = { Text(stringResource(R.string.chat_speaker)) },
                    )
                }
            }
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
                MicButton(enabled = micEnabled, phase = phase, onClick = onMicClick)
            }
        }
    }
}

@Composable
private fun CallStatus(phase: CallPhase) {
    val text = when (phase) {
        CallPhase.Idle -> null
        CallPhase.Connecting -> stringResource(R.string.chat_call_connecting)
        CallPhase.Listening -> stringResource(R.string.chat_call_listening)
        CallPhase.Closing -> stringResource(R.string.chat_call_closing)
        CallPhase.MissingSettings -> stringResource(R.string.chat_call_missing_settings)
        CallPhase.MicDenied -> stringResource(R.string.chat_call_mic_denied)
        is CallPhase.Failed -> phase.message
    }
    val colors = MaterialTheme.colorScheme
    val isError = phase is CallPhase.Failed || phase == CallPhase.MissingSettings || phase == CallPhase.MicDenied
    val dotColor by animateColorAsState(
        when {
            isError -> colors.error
            phase == CallPhase.Listening -> Color(0xFF2F8F5B)
            else -> MicAmber
        },
        label = "callDot",
    )
    AnimatedVisibility(
        visible = text != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(10.dp).background(dotColor, CircleShape))
            Text(
                text = text.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MicButton(enabled: Boolean, phase: CallPhase, onClick: () -> Unit) {
    val active = phase.isActive
    // The infinite transition exists only while listening, so an idle screen draws no frames.
    val pulse = if (phase == CallPhase.Listening) {
        rememberInfiniteTransition(label = "micPulse").animateFloat(
            initialValue = 1f,
            targetValue = LISTENING_PULSE_SCALE,
            animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
            label = "micScale",
        )
    } else {
        null
    }
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.error else MicAmber,
        label = "micContainer",
    )
    val content = if (active) MaterialTheme.colorScheme.onError else OnMicAmber
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(MicButtonSize)
            .graphicsLayer {
                val scale = pulse?.value ?: 1f
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = if (enabled) 8.dp else 2.dp, shape = CircleShape),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = DISABLED_MIC_ALPHA),
            disabledContentColor = content.copy(alpha = DISABLED_MIC_ALPHA),
        ),
    ) {
        if (active) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_end_call),
                modifier = Modifier.size(36.dp),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = stringResource(R.string.chat_mic),
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChatPreview() {
    GovorilkaTheme {
        ChatContent(
            title = NEW_CONVERSATION_TITLE,
            feed = listOf(
                FeedItem("1", MessageRole.User, "Привет! Как дела?"),
                FeedItem("2", MessageRole.Assistant, "Привет! Всё хорошо, чем помочь?"),
            ),
            draft = "",
            inputEnabled = false,
            micEnabled = true,
            phase = CallPhase.Listening,
            muted = false,
            speakerOn = true,
            onDraftChange = {},
            onSend = {},
            onMicClick = {},
            onMuteToggle = {},
            onSpeakerToggle = {},
            onBack = {},
        )
    }
}
