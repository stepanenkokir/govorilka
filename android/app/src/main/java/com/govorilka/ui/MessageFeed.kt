package com.govorilka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.govorilka.domain.MessageRole

data class FeedItem(val id: String, val role: MessageRole, val text: String)

private val BubbleCorner = 20.dp
private val BubbleTail = 6.dp

@Composable
fun MessageFeed(items: List<FeedItem> = emptyList(), modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val lastText = items.lastOrNull()?.text
    LaunchedEffect(items.size, lastText) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.id }) { item ->
            MessageBubble(item, Modifier.animateItem())
        }
    }
}

@Composable
private fun MessageBubble(item: FeedItem, modifier: Modifier = Modifier) {
    val isUser = item.role == MessageRole.User
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = BubbleCorner,
                topEnd = BubbleCorner,
                bottomStart = if (isUser) BubbleCorner else BubbleTail,
                bottomEnd = if (isUser) BubbleTail else BubbleCorner,
            ),
            color = if (isUser) colors.primaryContainer else colors.surfaceContainerHigh,
            contentColor = if (isUser) colors.onPrimaryContainer else colors.onSurface,
            shadowElevation = 2.dp,
        ) {
            Text(
                text = item.text.trim(),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
