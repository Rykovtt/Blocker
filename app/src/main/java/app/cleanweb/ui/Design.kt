package app.cleanweb.ui

import android.graphics.Paint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cleanweb.R

// ---------------------------------------------------------------------------------------------
// Palette: soft grey-blue "neumorphic" surfaces with a blue → violet accent gradient.
// ---------------------------------------------------------------------------------------------

@Immutable
data class CwColors(
    val background: Color,
    val surfaceTop: Color,
    val surfaceBottom: Color,
    val shadowDark: Color,
    val shadowLight: Color,
    val text: Color,
    val textMuted: Color,
    val outline: Color,
    val track: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val blocked: Color,
    val allowed: Color,
    val warning: Color,
    val isDark: Boolean,
) {
    val accent: Brush get() = Brush.linearGradient(listOf(accentStart, accentEnd))
    val accentHorizontal: Brush get() = Brush.horizontalGradient(listOf(accentStart, accentEnd))
    val surface: Brush get() = Brush.verticalGradient(listOf(surfaceTop, surfaceBottom))
}

val LightCw = CwColors(
    background = Color(0xFFE3E9EF),
    surfaceTop = Color(0xFFFBFCFD),
    surfaceBottom = Color(0xFFEEF2F6),
    shadowDark = Color(0x5591A2B8),
    shadowLight = Color(0xE6FFFFFF),
    text = Color(0xFF353A42),
    textMuted = Color(0xFF8790A0),
    outline = Color(0xFFB9C3CF),
    track = Color(0xFFD7DEE6),
    accentStart = Color(0xFF2A97E8),
    accentEnd = Color(0xFF3B2DF2),
    blocked = Color(0xFFE5484D),
    allowed = Color(0xFF1FA971),
    warning = Color(0xFFE8912A),
    isDark = false,
)

val DarkCw = CwColors(
    background = Color(0xFF1C2028),
    surfaceTop = Color(0xFF282E38),
    surfaceBottom = Color(0xFF22272F),
    shadowDark = Color(0x99090B0F),
    shadowLight = Color(0x22FFFFFF),
    text = Color(0xFFE7EBF1),
    textMuted = Color(0xFF98A2B2),
    outline = Color(0xFF3F4755),
    track = Color(0xFF353C48),
    accentStart = Color(0xFF3AA4F2),
    accentEnd = Color(0xFF6A5CFF),
    blocked = Color(0xFFFF6B70),
    allowed = Color(0xFF3CCB8E),
    warning = Color(0xFFF2A54A),
    isDark = true,
)

val LocalCw = staticCompositionLocalOf { LightCw }

object Cw {
    val colors: CwColors @Composable get() = LocalCw.current
}

val Montserrat = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
)

// ---------------------------------------------------------------------------------------------
// Convex surface: two soft shadows (dark bottom-right, light top-left) under a gradient face.
// ---------------------------------------------------------------------------------------------

fun Modifier.neu(
    colors: CwColors,
    radius: Dp = 28.dp,
    elevation: Dp = 8.dp,
    face: Brush? = null,
): Modifier = this.drawBehind {
    val r = radius.toPx()
    val e = elevation.toPx()
    if (e > 0f) {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors.surfaceBottom.toArgb() }
            paint.setShadowLayer(e * 1.8f, e * 0.7f, e * 0.9f, colors.shadowDark.toArgb())
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
            paint.setShadowLayer(e * 1.4f, -e * 0.6f, -e * 0.6f, colors.shadowLight.toArgb())
            canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, paint)
        }
    }
    drawRoundRect(face ?: colors.surface, cornerRadius = CornerRadius(r, r))
}

/** Coloured glow under gradient elements (buttons, the active switch). */
fun Modifier.glow(color: Color, radius: Dp, elevation: Dp = 10.dp): Modifier = this.drawBehind {
    val r = radius.toPx()
    val e = elevation.toPx()
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = android.graphics.Color.TRANSPARENT }
        paint.setShadowLayer(e * 1.5f, 0f, e * 0.8f, color.copy(alpha = 0.38f).toArgb())
        canvas.nativeCanvas.drawRoundRect(e * 0.3f, e * 0.3f, size.width - e * 0.3f, size.height, r, r, paint)
    }
}

// ---------------------------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------------------------

@Composable
fun NeuCard(
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    padding: Dp = 20.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Cw.colors
    Column(
        modifier
            .neu(c, radius)
            .clip(RoundedCornerShape(radius))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/** Large page title like the kit's "Login". */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Normal),
        color = Cw.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 10.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
        color = Cw.colors.textMuted,
    )
}

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Cw.colors
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .defaultMinSize(minHeight = 54.dp)
            .then(if (enabled) Modifier.glow(c.accentEnd, 27.dp) else Modifier)
            .clip(shape)
            .background(if (enabled) c.accentHorizontal else Brush.horizontalGradient(listOf(c.track, c.track)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = if (enabled) Color.White else c.textMuted, style = MaterialTheme.typography.titleMedium, maxLines = 2)
    }
}

/** Raised white pill with an optional gradient icon tile, like the kit's "Add Friend" / "Share". */
@Composable
fun NeuButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    painter: Painter? = null,
    accentIcon: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Cw.colors
    Row(
        modifier
            .defaultMinSize(minHeight = 56.dp)
            .neu(c, 28.dp, elevation = 6.dp)
            .clip(RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null || painter != null) {
            IconTile(icon, painter, accent = accentIcon, size = 40.dp)
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text,
            color = if (enabled) c.text else c.textMuted,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 14.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rounded-square icon holder: gradient (accent) or plain. */
@Composable
fun IconTile(
    icon: ImageVector? = null,
    painter: Painter? = null,
    accent: Boolean = true,
    size: Dp = 44.dp,
    brush: Brush? = null,
) {
    val c = Cw.colors
    Box(
        Modifier
            .size(size)
            .then(if (accent) Modifier.glow(c.accentEnd, size / 3, 6.dp) else Modifier)
            .clip(RoundedCornerShape(size / 3))
            .background(brush ?: if (accent) c.accent else Brush.linearGradient(listOf(c.track, c.track))),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (accent || brush != null) Color.White else c.textMuted
        if (icon != null) Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
        else if (painter != null) Icon(painter, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
fun NeuIconButton(onClick: () -> Unit, contentDescription: String?, icon: ImageVector) {
    val c = Cw.colors
    Box(
        Modifier
            .size(46.dp)
            .neu(c, 23.dp, elevation = 5.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription, tint = c.textMuted, modifier = Modifier.size(22.dp)) }
}

/** The kit's pill toggle: gradient track with white knob when on, grey inset when off. */
@Composable
fun NeuSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true) {
    val c = Cw.colors
    val knobOffset by animateDpAsState(if (checked) 26.dp else 0.dp, label = "knob")
    val trackAlpha by animateFloatAsState(if (checked) 1f else 0f, label = "track")
    Box(
        Modifier
            .requiredSize(width = 58.dp, height = 32.dp)
            .then(if (checked) Modifier.glow(c.accentEnd, 16.dp, 6.dp) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(c.track)
            .background(c.accentHorizontal, alpha = trackAlpha)
            .clickable(
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(knobOffset.roundToPx(), 0) }
                .size(26.dp)
                .shadow(3.dp, CircleShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFEFF2F6))), CircleShape),
        )
    }
}

/** Pill chip: gradient when selected (the kit's "2019"), raised white otherwise. */
@Composable
fun NeuChip(selected: Boolean, text: String, onClick: () -> Unit) {
    val c = Cw.colors
    val textColor by animateColorAsState(if (selected) Color.White else c.textMuted, label = "chip")
    Box(
        Modifier
            .height(40.dp)
            .then(if (selected) Modifier.glow(c.accentEnd, 20.dp, 6.dp) else Modifier.neu(c, 20.dp, elevation = 4.dp))
            .clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(c.accent) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = textColor, style = MaterialTheme.typography.labelLarge) }
}

/** A row inside a card: title, optional subtitle, trailing content. */
@Composable
fun NeuRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    subtitleColor: Color? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = Cw.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = subtitleColor ?: c.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.dp)
            .background(Cw.colors.outline.copy(alpha = 0.35f)),
    )
}

/** Card holding rows separated by thin dividers. */
@Composable
fun NeuGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    NeuCard(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), padding = 0.dp, content = { Spacer(Modifier.height(4.dp)); content(); Spacer(Modifier.height(4.dp)) })
}

@Composable
fun cwTextFieldColors(): TextFieldColors {
    val c = Cw.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accentEnd,
        unfocusedBorderColor = c.outline.copy(alpha = 0.6f),
        focusedContainerColor = c.surfaceTop,
        unfocusedContainerColor = c.surfaceTop,
        cursorColor = c.accentEnd,
        focusedTextColor = c.text,
        unfocusedTextColor = c.text,
        focusedLeadingIconColor = c.accentEnd,
        unfocusedLeadingIconColor = c.textMuted,
        focusedPlaceholderColor = c.textMuted,
        unfocusedPlaceholderColor = c.textMuted,
        focusedLabelColor = c.accentEnd,
        unfocusedLabelColor = c.textMuted,
    )
}

val FieldShape = RoundedCornerShape(20.dp)

// ---------------------------------------------------------------------------------------------
// Charts
// ---------------------------------------------------------------------------------------------

/** Gradient ring like the kit's "75%" gauge. [fraction] 0..1. */
@Composable
fun GradientRing(fraction: Float, modifier: Modifier, stroke: Dp = 14.dp, active: Boolean = true) {
    val c = Cw.colors
    val anim by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "ring")
    Canvas(modifier) {
        val s = stroke.toPx()
        val inset = s / 2
        val arcSize = Size(size.width - s, size.height - s)
        drawArc(c.track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        if (anim > 0f) {
            val brush = if (active) {
                Brush.linearGradient(listOf(c.accentStart, c.accentEnd), start = Offset(size.width, 0f), end = Offset(0f, size.height))
            } else {
                Brush.linearGradient(listOf(c.outline, c.outline))
            }
            drawArc(brush, -90f, 360f * anim, false, Offset(inset, inset), arcSize, style = Stroke(s, cap = StrokeCap.Round))
        }
    }
}

/** Smooth area chart with a gradient line and fill, like the kit's "Last 7 days". */
@Composable
fun AreaChart(values: List<Int>, modifier: Modifier, highlightLast: Boolean = true) {
    val c = Cw.colors
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1).toFloat()
        val top = 10.dp.toPx()
        val h = size.height - top
        val stepX = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v -> Offset(i * stepX, top + h - h * (v / max)) }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(c.accentEnd.copy(alpha = 0.28f), c.accentStart.copy(alpha = 0.02f))))
        drawPath(line, Brush.horizontalGradient(listOf(c.accentStart, c.accentEnd)), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        val peak = pts.withIndex().maxByOrNull { values[it.index] }!!.value
        val dot = if (highlightLast) pts.last() else peak
        drawCircle(Color.White, 8.dp.toPx(), dot)
        drawCircle(Brush.linearGradient(listOf(c.accentStart, c.accentEnd), dot - Offset(6f, 6f), dot + Offset(6f, 6f)), 5.dp.toPx(), dot)
    }
}

