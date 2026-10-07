package com.vmax.hyperboost

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// ---------- Тема в стиле HyperOS ----------

class HyperColors(
    val bg: Color, val card: Color, val text: Color, val sub: Color,
    val accent: Color, val track: Color, val warn: Color
)

private val LightColors = HyperColors(
    Color(0xFFF2F3F5), Color.White, Color(0xFF111111), Color(0xFF8A8D93),
    Color(0xFF0D84FF), Color(0xFFE6E8EC), Color(0xFFFF9500)
)
private val DarkColors = HyperColors(
    Color.Black, Color(0xFF1C1C1E), Color.White, Color(0xFF8E8E93),
    Color(0xFF3482FF), Color(0xFF2C2C2E), Color(0xFFFF9F0A)
)
val LocalHyper = staticCompositionLocalOf { LightColors }

@Composable
fun HyperTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalHyper provides if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

// ---------- Корневой экран ----------

@Composable
fun AppScreen(onToggle: (Boolean) -> Unit) {
    val c = LocalHyper.current
    val st by Controller.state.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val sourceLayer = rememberGraphicsLayer()

    Box(Modifier.fillMaxSize()) {
        // Всё содержимое записывается в слой, чтобы нижняя панель могла размывать то, что под ней
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    sourceLayer.record(
                        density = this,
                        layoutDirection = layoutDirection,
                        size = IntSize(size.width.toInt(), size.height.toInt())
                    ) { this@drawWithContent.drawContent() }
                    drawLayer(sourceLayer)
                }
                .background(c.bg)
        ) {
            Crossfade(targetState = tab, label = "tab") {
                if (it == 0) BoostScreen(st, onToggle) else DevicesScreen(st)
            }
        }
        GlassBar(tab, { tab = it }, sourceLayer, Modifier.align(Alignment.BottomCenter))
    }
}

// ---------- Базовые элементы ----------

@Composable
fun HCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalHyper.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(c.card)
            .padding(18.dp),
        content = content
    )
}

@Composable
fun HyperSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = LocalHyper.current
    val bg by animateColorAsState(if (checked) c.accent else c.track, label = "sw")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, label = "swx")
    Box(
        Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable { onChange(!checked) }
    ) {
        Box(
            Modifier
                .offset(x, 2.dp)
                .size(28.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
fun HyperSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalHyper.current
    var w by remember { mutableFloatStateOf(1f) }
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(c.track)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / w).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / w).coerceIn(0f, 1f))
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value.coerceIn(0f, 1f))
                .background(c.accent)
        )
    }
}

fun kindIcon(k: Kind): ImageVector = when (k) {
    Kind.PHONE -> Icons.Rounded.Smartphone
    Kind.BLUETOOTH -> Icons.Rounded.Bluetooth
    Kind.WIRED -> Icons.Rounded.Headphones
    Kind.USB -> Icons.Rounded.Usb
    Kind.HDMI -> Icons.Rounded.Tv
    Kind.HEARING -> Icons.Rounded.Hearing
}

@Composable
fun IconBubble(kind: Kind, active: Boolean) {
    val c = LocalHyper.current
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(if (active) c.accent else c.track),
        contentAlignment = Alignment.Center
    ) {
        Icon(kindIcon(kind), null, tint = if (active) Color.White else c.sub, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun Title(text: String) {
    val c = LocalHyper.current
    Text(
        text, color = c.text, fontSize = 34.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 6.dp, top = 28.dp, bottom = 18.dp)
    )
}

@Composable
fun SectionLabel(text: String) {
    val c = LocalHyper.current
    Text(
        text, color = c.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 10.dp, top = 18.dp, bottom = 8.dp)
    )
}

// ---------- Экран «Усилитель» ----------

@Composable
fun BoostScreen(st: UiState, onToggle: (Boolean) -> Unit) {
    val c = LocalHyper.current
    val active = st.devices.firstOrNull { it.isActive }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Title("Усилитель громкости")

        HCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active != null) IconBubble(active.kind, true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Сейчас подключено", color = c.sub, fontSize = 13.sp)
                    Text(
                        active?.name ?: "Нет активного устройства",
                        color = c.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                    )
                    if (active != null) Text(active.typeLabel, color = c.sub, fontSize = 13.sp)
                }
            }
        }

        HCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Усиление", color = c.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Работает для всех приложений", color = c.sub, fontSize = 13.sp)
                }
                HyperSwitch(st.enabled, onToggle)
            }
            Spacer(Modifier.height(22.dp))
            val boost = active?.boost ?: 0
            Text(
                "+$boost%", color = if (st.enabled) c.accent else c.sub,
                fontSize = 56.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                "до +15 дБ", color = c.sub, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(18.dp))
            HyperSlider(
                value = boost / 100f,
                onChange = { v -> if (st.enabled && active != null) Controller.setBoost(active.key, (v * 100).roundToInt()) },
                modifier = Modifier.alpha(if (st.enabled && active != null) 1f else 0.4f)
            )
            if (active != null) {
                Spacer(Modifier.height(10.dp))
                Text("Настройка запоминается для «${active.name}»", color = c.sub, fontSize = 12.sp)
            }
        }

        HCard {
            Text("Осторожно", color = c.warn, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Сильное усиление искажает звук и может повредить динамики и слух. Начинайте с малого.",
                color = c.sub, fontSize = 14.sp
            )
            st.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = c.warn, fontSize = 13.sp)
            }
        }
    }
}

// ---------- Экран «Устройства» ----------

@Composable
fun DevicesScreen(st: UiState) {
    val c = LocalHyper.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Title("Устройства")
        SectionLabel("ПОДКЛЮЧЕНЫ")
        if (st.devices.isEmpty()) {
            HCard { Text("Устройства вывода не найдены", color = c.sub, fontSize = 15.sp) }
        }
        st.devices.forEach { d ->
            HCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBubble(d.kind, d.isActive)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.name, color = c.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(d.typeLabel, color = c.sub, fontSize = 13.sp)
                    }
                    if (d.isActive) {
                        Text(
                            "Активно", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.accent)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        HyperSlider(d.boost / 100f, { v -> Controller.setBoost(d.key, (v * 100).roundToInt()) })
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("+${d.boost}%", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(56.dp))
                }
            }
        }
        if (st.paired.isNotEmpty()) {
            SectionLabel("СОПРЯЖЕНЫ, НО НЕ ПОДКЛЮЧЕНЫ")
            HCard {
                st.paired.forEachIndexed { i, n ->
                    if (i > 0) Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Bluetooth, null, tint = c.sub, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(n, color = c.text, fontSize = 16.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------- Нижняя панель Liquid Glass ----------

@Composable
fun GlassBar(tab: Int, onTab: (Int) -> Unit, source: GraphicsLayer, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val blurLayer = rememberGraphicsLayer()
    var pos by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(36.dp)
    val tintTop = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.50f)
    val tintBottom = if (dark) Color.White.copy(alpha = 0.04f) else Color.White.copy(alpha = 0.22f)

    Row(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp, vertical = 12.dp)
            .height(70.dp)
            .shadow(24.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x44000000))
            .clip(shape)
            .onGloballyPositioned { pos = it.positionInRoot() }
            .drawBehind {
                val s = IntSize(size.width.toInt(), size.height.toInt())
                blurLayer.renderEffect = BlurEffect(60f, 60f, TileMode.Clamp)
                blurLayer.record(density = this, layoutDirection = layoutDirection, size = s) {
                    translate(-pos.x, -pos.y) { drawLayer(source) }
                }
                drawLayer(blurLayer)
            }
            .background(Brush.verticalGradient(listOf(tintTop, tintBottom)))
            .border(
                1.2.dp,
                Brush.linearGradient(listOf(Color.White.copy(0.95f), Color.White.copy(0.08f), Color.White.copy(0.55f))),
                shape
            )
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassTab(Icons.AutoMirrored.Rounded.VolumeUp, "Усилитель", tab == 0, dark, Modifier.weight(1f)) { onTab(0) }
        GlassTab(Icons.Rounded.Devices, "Устройства", tab == 1, dark, Modifier.weight(1f)) { onTab(1) }
    }
}

@Composable
private fun GlassTab(
    icon: ImageVector, label: String, selected: Boolean, dark: Boolean,
    modifier: Modifier, onClick: () -> Unit
) {
    val c = LocalHyper.current
    val lens by animateColorAsState(
        if (selected) (if (dark) Color.White.copy(0.20f) else Color.White.copy(0.60f)) else Color.Transparent,
        label = "lens"
    )
    val tint = if (selected) c.accent else c.sub
    Column(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(30.dp))
            .background(lens)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
