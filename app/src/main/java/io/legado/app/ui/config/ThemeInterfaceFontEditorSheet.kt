package io.legado.app.ui.config

import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.font.FontDirectoryCatalog
import io.legado.app.help.font.FontFileEntry
import io.legado.app.help.font.FontLibraryStore
import io.legado.app.help.font.FontUsageInspector
import io.legado.app.help.font.FontUsageReference
import io.legado.app.ui.design.components.compose.*
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.font.FontDeletionConfirmDialog
import io.legado.app.ui.font.FontDeletionRequest
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

private data class InterfaceFontEntry(val uri: String, val name: String, val source: FontFileEntry? = null)
private data class InterfaceFontSelectionRequest(val entry: InterfaceFontEntry, val id: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeInterfaceFontEditorSheet(
    onDismissRequest: () -> Unit,
    preferredFontPath: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val names = stringArrayResource(R.array.system_typefaces)
    val fontRevision by NgInterfaceFontStore.revision.collectAsState()
    val openedAtRevision = rememberSaveable { fontRevision }
    val choice = remember(fontRevision) { NgInterfaceFontStore.choice(context) }
    val chosenName = remember(fontRevision) { NgInterfaceFontStore.name(context) }
    var customTab by rememberSaveable { mutableStateOf(preferredFontPath != null || choice !in NgInterfaceFontStore.systemChoices) }
    var directory by rememberSaveable {
        mutableStateOf(context.getPrefString(NgInterfaceFontStore.DIRECTORY)
            ?: context.getPrefString(PreferKey.fontFolder).orEmpty())
    }
    var entries by remember { mutableStateOf(emptyList<InterfaceFontEntry>()) }
    var loadingList by remember { mutableStateOf(false) }
    val selectionGate = remember { InterfaceFontSelectionGate() }
    var selection by remember { mutableStateOf<InterfaceFontSelectionRequest?>(null) }
    val listState = rememberLazyListState()
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDeletion by remember { mutableStateOf<FontDeletionRequest?>(null) }
    var deletionBusy by remember { mutableStateOf(false) }
    val libraryRevision by FontLibraryStore.revision.collectAsState()
    LaunchedEffect(libraryRevision) {
        directory = context.getPrefString(NgInterfaceFontStore.DIRECTORY)
            ?: context.getPrefString(PreferKey.fontFolder).orEmpty()
    }
    DisposableEffect(selectionGate) {
        onDispose { selectionGate.invalidate() }
    }
    val dismissSheet: () -> Unit = {
        selectionGate.invalidate()
        selection = null
        onDismissRequest()
        // Compose updates live; recreate old View hosts only after this sheet has closed.
        if (NgInterfaceFontStore.revision.value != openedAtRevision) postEvent(EventBus.RECREATE, "")
    }
    LaunchedEffect(directory, libraryRevision, fontRevision) {
        loadingList = true
        val result = withContext(Dispatchers.IO) { runCatching {
            val selectedIdentity = choice.takeUnless { it in NgInterfaceFontStore.systemChoices }
                ?.let(FontDirectoryCatalog::identity)
            FontDirectoryCatalog.prioritize(FontDirectoryCatalog.list(), preferredFontPath).map { font ->
                val uri = if (selectedIdentity != null && FontDirectoryCatalog.identity(font.uri) == selectedIdentity) choice else font.uri
                InterfaceFontEntry(uri, font.name, font)
            }
        }.onFailure { if (it is CancellationException) throw it } }
        entries = result.getOrElse { context.toastOnUi("字体目录读取失败：${it.localizedMessage}"); emptyList() }
        loadingList = false
    }
    LaunchedEffect(selection) {
        val request = selection ?: return@LaunchedEffect
        error = null
        try {
            val prepared = NgInterfaceFontStore.load(
                context,
                request.entry.uri,
                reloadFromSource = request.entry.uri !in NgInterfaceFontStore.systemChoices,
            )
            currentCoroutineContext().ensureActive()
            withContext(Dispatchers.IO) {
                currentCoroutineContext().ensureActive()
                NgInterfaceFontStore.saveIfCurrent(context, request.entry.uri, request.entry.name, prepared) { apply ->
                    selectionGate.commitIfCurrent(request.id, apply)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (selectionGate.isCurrent(request.id)) error = "字体无法应用：${failure.localizedMessage}"
        }
    }
    val directoryPicker = rememberLauncherForActivityResult(SelectDirectoryContract()) { result ->
        result.uri?.let { uri ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { FontDirectoryCatalog.registerDirectory(uri) }
                    directory = uri.toString()
                    customTab = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    context.toastOnUi("字体目录无法读取：${error.localizedMessage}")
                }
            }
        }
    }
    val base = NgTheme.snapshot
    ModalBottomSheet(
        onDismissRequest = { if (!deletionBusy) dismissSheet() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Transparent, contentColor = Color(base.colors.onSurface),
        shape = RectangleShape, dragHandle = null,
    ) {
        NgBottomDrawerSurface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.60f),
            contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
        ) {
            val color = Color(NgTheme.colors.onSurface)
            Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NgThemeSheetActionButton(
                        onClick = { directoryPicker.launch(SelectDirectoryContract.Request(initialUri = directory.takeIf { it.startsWith("content:") }?.let(Uri::parse))) },
                        contentDescription = stringResource(R.string.add_font_directory),
                        enabled = customTab,
                    ) {
                        Icon(painterResource(R.drawable.ic_create_folder_outline), null, Modifier.size(20.dp),
                            tint = Color(NgTheme.colors.primary).copy(alpha = if (customTab) 1f else .38f))
                    }
                    Text(stringResource(R.string.interface_font), Modifier.weight(1f), color = color,
                        fontSize = 21.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.width(44.dp))
                }
                Spacer(Modifier.height(10.dp))
                TabRow(
                    selectedTabIndex = if (customTab) 1 else 0,
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = Color.Transparent,
                    contentColor = Color(NgTheme.colors.primary),
                ) {
                    listOf(R.string.font_mode_system, R.string.font_mode_custom).forEachIndexed { index, label ->
                        val selected = index == (if (customTab) 1 else 0)
                        Tab(
                            selected = selected,
                            onClick = { customTab = index == 1 },
                            text = {
                                Text(
                                    text = stringResource(label),
                                    color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
                                    fontSize = 14.sp,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                error?.let { Text(it, color = Color(NgTheme.colors.error), fontSize = 12.sp) }
                val rows = if (!customTab) NgInterfaceFontStore.systemChoices.mapIndexed { i, value -> InterfaceFontEntry(value, names[i]) }
                    else entries.let { files ->
                        if (choice !in NgInterfaceFontStore.systemChoices && files.none { it.uri == choice }) {
                            listOf(InterfaceFontEntry(choice, chosenName.ifBlank { Uri.parse(choice).lastPathSegment.orEmpty() })) + files
                        } else files
                    }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(rows, key = { it.uri }) { font ->
                        val fontTypeface by produceState<Typeface?>(
                            font.uri.takeIf { it in NgInterfaceFontStore.systemChoices }?.let(NgInterfaceFontStore::systemTypeface),
                            font.uri,
                            if (choice == font.uri) fontRevision else 0L,
                            loadingList,
                        ) {
                            if (loadingList) return@produceState
                            value = runCatching { NgInterfaceFontStore.load(context, font.uri).typeface }
                                .onFailure { if (it is CancellationException) throw it }.getOrNull()
                        }
                        val displayedTypeface = fontTypeface
                        val displayedFontFamily = remember(displayedTypeface) {
                            displayedTypeface?.let(::FontFamily)
                        }
                        val file = font.source
                        val deleteAction: (@Composable () -> Unit)? = if (file != null && customTab) {
                            {
                                val enabled = file.canDelete && !loadingList && !deletionBusy
                                Box(
                                    modifier = Modifier.fillMaxSize().clickable(
                                        enabled = enabled,
                                        role = Role.Button,
                                        onClick = {
                                            selectionGate.invalidate()
                                            selection = null
                                            error = null
                                            deletionBusy = true
                                            scope.launch {
                                                try {
                                                    val usages = withContext(Dispatchers.IO) {
                                                        FontUsageInspector.deletionUsageReferences(listOf(file.uri))
                                                    }
                                                    pendingDeletion = FontDeletionRequest(file, usages)
                                                } catch (cancelled: CancellationException) {
                                                    throw cancelled
                                                } catch (failure: Exception) {
                                                    context.toastOnUi(failure.localizedMessage ?: "无法检查字体使用情况")
                                                } finally {
                                                    deletionBusy = false
                                                }
                                            }
                                        },
                                    ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_bgm_menu_delete),
                                        contentDescription = "${stringResource(R.string.delete)} ${font.name}",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color(NgTheme.colors.error).copy(alpha = if (enabled) 1f else .38f),
                                    )
                                }
                            }
                        } else null
                        NgManagementListCard(
                            title = font.name,
                            trailingContent = deleteAction,
                            selected = choice == font.uri,
                            containerColor = ngDrawerContentCardColor(),
                            titleColor = Color(NgTheme.colors.onSurface),
                            titleFontFamily = displayedFontFamily,
                            size = NgManagementListCardSize.COMPACT_SINGLE_LINE,
                            onClick = {
                                if (!deletionBusy && (!customTab || !loadingList)) {
                                    error = null
                                    selection = InterfaceFontSelectionRequest(font, selectionGate.nextRequest())
                                }
                            },
                        ) {
                            Text("Aa", color = Color(NgTheme.colors.onSurface), fontSize = 20.sp,
                                fontFamily = displayedFontFamily)
                        }
                    }
                    if (customTab && rows.isEmpty()) item {
                        Text(if (loadingList) stringResource(R.string.loading) else stringResource(R.string.font_folder_empty),
                            color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 14.sp,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
    pendingDeletion?.let { deletion ->
        FontDeletionConfirmDialog(
            usages = deletion.usages,
            busy = deletionBusy,
            onDismissRequest = { pendingDeletion = null },
            onConfirm = {
                deletionBusy = true
                scope.launch {
                    try {
                        FontDirectoryCatalog.delete(
                            deletion.file,
                            confirmedUsages = deletion.usages.map(FontUsageReference::location),
                        )
                        if (choice !in NgInterfaceFontStore.systemChoices &&
                            NgInterfaceFontStore.choice(context) == NgInterfaceFontStore.systemChoices.first()
                        ) {
                            customTab = false
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        context.toastOnUi(failure.localizedMessage ?: "字体删除失败")
                    } finally {
                        deletionBusy = false
                        pendingDeletion = null
                    }
                }
            },
        )
    }
}
