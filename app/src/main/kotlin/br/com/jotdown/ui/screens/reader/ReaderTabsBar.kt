package br.com.jotdown.ui.screens.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.jotdown.R
import br.com.jotdown.ui.viewmodel.ReaderTab

@Composable
fun ReaderTabsBar(
    tabs: List<ReaderTab>,
    activeDocumentId: String,
    isPro: Boolean,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
    onAddTab: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(tabs, activeDocumentId) {
        val selectedIndex = tabs.indexOfFirst { it.documentId == activeDocumentId }
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(tabs, key = { it.documentId }) { tab ->
                Surface(
                    onClick = { onSelect(tab.documentId) },
                    shape = RoundedCornerShape(20.dp),
                    color = if (tab.documentId == activeDocumentId) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.widthIn(max = 240.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = tab.title,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge
                        )
                        IconButton(
                            onClick = { onClose(tab.documentId) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.reader_tabs_close, tab.title),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onAddTab) {
            Icon(
                imageVector = if (isPro) Icons.Default.Add else Icons.Default.Lock,
                contentDescription = stringResource(
                    if (isPro) R.string.reader_tabs_add else R.string.reader_tabs_unlock
                )
            )
        }
    }
}
