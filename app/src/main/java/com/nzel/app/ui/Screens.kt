package com.nzel.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import coil.compose.AsyncImage
import com.nzel.app.MainViewModel
import com.nzel.app.Phase
import com.nzel.app.R
import com.nzel.app.SharedLink
import com.nzel.app.dl.DownloadEngine
import com.nzel.app.dl.Format
import com.nzel.app.dl.HistoryItem
import com.nzel.app.dl.HistoryStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private fun formatLabelRes(f: Format) = when (f) {
    Format.BEST -> R.string.fmt_best
    Format.P720 -> R.string.fmt_720
    Format.P480 -> R.string.fmt_480
    Format.MP3 -> R.string.fmt_audio
}

private fun fmtDuration(sec: Int): String =
    if (sec <= 0) "" else "%d:%02d".format(sec / 60, sec % 60)

private fun fmtSize(bytes: Long): String =
    if (bytes <= 0) "" else if (bytes < 1024 * 1024) "${bytes / 1024} KB" else "%.1f MB".format(bytes / 1048576.0)

private fun fmtEta(sec: Long): String =
    if (sec < 0) "" else "%d:%02d".format(sec / 60, sec % 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val startDownload = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.download(context.applicationContext)
    }

    LaunchedEffect(SharedLink.pending) {
        SharedLink.pending?.let {
            vm.urlText = it
            tab = 0
            vm.analyze()
            SharedLink.pending = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Download, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_new)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.History, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_history)) }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            if (tab == 0) HomeScreen(vm, startDownload) else HistoryScreen(vm)
        }
    }

    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            SettingsContent()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: MainViewModel, onDownload: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = vm.urlText,
            onValueChange = { vm.urlText = it; vm.softReset() },
            label = { Text(stringResource(R.string.url_label)) },
            placeholder = { Text(stringResource(R.string.url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            trailingIcon = {
                if (vm.urlText.isNotBlank()) {
                    IconButton(onClick = { vm.reset() }) {
                        Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                clipboard.getText()?.text?.let { vm.urlText = it }
            }) {
                Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.paste))
            }
            vm.platform?.let { p ->
                SuggestionChip(onClick = {}, label = { Text(stringResource(R.string.detected) + ": " + p.label) })
            }
        }

        if (!DownloadEngine.ready) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    DownloadEngine.initError ?: stringResource(R.string.preparing_engine),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Button(
            onClick = { vm.analyze() },
            enabled = vm.urlText.isNotBlank() && DownloadEngine.ready &&
                vm.phase != Phase.ANALYZING && vm.phase != Phase.DOWNLOADING,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.fetch))
        }

        when (vm.phase) {
            Phase.ANALYZING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.analyzing), style = MaterialTheme.typography.bodyMedium)
            }
            Phase.ERROR -> ErrorCard(vm)
            Phase.READY -> MetaCard(vm, onDownload)
            Phase.DOWNLOADING -> DownloadingCard(vm)
            Phase.DONE -> DoneCard(vm)
            Phase.IDLE -> {}
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorCard(vm: MainViewModel) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.error_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                stringResource(vm.errorTextRes ?: R.string.error_generic),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            vm.errorDetail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            TextButton(onClick = { vm.analyze() }, enabled = DownloadEngine.ready) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetaCard(vm: MainViewModel, onDownload: () -> Unit) {
    val m = vm.meta ?: return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (m.thumb != null) {
                    AsyncImage(
                        model = m.thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        m.uploader,
                        fmtDuration(m.durationSec).ifBlank { null },
                        m.extractor?.replaceFirstChar { it.uppercase() }
                    ).joinToString(" • ")
                    if (sub.isNotBlank()) {
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(stringResource(R.string.choose_format), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Format.entries.forEach { f ->
                    FilterChip(
                        selected = vm.selectedFormat == f,
                        onClick = { vm.selectedFormat = f },
                        label = { Text(stringResource(formatLabelRes(f))) }
                    )
                }
            }
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.download))
            }
        }
    }
}

@Composable
private fun DownloadingCard(vm: MainViewModel) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(vm.meta?.title ?: "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { DownloadEngine.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${(DownloadEngine.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (DownloadEngine.etaSec >= 0) "ETA " + fmtEta(DownloadEngine.etaSec) else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (DownloadEngine.lastLine.isNotBlank()) {
                Text(
                    DownloadEngine.lastLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { vm.cancelDownload() }) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun DoneCard(vm: MainViewModel) {
    val context = LocalContext.current
    val item = vm.savedItem ?: return
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.saved_to),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                listOf(item.fileName, fmtSize(item.sizeBytes)).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { vm.openItem(context, item) }) { Text(stringResource(R.string.open)) }
                TextButton(onClick = { vm.shareItem(context, item) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.share))
                }
                TextButton(onClick = { vm.reset() }) { Text(stringResource(R.string.new_link)) }
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val items = HistoryStore.items
    if (items.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.empty_history_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.empty_history_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(items, key = { it.id }) { item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.openItem(context, item) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.thumb != null) {
                        AsyncImage(
                            model = item.thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val date = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                        Text(
                            listOf(item.platform, fmtSize(item.sizeBytes), date).filter { it.isNotBlank() }.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { HistoryStore.remove(context, item.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SettingsContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentTag = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifBlank { "system" }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.language), style = MaterialTheme.typography.labelLarge)
        val langs = listOf("en" to "English", "ar" to "العربية", "fr" to "Français")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            langs.forEachIndexed { i, (tag, label) ->
                SegmentedButton(
                    selected = currentTag.startsWith(tag),
                    onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
                    shape = SegmentedButtonDefaults.itemShape(index = i, count = langs.size)
                ) { Text(label) }
            }
        }
        HorizontalDivider()
        Text(stringResource(R.string.engine), style = MaterialTheme.typography.labelLarge)
        Text(
            "yt-dlp " + (DownloadEngine.engineVersion ?: stringResource(R.string.analyzing)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = { scope.launch { DownloadEngine.refreshEngine(context.applicationContext) } },
            enabled = DownloadEngine.ready && DownloadEngine.updateState != "updating"
        ) {
            Text(
                stringResource(
                    when (DownloadEngine.updateState) {
                        "updating" -> R.string.engine_updating
                        else -> R.string.engine_update
                    }
                )
            )
        }
        when (DownloadEngine.updateState) {
            "done" -> Text(stringResource(R.string.engine_updated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            "failed" -> Text(stringResource(R.string.engine_update_failed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        HorizontalDivider()
        Text(stringResource(R.string.about), style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.version) + " 1.0.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
