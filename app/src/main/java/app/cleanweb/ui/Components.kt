package app.cleanweb.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SectionTitle(text: String) = SectionLabel(text)

/** Setting with a kit-style toggle. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    NeuRow(title, subtitle, onClick = { onChange(!checked) }) {
        NeuSwitch(checked = checked, onCheckedChange = onChange)
    }
}

/** A card that points at a problem or tip and offers one action. */
@Composable
fun AlertCard(title: String, body: String, action: String?, onAction: () -> Unit, error: Boolean = false) {
    val c = Cw.colors
    NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconTile(
                icon = if (error) Icons.Filled.Warning else Icons.Filled.Info,
                size = 40.dp,
                brush = if (error) Brush.linearGradient(listOf(c.warning, c.blocked)) else null,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = c.text)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
        }
        if (action != null) {
            GradientButton(action, onAction, Modifier.fillMaxWidth().padding(top = 16.dp))
        }
    }
}

val BlockedColor: Color @Composable get() = Cw.colors.blocked
val AllowedColor: Color @Composable get() = Cw.colors.allowed
val WhitelistColor: Color @Composable get() = Cw.colors.accentStart
