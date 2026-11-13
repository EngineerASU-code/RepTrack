package app.reptrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ink = Color(0xFF12101C)
private val panel = Color(0xFF211C31)
private val purple = Color(0xFF8957F6)
private val days = listOf("Push", "Legs", "Pull", "Up")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { RepTrackApp() } }
}

@Composable
private fun RepTrackApp() {
    var selected by remember { mutableStateOf("Главная") }
    var next by remember { mutableIntStateOf(0) }
    var active by remember { mutableStateOf(false) }
    val exercises = when (days[next]) {
        "Push" -> listOf("Жим угловой", "Хаммер низ груди", "Тяга к поясу", "Трицепс", "Плечи стоя, задняя дельта", "Бицепс Скотт")
        "Legs" -> listOf("Разминка, ходьба в гору", "Жим ногами", "Сгибания ног", "Разгибания ног", "Подъём на носки стоя")
        "Pull" -> listOf("Подтягивания / тяга", "Тяга нижнего блока", "Тяга спины Hammer", "Тяга на трицепс", "Молотки с гантелями", "Плечи Smith")
        else -> listOf("Жим груди в наклоне", "Smith дельта", "Разводка груди Butterfly", "Тяга верхнего блока", "Бицепс стоя со штангой", "Беговая дорожка")
    }
    MaterialTheme(colorScheme = darkColorScheme(background = ink, surface = panel, primary = purple)) {
        Scaffold(containerColor = ink, bottomBar = { NavigationBar(containerColor = panel) { listOf("Главная", "Зал", "План", "Прогресс", "История").forEach { NavigationBarItem(selected == it, { selected = it }, icon = {}, label = { Text(it) }) } } }) { pad ->
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text("RepTrack", style = MaterialTheme.typography.headlineMedium, color = Color.White) }
                item { Card(colors = CardDefaults.cardColors(containerColor = panel)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (active) "Тренировка продолжается" else "Следующая тренировка · ${days[next]}", color = Color.White, style = MaterialTheme.typography.titleLarge)
                    Text("Push → Legs → Pull → Up", color = Color.LightGray)
                    Button(onClick = { active = !active; if (!active) next = (next + 1) % days.size }, modifier = Modifier.fillMaxWidth()) { Text(if (active) "Завершить тренировку" else "Начать тренировку") }
                } } }
                item { Text(if (selected == "Зал" || active) "Упражнения" else selected, color = Color.White, style = MaterialTheme.typography.titleLarge) }
                if (selected == "Зал" || active) items(exercises) { exercise -> var sets by remember { mutableIntStateOf(3) }; Card(colors = CardDefaults.cardColors(containerColor = panel)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exercise, color = Color.White, style = MaterialTheme.typography.titleMedium)
                    repeat(sets) { i -> Text("${i + 1}     — кг       — повт.       ○", color = Color.LightGray) }
                    TextButton(onClick = { sets++ }) { Text("+ Добавить подход") }
                } } }
                else if (selected == "План") items(days) { day -> Card(colors = CardDefaults.cardColors(containerColor = panel)) { Text(day, Modifier.padding(18.dp), color = Color.White) } }
                else item { Text("Тренировок пока нет. Начни первую сессию, чтобы увидеть историю и прогресс.", color = Color.LightGray) }
            }
        }
    }
}
