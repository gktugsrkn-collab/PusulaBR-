package com.pusulabrc.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.pow

private val ComponentActivity.store by preferencesDataStore("pusula_v2")
private val DATA_KEY = stringPreferencesKey("finance_json")
private val DARK_KEY = booleanPreferencesKey("dark_mode")
private val fmt = NumberFormat.getCurrencyInstance(Locale("tr", "TR"))
private fun tl(v: Double) = fmt.format(v)
private fun num(s: String): Double {
    val value = s.trim().replace("₺", "").replace(" ", "")
    val normalized = if (value.contains(",")) value.replace(".", "").replace(",", ".") else value
    return normalized.toDoubleOrNull() ?: 0.0
}
private fun today() = LocalDate.now().toString()

data class Debt(
    val id: Long, val name: String, val type: String, val principal: Double,
    val annualRate: Double, val months: Int, val startDate: String,
    val paid: Int = 0, val manualPayment: Double = 0.0, val notes: String = ""
) {
    fun payment(): Double {
        if (manualPayment > 0) return manualPayment
        if (months <= 0) return 0.0
        val r = annualRate / 1200.0
        return if (r == 0.0) principal / months else principal * r / (1 - (1 + r).pow(-months))
    }
    fun remaining(): Double {
        val n = (months - paid).coerceAtLeast(0)
        if (n == 0) return 0.0
        val r = annualRate / 1200.0
        if (manualPayment > 0.0 || r == 0.0) return (principal * n / months.coerceAtLeast(1)).coerceAtLeast(0.0)
        val k = (1.0 + r).pow(paid)
        return (principal * k - payment() * ((k - 1.0) / r)).coerceAtLeast(0.0)
    }
    fun dueDate(index: Int): LocalDate = runCatching {
        YearMonth.from(LocalDate.parse(startDate)).plusMonths(index.toLong()).atDay(LocalDate.parse(startDate).dayOfMonth.coerceAtMost(YearMonth.from(LocalDate.parse(startDate)).plusMonths(index.toLong()).lengthOfMonth()))
    }.getOrElse { LocalDate.now().plusMonths(index.toLong()) }
}
data class Entry(val id: Long, val title: String, val amount: Double, val date: String, val category: String, val income: Boolean)
data class FinanceState(val debts: List<Debt> = emptyList(), val entries: List<Entry> = emptyList(), val dark: Boolean? = null)

private fun encode(s: FinanceState): String {
    val root = JSONObject()
    root.put("debts", JSONArray().apply { s.debts.forEach { d -> put(JSONObject().apply {
        put("id", d.id); put("name", d.name); put("type", d.type); put("principal", d.principal)
        put("rate", d.annualRate); put("months", d.months); put("start", d.startDate)
        put("paid", d.paid); put("manual", d.manualPayment); put("notes", d.notes)
    }) } })
    root.put("entries", JSONArray().apply { s.entries.forEach { e -> put(JSONObject().apply {
        put("id", e.id); put("title", e.title); put("amount", e.amount); put("date", e.date)
        put("category", e.category); put("income", e.income)
    }) } })
    return root.toString()
}
private fun decode(raw: String): FinanceState = runCatching {
    val root = JSONObject(raw)
    val debts = root.optJSONArray("debts") ?: JSONArray()
    val entries = root.optJSONArray("entries") ?: JSONArray()
    FinanceState(
        (0 until debts.length()).map { i -> debts.getJSONObject(i).let { o ->
            Debt(o.optLong("id"), o.optString("name"), o.optString("type"), o.optDouble("principal"),
                o.optDouble("rate"), o.optInt("months", 1), o.optString("start", today()),
                o.optInt("paid"), o.optDouble("manual"), o.optString("notes"))
        } },
        (0 until entries.length()).map { i -> entries.getJSONObject(i).let { o ->
            Entry(o.optLong("id"), o.optString("title"), o.optDouble("amount"), o.optString("date"),
                o.optString("category"), o.optBoolean("income"))
        } }
    )
}.getOrDefault(FinanceState())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            var state by remember { mutableStateOf(FinanceState()) }
            var loaded by remember { mutableStateOf(false) }
            val systemDark = isSystemInDarkTheme()
            LaunchedEffect(Unit) {
                val p = store.data.first()
                state = decode(p[DATA_KEY] ?: "")
                state = state.copy(dark = p[DARK_KEY])
                loaded = true
            }
            LaunchedEffect(state.debts, state.entries, loaded) {
                if (loaded) store.edit { it[DATA_KEY] = encode(state) }
            }
            val dark = state.dark ?: systemDark
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF61D6A5), secondary = androidx.compose.ui.graphics.Color(0xFF91B8F5), background = androidx.compose.ui.graphics.Color(0xFF101820), surface = androidx.compose.ui.graphics.Color(0xFF1B2933))
                else lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF087F5B), secondary = androidx.compose.ui.graphics.Color(0xFF2767A8), background = androidx.compose.ui.graphics.Color(0xFFF4F7F6))) {
                if (loaded) App(state, { state = it }, { v ->
                    state = state.copy(dark = v)
                    scope.launch { store.edit { p -> if (v == null) p.remove(DARK_KEY) else p[DARK_KEY] = v } }
                }) else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(s: FinanceState, update: (FinanceState) -> Unit, setTheme: (Boolean?) -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf("") }
    var editDebt by remember { mutableStateOf<Debt?>(null) }
    var editEntry by remember { mutableStateOf<Entry?>(null) }\n    var settingsOpen by remember { mutableStateOf(false) }
    val total = s.debts.sumOf { it.remaining() }
    val monthly = s.debts.sumOf { it.payment() }
    val monthEntries = s.entries.filter { runCatching { YearMonth.from(LocalDate.parse(it.date)) == YearMonth.now() }.getOrDefault(false) }
    val income = monthEntries.filter { it.income }.sumOf { it.amount }
    val expenses = monthEntries.filterNot { it.income }.sumOf { it.amount }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) dialog = "entry:$spoken"
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("pusulaBRÇ", fontWeight = FontWeight.Bold) }, actions = {
            IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Ayarlar") }
        }) },
        floatingActionButton = {
            if (page == 1 || page == 3) FloatingActionButton(onClick = { dialog = if (page == 1) "debt" else "entry" }) { Icon(Icons.Default.Add, "Ekle") }
            if (page == 3) {}
        },
        bottomBar = {
            NavigationBar { listOf("Özet", "Borçlar", "Takvim", "Hareketler", "Bütçe", "Rapor", "Asistan").forEachIndexed { i, label ->
                NavigationBarItem(selected = page == i, onClick = { page = i },
                    icon = { Icon(listOf(Icons.Default.Dashboard, Icons.Default.AccountBalance, Icons.Default.CalendarMonth, Icons.Default.SwapVert, Icons.Default.Payments, Icons.Default.BarChart, Icons.Default.AutoAwesome)[i], null) },
                    label = { Text(label, maxLines = 1) })
            } }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (page) {
                0 -> Dashboard(s.debts, total, monthly, income, expenses, { page = 1 })
                1 -> DebtsPage(s.debts, { editDebt = it; dialog = "editDebt" }, { id -> update(s.copy(debts = s.debts.filterNot { it.id == id })) }, { id -> update(s.copy(debts = s.debts.map { if (it.id == id) it.copy(paid = (it.paid + 1).coerceAtMost(it.months)) else it })) })
                2 -> CalendarPage(s.debts)
                3 -> EntriesPage(s.entries, { editEntry = it; dialog = "editEntry" }, { id -> update(s.copy(entries = s.entries.filterNot { it.id == id })) }, { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR"); putExtra(RecognizerIntent.EXTRA_PROMPT, "Gelir veya giderini söyle") }) })
                4 -> BudgetPage(income, expenses, monthly, s.entries)
                5 -> ReportsPage(s.debts, income, expenses, monthly, total, s.entries)
                6 -> AssistantPage(s.debts, total, monthly, income, expenses)
            }
        }
    }
    when {
        dialog == "debt" -> DebtDialog(null, { dialog = "" }, { d -> update(s.copy(debts = s.debts + d)); dialog = "" })
        dialog == "editDebt" && editDebt != null -> DebtDialog(editDebt, { dialog = "" }, { d -> update(s.copy(debts = s.debts.map { if (it.id == d.id) d else it })); dialog = "" })
        dialog == "entry" -> EntryDialog(null, { dialog = "" }, { e -> update(s.copy(entries = s.entries + e)); dialog = "" })
        dialog == "editEntry" && editEntry != null -> EntryDialog(editEntry, { dialog = "" }, { e -> update(s.copy(entries = s.entries.map { if (it.id == e.id) e else it })); dialog = "" })
        dialog.startsWith("entry:") -> EntryDialog(null, { dialog = "" }, { e -> update(s.copy(entries = s.entries + e.copy(title = dialog.removePrefix("entry:").take(80)))); dialog = "" })
    }
}

@Composable private fun PageTitle(title: String, subtitle: String = "") {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.secondary) }
}
@Composable private fun Metric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(15.dp)) { Text(title, style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(5.dp)); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) } }
}
@Composable private fun Dashboard(debts: List<Debt>, total: Double, monthly: Double, income: Double, expenses: Double, go: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { PageTitle("Finansal pusulan", "Bugün attığın küçük adımlar yarının özgürlüğü") }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp)) { Text("TOPLAM KALAN BORÇ", style = MaterialTheme.typography.labelLarge); Text(tl(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("${debts.size} kayıtlı borç") }
        } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Aylık taksit", tl(monthly), Modifier.weight(1f)); Metric("Bu ay gelir", tl(income), Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Bu ay gider", tl(expenses), Modifier.weight(1f)); Metric("Serbest bakiye", tl(income - expenses - monthly), Modifier.weight(1f)) } }
        item { Card { Column(Modifier.padding(16.dp)) { Text("Pusula önerisi", fontWeight = FontWeight.Bold); Text(if (debts.isEmpty()) "Borçlarını ekleyerek kişisel ödeme planını oluşturmaya başla." else "Asgari/zorunlu ödemeleri aksatma. Ekstra ödeme yapacaksan toplam maliyeti yüksek borçları önce değerlendirebilirsin."); TextButton(onClick = go) { Text("Borçlarımı yönet") } } } }
    }
}
@Composable private fun DebtsPage(debts: List<Debt>, edit: (Debt) -> Unit, delete: (Long) -> Unit, paid: (Long) -> Unit) {
    if (debts.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Borç eklemek için + düğmesine dokun.") }
    else LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { PageTitle("Borçlarım", "Krediler, kartlar, avans hesaplar ve diğer borçlar") }
        items(debts, key = { it.id }) { d ->
            Card { Column(Modifier.padding(15.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(d.name, fontWeight = FontWeight.Bold); Text(d.type, color = MaterialTheme.colorScheme.secondary) }; Text(tl(d.remaining()), fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(5.dp)); Text("Aylık ödeme: ${tl(d.payment())} • Faiz: %${d.annualRate} • ${d.paid}/${d.months} taksit")
                LinearProgressIndicator(progress = { d.paid.toFloat() / d.months.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Row { TextButton(onClick = { paid(d.id) }, enabled = d.paid < d.months) { Text("Taksit ödendi") }; TextButton(onClick = { edit(d) }) { Text("Düzenle") }; TextButton(onClick = { delete(d.id) }) { Text("Sil") } }
            } }
        }
    }
}
@Composable private fun CalendarPage(debts: List<Debt>) {
    val rows = debts.flatMap { d -> (d.paid until d.months).map { i -> Triple(d.dueDate(i), d, i + 1) } }.sortedBy { it.first }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { PageTitle("Ödeme takvimi", "Yaklaşan ve sonraki taksitler") }
        if (rows.isEmpty()) item { Text("Gösterilecek ödeme bulunmuyor.") }
        items(rows) { (date, debt, n) ->
            Card { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale("tr"))), fontWeight = FontWeight.Bold); Text("${debt.name} • $n/${debt.months}. taksit") }
                Text(tl(debt.payment()), fontWeight = FontWeight.Bold)
            } }
        }
    }
}
@Composable private fun EntriesPage(entries: List<Entry>, edit: (Entry) -> Unit, delete: (Long) -> Unit, voice: () -> Unit) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { PageTitle("Gelir ve giderler", "İşlemlerini kaydet ve kategorilere ayır"); Spacer(Modifier.weight(1f)); IconButton(onClick = voice) { Icon(Icons.Default.Mic, "Sesli giriş") } } }
        if (entries.isEmpty()) item { Text("Henüz işlem eklenmedi. + ile gelir veya gider ekle; mikrofonla sesli giriş deneyebilirsin.") }
        items(entries.sortedByDescending { it.date }, key = { it.id }) { e ->
            Card { Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(e.title, fontWeight = FontWeight.Bold); Text("${e.category} • ${e.date}", color = MaterialTheme.colorScheme.secondary) }
                Text((if (e.income) "+" else "−") + tl(e.amount), color = if (e.income) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                IconButton(onClick = { edit(e) }) { Icon(Icons.Default.Edit, "Düzenle") }
                IconButton(onClick = { delete(e.id) }) { Icon(Icons.Default.Delete, "Sil") }
            } }
        }
    }
}
@Composable private fun BudgetPage(income: Double, expenses: Double, monthly: Double, entries: List<Entry>) {
    val free = income - expenses - monthly
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Aylık bütçe", "Bu ayın kayıtlı gelir ve giderlerine göre") }
        item { Metric("Toplam gelir", tl(income), Modifier.fillMaxWidth()) }
        item { Metric("Toplam gider", tl(expenses), Modifier.fillMaxWidth()) }
        item { Metric("Borç taksitleri", tl(monthly), Modifier.fillMaxWidth()) }
        item { Metric("Taksitlerden sonra kalan", tl(free), Modifier.fillMaxWidth()) }
        item { Text("Gider kategorileri", fontWeight = FontWeight.Bold) }
        val groups = entries.filterNot { it.income }.groupBy { it.category }.mapValues { it.value.sumOf { e -> e.amount } }.toList().sortedByDescending { it.second }
        items(groups) { (category, amount) -> Card { Row(Modifier.fillMaxWidth().padding(13.dp)) { Text(category, Modifier.weight(1f)); Text(tl(amount), fontWeight = FontWeight.Bold) } } }
    }
}
@Composable private fun ReportsPage(debts: List<Debt>, income: Double, expenses: Double, monthly: Double, total: Double, entries: List<Entry>) {
    val debtTypes = debts.groupBy { it.type }.mapValues { it.value.sumOf { d -> d.remaining() } }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Raporlar", "Borç dağılımı ve aylık finans özeti") }
        item { Metric("Toplam borç", tl(total), Modifier.fillMaxWidth()) }
        item { Metric("Tahmini aylık borç ödemesi", tl(monthly), Modifier.fillMaxWidth()) }
        item { Metric("Aylık net nakit akışı", tl(income - expenses), Modifier.fillMaxWidth()) }
        item { Text("Borç türüne göre dağılım", fontWeight = FontWeight.Bold) }
        items(debtTypes.toList()) { (type, amount) -> Column { Row { Text(type, Modifier.weight(1f)); Text(tl(amount)) }; LinearProgressIndicator(progress = { if (total > 0) (amount / total).toFloat() else 0f }, Modifier.fillMaxWidth()) } }
        item { Text("Not: Bu rapor kayıtlı verilere dayanır. Banka ekstresi veya resmi ödeme planı değildir.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    }
}
@Composable private fun AssistantPage(debts: List<Debt>, total: Double, monthly: Double, income: Double, expenses: Double) {
    val available = income - expenses
    val highest = debts.maxByOrNull { it.annualRate }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { PageTitle("Finans asistanın", "Kayıtlarına göre çevrimdışı öneriler") }
        item { Advice("Genel durum", "Toplam kalan borcun ${tl(total)}. Kayıtlı aylık taksit tahminin ${tl(monthly)}.") }
        item { Advice("Bütçe kontrolü", if (available - monthly >= 0) "Gelir-gider farkından taksitleri çıkardığında ${tl(available - monthly)} kalıyor. Bir kısmını beklenmeyen giderler için ayırmayı düşün." else "Mevcut gelir-gider kayıtlarına göre taksit yükü bütçeyi aşıyor. Önce zorunlu giderleri gözden geçir ve alacaklı bankalarla gecikmeden iletişim kur.") }
        item { Advice("Faiz stratejisi", if (highest != null) "Kayıtlarındaki en yüksek yıllık faiz ${highest.name} borcunda (%${highest.annualRate}). Diğer zorunlu ödemeleri aksatmadan ekstra ödemeyi bu borca yöneltmek toplam faizi azaltabilir." else "Borç eklediğinde faiz oranına göre öncelik önerisi sunabilirim.") }
        item { Advice("Motivasyon", "Borçsuzluk bir günde değil, sürdürülebilir bir planla gelir. Her kaydettiğin ödeme ilerlemenin kanıtı.") }
        item { Text("Bu asistan kural tabanlıdır; bir bulut yapay zekâ servisine veya banka hesabına bağlı değildir. Finansal karar öncesi bankanın güncel koşullarını doğrula.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    }
}
@Composable private fun Advice(title: String, body: String) { Card { Column(Modifier.padding(15.dp)) { Text(title, fontWeight = FontWeight.Bold); Spacer(Modifier.height(5.dp)); Text(body) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DebtDialog(existing: Debt?, dismiss: () -> Unit, save: (Debt) -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var type by remember { mutableStateOf(existing?.type ?: "İhtiyaç kredisi") }
    var amount by remember { mutableStateOf(existing?.principal?.toString() ?: "") }
    var rate by remember { mutableStateOf(existing?.annualRate?.toString() ?: "0") }
    var months by remember { mutableStateOf(existing?.months?.toString() ?: "10") }
    var date by remember { mutableStateOf(existing?.startDate ?: today()) }
    var paid by remember { mutableStateOf(existing?.paid?.toString() ?: "0") }
    var manual by remember { mutableStateOf(existing?.manualPayment?.takeIf { it > 0 }?.toString() ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val types = listOf("İhtiyaç kredisi", "Taşıt kredisi", "Konut kredisi", "Kredi kartı", "Avans hesap / KMH", "Elden alınan borç", "Diğer")
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (existing == null) "Borç ekle" else "Borcu düzenle") },
        text = { Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Borç adı") }, modifier = Modifier.fillMaxWidth())
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(type, {}, readOnly = true, label = { Text("Borç türü") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded, { expanded = false }) { types.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { type = it; expanded = false }) } }
            }
            Field(amount, { amount = it }, "Güncel borç / anapara (₺)")
            Field(rate, { rate = it }, "Yıllık faiz (%)")
            Field(months, { months = it }, "Vade (ay)")
            Field(date, { date = it }, "İlk ödeme tarihi (YYYY-AA-GG)")
            Field(paid, { paid = it }, "Ödenmiş taksit sayısı")
            Field(manual, { manual = it }, "Aylık ödeme (opsiyonel; kart/KMH için)")
            OutlinedTextField(notes, { notes = it }, label = { Text("Notlar (opsiyonel)") }, modifier = Modifier.fillMaxWidth())
            Text("Kredi taksiti standart eşit taksit formülüyle tahmin edilir. Kart/KMH için bankanın gerçek asgari tutarı/faizi yerine manuel ödeme girmen önerilir.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton(onClick = {
            val a = num(amount); val m = months.toIntOrNull() ?: 0; val r = num(rate); val p = paid.toIntOrNull() ?: 0
            val validDate = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
            if (name.isNotBlank() && a > 0 && m > 0 && r >= 0 && p in 0..m && validDate) save(Debt(existing?.id ?: System.currentTimeMillis(), name, type, a, r, m, date, p, num(manual), notes))
        }) { Text("Kaydet") } }, dismissButton = { TextButton(onClick = dismiss) { Text("İptal") } })
}
@Composable private fun Field(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (label.contains("tarih")) KeyboardType.Text else KeyboardType.Decimal))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun EntryDialog(existing: Entry?, dismiss: () -> Unit, save: (Entry) -> Unit) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.toString() ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: today()) }
    var category by remember { mutableStateOf(existing?.category ?: "Diğer") }
    var income by remember { mutableStateOf(existing?.income ?: false) }
    val categories = if (income) listOf("Maaş", "Ek gelir", "Yatırım", "Diğer") else listOf("Kira", "Fatura", "Market", "Ulaşım", "Sağlık", "Eğitim", "Diğer")
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (existing == null) "İşlem ekle" else "İşlemi düzenle") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { FilterChip(income, { income = true; category = "Maaş" }, label = { Text("Gelir") }); Spacer(Modifier.width(8.dp)); FilterChip(!income, { income = false; category = "Diğer" }, label = { Text("Gider") }) }
            OutlinedTextField(title, { title = it }, label = { Text("Açıklama") }, modifier = Modifier.fillMaxWidth())
            Field(amount, { amount = it }, "Tutar (₺)")
            Field(date, { date = it }, "Tarih (YYYY-AA-GG)")
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(category, {}, readOnly = true, label = { Text("Kategori") }, modifier = Modifier.menuAnchor().fillMaxWidth())
                ExposedDropdownMenu(expanded, { expanded = false }) { categories.forEach { DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false }) } }
            }
        } },
        confirmButton = { TextButton(onClick = {
            val a = num(amount); val valid = runCatching { LocalDate.parse(date); true }.getOrDefault(false)
            if (title.isNotBlank() && a > 0 && valid) save(Entry(existing?.id ?: System.currentTimeMillis(), title, a, date, category, income))
        }) { Text("Kaydet") } }, dismissButton = { TextButton(onClick = dismiss) { Text("İptal") } })
}

@Composable private fun SettingsPagePlaceholder() {}
