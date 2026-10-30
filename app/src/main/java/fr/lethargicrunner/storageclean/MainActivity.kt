package fr.lethargicrunner.storageclean

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StorageCleanTheme { StorageCleanApp(this) } }
    }
}

// ---------- Theme ----------

@Composable
fun StorageCleanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ---------- Selection persistante ----------

private const val PREFS = "storageclean_prefs"
private const val KEY_FOLDERS = "selected_folders"
private const val KEY_TYPES = "selected_types"

fun loadFolders(activity: ComponentActivity): MutableList<String> {
    val prefs: SharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return prefs.getStringSet(KEY_FOLDERS, emptySet())?.toMutableList() ?: mutableListOf()
}

fun saveFolders(activity: ComponentActivity, folders: List<String>) {
    val prefs: SharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(KEY_FOLDERS, folders.toSet()).apply()
}

fun loadTypes(activity: ComponentActivity): MutableMap<String, Boolean> {
    val prefs: SharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val stored = prefs.getStringSet(KEY_TYPES, null)
    return mutableMapOf<String, Boolean>().apply {
        CATEGORIES.forEach { c ->
            put(c.name, stored?.contains(c.name) ?: (c.name != "Audio"))
        }
    }
}

fun saveTypes(activity: ComponentActivity, types: Map<String, Boolean>) {
    val prefs: SharedPreferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    prefs.edit().putStringSet(KEY_TYPES, types.filterValues { it }.keys).apply()
}

fun absorbedPaths(selected: List<String>): List<String> =
    selected.filter { p ->
        selected.none { o ->
            o != p && p.startsWith(o.trimEnd('/') + "/")
        }
    }

fun isHiddenPath(path: String): Boolean =
    path.split('/').any { it.startsWith(".") && it.length > 1 }

// ---------- Application ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageCleanApp(activity: ComponentActivity) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(loadFolders(activity)) } }
    val typeSelected = remember { mutableStateMapOf<String, Boolean>().apply { putAll(loadTypes(activity)) } }
    var showPicker by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<ScanReport?>(null) }
    var progress by remember { mutableStateOf<ScanProgress?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var hasAccess by remember { mutableStateOf(Environment.isExternalStorageManager()) }
    val expandedGroups = remember { mutableStateOf(setOf(0)) }
    val scope = rememberCoroutineScope()

    if (showPicker) {
        FolderPickerScreen(
            selected = selected,
            onToggle = { path ->
                if (selected.contains(path)) selected.remove(path) else selected.add(path)
                saveFolders(activity, selected)
            },
            onDone = { showPicker = false },
        )
        return
    }

    val scanPaths = absorbedPaths(selected)
    val selectedExts = CATEGORIES
        .filter { typeSelected[it.name] == true }
        .flatMap { it.extensions }
        .toSet()

    Scaffold(
        topBar = { TopAppBar(title = { Text("StorageClean") }) },
    ) { innerPadding ->
        Surface(
            Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasAccess) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Acces aux fichiers requis", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "StorageClean a besoin de l'acces a tous les fichiers pour scanner les memoires du telephone. Aucun fichier n'est jamais modifie ni supprime.",
                                fontSize = 13.sp,
                            )
                            Button(onClick = {
                                activity.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:" + activity.packageName),
                                    )
                                )
                            }) { Text("Accorder l'acces") }
                            OutlinedButton(onClick = { hasAccess = Environment.isExternalStorageManager() }) {
                                Text("J'ai accorde l'acces")
                            }
                        }
                    }
                } else {
                    // --- Selection des dossiers ---
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Dossiers a scanner", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    selected.size.toString() + " coche(s)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (selected.isEmpty()) {
                                Text(
                                    "Aucun dossier selectionne. Utilise le navigateur pour choisir les dossiers a scanner.",
                                    fontSize = 13.sp,
                                )
                            } else {
                                selected.sorted().forEach { folder ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val viaParent = scanPaths.none { folder == it }
                                        Text(
                                            folder + if (viaParent) "  (recouvert par un dossier parent)" else "",
                                            Modifier.fillMaxWidth(0.75f),
                                            fontSize = 12.sp,
                                        )
                                        TextButton(onClick = {
                                            selected.remove(folder)
                                            saveFolders(activity, selected)
                                        }) { Text("Retirer") }
                                    }
                                }
                            }
                            Button(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Naviguer et cocher des dossiers")
                            }
                        }
                    }

                    // --- Selection des types de fichiers ---
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Types de fichiers", style = MaterialTheme.typography.titleMedium)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CATEGORIES.forEach { c ->
                                    FilterChip(
                                        selected = typeSelected[c.name] == true,
                                        onClick = {
                                            typeSelected[c.name] = !(typeSelected[c.name] ?: false)
                                            saveTypes(activity, typeSelected)
                                        },
                                        label = {
                                            Column {
                                                Text(c.name, fontSize = 13.sp)
                                                Text(
                                                    c.extensions.take(4).joinToString(", ") +
                                                        if (c.extensions.size > 4) "…" else "",
                                                    fontSize = 10.sp,
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                            Text(
                                "L'audio n'est pas coche par defaut : les doublons MP3 sont deja traites par DupScan.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (!scanning) {
                                scanning = true
                                report = null
                                message = null
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        FileScanner.scan(scanPaths, selectedExts) { p ->
                                            progress = p
                                        }
                                    }
                                    report = result
                                    scanning = false
                                    progress = null
                                }
                            }
                        },
                        enabled = !scanning && scanPaths.isNotEmpty() && selectedExts.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (scanning) "Scan en cours…" else "Scanner") }

                    progress?.let { p ->
                        Column {
                            if (p.total > 0) {
                                val frac = p.done.toFloat() / p.total
                                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                                Text(p.phase + " : " + p.done + " / " + p.total, fontSize = 12.sp)
                            } else {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(p.phase + " : " + p.done + " fichiers…", fontSize = 12.sp)
                            }
                        }
                    }

                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }

                    report?.let { r ->
                        // --- Etat du stockage par type ---
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Etat du stockage", style = MaterialTheme.typography.titleMedium)
                                r.typeStats.forEach { ts ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(ts.category, fontSize = 13.sp)
                                        Text(
                                            ts.count.toString() + " fichiers — " + human(ts.bytes),
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    Stat(r.groups.size.toString(), "groupes doublons")
                                    Stat(r.redundant.toString(), "redondants")
                                    Stat(human(r.spaceGain), "recuperables")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val dir = activity.getExternalFilesDir(null)
                                        if (dir == null) {
                                            message = "Stockage externe indisponible."
                                        } else {
                                            val f = File(dir, "storageclean_rapport.csv")
                                            f.writeText(buildCsv(r))
                                            message = "Rapport ecrit : " + f.absolutePath
                                        }
                                    }) { Text("Exporter CSV") }
                                    TextButton(onClick = { expandedGroups.value = emptySet() }) {
                                        Text("Tout replier")
                                    }
                                }
                            }
                        }

                        LazyColumn {
                            itemsIndexed(r.groups) { i, g ->
                                Card(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                        expandedGroups.value =
                                            if (i in expandedGroups.value) expandedGroups.value - i
                                            else expandedGroups.value + i
                                    },
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            "Groupe " + (i + 1) + " — " + g.files.size + " exemplaires",
                                            style = MaterialTheme.typography.titleSmall,
                                        )
                                        Text(
                                            "Gain : " + human(g.gain),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.secondary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (i in expandedGroups.value) {
                                            Spacer(Modifier.height(4.dp))
                                            g.files.sortedBy { it.mtime }.forEach { f ->
                                                val hidden = if (isHiddenPath(f.path)) " [cache]" else ""
                                                val oldest = if (f == g.oldest) "  [le plus ancien]" else ""
                                                Text(
                                                    "• " + human(f.size) + "  " + f.path + hidden + oldest,
                                                    fontSize = 12.sp,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Ecran de navigation des dossiers ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerScreen(
    selected: List<String>,
    onToggle: (String) -> Unit,
    onDone: () -> Unit,
) {
    var currentPath by remember { mutableStateOf("/storage") }

    val folders = remember(currentPath) {
        File(currentPath).listFiles()
            ?.filter { it.isDirectory && it.canRead() }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selection des dossiers") },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Terminer") }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).padding(16.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                val segments = currentPath.split('/').filter { it.isNotEmpty() }
                var acc = ""
                TextButton(onClick = { currentPath = "/storage" }) { Text("/") }
                segments.forEach { seg ->
                    acc += "/" + seg
                    val target = acc
                    Text("/", fontSize = 13.sp)
                    TextButton(onClick = { currentPath = target }) { Text(seg, fontSize = 13.sp) }
                }
            }

            Text(
                "Coche = scanner ce dossier et tout son contenu. Touche le nom pour entrer dedans.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn {
                items(folders, key = { it.absolutePath }) { dir ->
                    val path = dir.absolutePath
                    val ancestors = generateSequence(path) {
                        it.substringBeforeLast('/').ifEmpty { null }
                    }.filter { it != path }.toList()
                    val viaParent = ancestors.any { it in selected }
                    val checked = viaParent || path in selected
                    val isHidden = dir.name.startsWith(".")
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            enabled = !viaParent,
                            onCheckedChange = { onToggle(path) },
                        )
                        Text(
                            dir.name + (if (isHidden) "  (cache)" else "") +
                                (if (viaParent) "  — inclus via un dossier parent" else ""),
                            Modifier.weight(1f).clickable { currentPath = path }.padding(vertical = 12.dp),
                            fontSize = 14.sp,
                            fontWeight = if (isHidden) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

// ---------- Utilitaires ----------

@Composable
fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun human(n: Long): String {
    var x = n.toDouble()
    for (unit in listOf("o", "Ko", "Mo", "Go", "To")) {
        if (x < 1024 || unit == "To") {
            return if (unit == "o") n.toString() + " o" else String.format(Locale.FRANCE, "%.1f %s", x, unit)
        }
        x /= 1024
    }
    return n.toString() + " o"
}

fun buildCsv(r: ScanReport): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.FRANCE)
    val sb = StringBuilder("groupe,categorie,taille_octets,sha256,mtime_iso,chemin\n")
    r.groups.forEachIndexed { i, g ->
        g.files.forEach { f ->
            sb.append(i + 1).append(',')
                .append(f.category ?: "Autres").append(',')
                .append(f.size).append(',')
                .append(f.digest ?: "").append(',')
                .append(fmt.format(Date(f.mtime))).append(',')
                .append('"').append(f.path.replace("\"", "\"\"")).append('"')
                .append('\n')
        }
    }
    return sb.toString()
}
