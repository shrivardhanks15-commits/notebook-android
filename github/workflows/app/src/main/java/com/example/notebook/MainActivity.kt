package com.example.notebook

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

val COVERS = listOf(0xFF4F6BED, 0xFFE15759, 0xFF59A14F, 0xFFF1A208, 0xFF76448A, 0xFF1B9AAA).map { it.toInt() }
val PENS = listOf(0xFF000000, 0xFF1E5EFF, 0xFFE15759, 0xFF59A14F, 0xFFF1A208, 0xFF76448A).map { it.toInt() }
const val RATIO = 612f / 792f

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lib = Library(applicationContext)
        setContent { MaterialTheme { App(lib) } }
    }
}

@Composable
fun App(lib: Library) {
    var nbId by rememberSaveable { mutableStateOf<String?>(null) }
    var editIdx by rememberSaveable { mutableStateOf<Int?>(null) }
    val nb = nbId?.let { lib.get(it) }
    when {
        nb != null && editIdx != null -> { BackHandler { editIdx = null }; Editor(lib, nb.id, editIdx!!) { editIdx = null } }
        nb != null -> { BackHandler { nbId = null }; Pages(lib, nb, { editIdx = it }) { nbId = null } }
        else -> Home(lib) { nbId = it }
    }
}

// ---------- Home: notebook grid ----------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Home(lib: Library, onOpen: (String) -> Unit) {
    var renaming by remember { mutableStateOf<Notebook?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("My Notebooks") }) },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Default.Add, "New notebook") } }
    ) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(lib.notebooks, key = { it.id }) { nb ->
                Box {
                    Column(Modifier.combinedClickable(onClick = { onOpen(nb.id) }, onLongClick = { menu = nb.id })) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(3f / 4).shadow(3.dp, RoundedCornerShape(10.dp))
                                .background(Color(nb.color), RoundedCornerShape(10.dp)).padding(14.dp)
                        ) { PageCanvas(lib, nb.pages[0], nb.pages[0].strokes, null, Modifier.fillMaxSize()) }
                        Spacer(Modifier.height(6.dp))
                        Text(nb.title, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("${nb.pages.size} page${if (nb.pages.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(menu == nb.id, { menu = null }) {
                        DropdownMenuItem({ Text("Rename") }, { menu = null; renaming = nb })
                        DropdownMenuItem({ Text("Delete") }, { menu = null; lib.delete(nb.id) })
                    }
                }
            }
        }
        if (lib.notebooks.isEmpty())
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) { Text("No notebooks yet. Tap + to create one.") }
    }
    if (creating) NameDialog("New Notebook", "", true, { creating = false }) { t, c ->
        lib.add(t.ifBlank { "Untitled" }, c); creating = false
    }
    renaming?.let { nb ->
        NameDialog("Rename Notebook", nb.title, false, { renaming = null }) { t, _ ->
            lib.update(nb.copy(title = t.ifBlank { "Untitled" })); renaming = null
        }
    }
}

@Composable
fun NameDialog(title: String, initial: String, colors: Boolean, onDismiss: () -> Unit, onOk: (String, Int) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    var col by remember { mutableIntStateOf(COVERS.random()) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") })
                if (colors) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        COVERS.forEach { c ->
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(Color(c))
                                    .border(if (c == col) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .clickable { col = c }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton({ onOk(text.trim(), col) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}

// ---------- Pages grid inside a notebook ----------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun Pages(lib: Library, nb: Notebook, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    var menu by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(nb.title, maxLines = 1) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        LazyVerticalGrid(
            GridCells.Adaptive(140.dp), Modifier.padding(pad).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(nb.pages, key = { _, p -> p.id }) { i, p ->
                Box {
                    Column(Modifier.combinedClickable(onClick = { onOpen(i) }, onLongClick = { menu = p.id })) {
                        PageCanvas(lib, p, p.strokes, null, Modifier.fillMaxWidth().aspectRatio(RATIO).shadow(2.dp))
                        Text("Page ${i + 1}", style = MaterialTheme.typography.bodySmall)
                    }
                    DropdownMenu(menu == p.id, { menu = null }) {
                        DropdownMenuItem({ Text("Delete page") }, {
                            menu = null
                            if (nb.pages.size > 1) lib.update(nb.copy(pages = nb.pages.filter { it.id != p.id }))
                        })
                    }
                }
            }
            item {
                OutlinedButton({ lib.update(nb.copy(pages = nb.pages + Page())) }, Modifier.fillMaxWidth().aspectRatio(RATIO)) {
                    Text("+ Add Page")
                }
            }
        }
    }
}

// ---------- Page renderer (used for thumbnails and the editor) ----------
@Composable
fun PageCanvas(lib: Library, page: Page, strokes: List<Ink>, live: Ink?, modifier: Modifier, bgWidth: Int = 400) {
    val bg by produceState<Bitmap?>(null, page.pdfFile, page.pdfIndex, bgWidth) {
        value = withContext(Dispatchers.IO) { lib.background(page, bgWidth) }
    }
    Box(modifier.background(Color.White)) {
        bg?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        Canvas(Modifier.fillMaxSize()) { strokes.forEach { drawInk(it) }; live?.let { drawInk(it) } }
    }
}

fun DrawScope.drawInk(s: Ink) {
    val w = size.width; val h = size.height
    val col = Color(s.color)
    if (s.pts.size < 4) { drawCircle(col, s.width * w / 2, Offset(s.pts[0] * w, s.pts[1] * h)); return }
    val p = Path(); p.moveTo(s.pts[0] * w, s.pts[1] * h)
    for (i in 2 until s.pts.size step 2) p.lineTo(s.pts[i] * w, s.pts[i + 1] * h)
    drawPath(p, col, style = Stroke(s.width * w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

// ---------- Full-screen editor ----------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Editor(lib: Library, nbId: String, start: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val nb = lib.get(nbId) ?: return
    var idx by remember { mutableIntStateOf(start) }
    val page = nb.pages[idx.coerceIn(0, nb.pages.lastIndex)]
    var tool by remember { mutableStateOf("pen") }
    var color by remember { mutableIntStateOf(PENS[0]) }
    var width by remember { mutableFloatStateOf(0.004f) }
    var finger by remember { mutableStateOf(true) }   // false = stylus-only (palm rejection)
    var live by remember { mutableStateOf<Ink?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { lib.importPdf(nbId, idx, it) }
    }
    val cur by rememberUpdatedState(page.strokes)
    val save by rememberUpdatedState { s: List<Ink> ->
        lib.update(nb.copy(pages = nb.pages.mapIndexed { i, p -> if (i == idx) p.copy(strokes = s) else p }))
    }

    val input = Modifier.pointerInput(idx, tool, color, width, finger) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!finger && down.type == PointerType.Touch) return@awaitEachGesture
            val w = size.width.toFloat(); val h = size.height.toFloat()
            fun n(o: Offset) = listOf(o.x / w, o.y / h)
            fun erase(o: Offset) {
                val px = o.x / w; val py = o.y / h
                val keep = cur.filterNot { s -> s.pts.chunked(2).any { hypot(it[0] - px, (it[1] - py) * h / w) < 0.02f } }
                if (keep.size != cur.size) save(keep)
            }
            val erasing = tool == "eraser"
            val hi = tool == "highlighter"
            val c = if (hi) (color and 0x00FFFFFF) or 0x59000000 else color
            val sw = if (hi) width * 4 else width
            var pts = n(down.position)
            down.consume()
            if (erasing) erase(down.position) else live = Ink(c, sw, pts)
            while (true) {
                val e = awaitPointerEvent()
                val ch = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                if (erasing) erase(ch.position) else { pts = pts + n(ch.position); live = Ink(c, sw, pts) }
                ch.consume()
            }
            if (!erasing) { save(cur + Ink(c, sw, pts)); live = null }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Page ${idx + 1} of ${nb.pages.size}") },
                navigationIcon = { IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton({ idx-- }, enabled = idx > 0) { Icon(Icons.Default.KeyboardArrowUp, "Previous") }
                    IconButton({ idx++ }, enabled = idx < nb.pages.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Next") }
                    TextButton({ picker.launch(arrayOf("application/pdf")) }) { Text("PDF") }
                    IconButton({
                        val f = lib.exportPdf(nb)
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                        ctx.startActivity(Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/pdf")
                                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share PDF"))
                    }) { Icon(Icons.Default.Share, "Export PDF") }
                }
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("pen" to "Pen", "highlighter" to "Highlighter", "eraser" to "Eraser").forEach { (k, l) ->
                        FilterChip(tool == k, { tool = k }, { Text(l) })
                    }
                    TextButton({ save(page.strokes.dropLast(1)) }) { Text("Undo") }
                    FilterChip(finger, { finger = !finger }, { Text("Finger draw") })
                    PENS.forEach { c ->
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == color) 3.dp else 0.dp, Color.Gray, CircleShape)
                                .clickable { color = c; if (tool == "eraser") tool = "pen" }
                        )
                    }
                }
                Slider(width, { width = it }, valueRange = 0.002f..0.016f)
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color(0xFFDDDDDD)).padding(8.dp), Alignment.Center) {
            PageCanvas(
                lib, page, page.strokes, live,
                Modifier.fillMaxHeight().aspectRatio(RATIO, matchHeightConstraintsFirst = true).shadow(4.dp).then(input),
                bgWidth = 1600
            )
        }
    }
}
