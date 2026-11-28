@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.primebox.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.primebox.data.Card
import com.primebox.data.Store
import com.primebox.ext.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

@Composable
fun SearchScreen(
    onOpenDetail: (Card, String) -> Unit,
    onOpenExtensions: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Card>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var noExtension by remember { mutableStateOf(false) }
    var recent by remember { mutableStateOf(Store.recentSearches()) }
    var job by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { focus.requestFocus() }
    }

    fun runSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        keyboard?.hide()
        val api = Providers.active()
        if (api == null) {
            noExtension = true
            return
        }
        noExtension = false
        Store.addRecentSearch(q)
        recent = Store.recentSearches()
        job?.cancel() // an older, slower search must not overwrite the newest one
        loading = true
        error = null
        job = scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching { withTimeout(25_000) { api.search(q) } }
            }
            loading = false
            searched = true
            results = outcome.getOrNull().orEmpty().map { Card.from(it) }
            error = outcome.exceptionOrNull()?.message?.let { "Search failed: $it" }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search movies, series, anime…") },
                    singleLine = true,
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = ""; focus.requestFocus() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
            }
        }
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            if (loading) WavyProgress(progress = null, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

            when {
                noExtension -> EmptyState(
                    icon = Icons.Filled.Extension,
                    title = "No extension installed",
                    body = "Search needs a source. Add an extension first.",
                    actionLabel = "Open extensions",
                    onAction = onOpenExtensions
                )

                error != null -> EmptyState(
                    icon = Icons.Filled.CloudOff,
                    title = "Something went wrong",
                    body = error.orEmpty(),
                    actionLabel = "Try again",
                    onAction = { runSearch(query) }
                )

                searched && !loading && results.isEmpty() -> EmptyState(
                    icon = Icons.Filled.SearchOff,
                    title = "No results",
                    body = "Try another title, or switch the source on the home screen."
                )

                results.isNotEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(results) { card ->
                        PosterCard(card = card, width = null, onClick = { onOpenDetail(card, "search|${card.url}") })
                    }
                }

                recent.isNotEmpty() && !loading -> Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Recent searches",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            Store.clearRecentSearches()
                            recent = emptyList()
                        }) { Text("Clear") }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { item ->
                            AssistChip(
                                onClick = {
                                    query = item
                                    runSearch(item)
                                },
                                label = { Text(item) },
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) }
                            )
                        }
                    }
                }

                else -> Box(Modifier.fillMaxSize())
            }
        }
    }
}
