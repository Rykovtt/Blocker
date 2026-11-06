package app.cleanweb.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import app.cleanweb.data.ThemeMode
import app.cleanweb.ui.AreaChart
import app.cleanweb.ui.CleanWebTheme
import app.cleanweb.ui.Cw
import app.cleanweb.ui.GradientButton
import app.cleanweb.ui.GradientRing
import app.cleanweb.ui.NeuButton
import app.cleanweb.ui.NeuCard
import app.cleanweb.ui.NeuChip
import app.cleanweb.ui.NeuGroup
import app.cleanweb.ui.NeuRow
import app.cleanweb.ui.NeuSwitch
import app.cleanweb.ui.RowDivider
import app.cleanweb.ui.ScreenTitle
import app.cleanweb.ui.SectionLabel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the design components to PNG (build/outputs/roborazzi) for a visual check. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DesignScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Composable
    private fun Sample() {
        val c = Cw.colors
        Column(Modifier.fillMaxSize().background(c.background).padding(top = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                ScreenTitle("Захист", Modifier.weight(1f))
            }
            NeuCard(Modifier.fillMaxWidth().padding(20.dp), radius = 36.dp, padding = 24.dp) {
                Text("ЗАХИСТ УВІМКНЕНО", style = MaterialTheme.typography.labelLarge, color = c.textMuted)
                Spacer(Modifier.height(12.dp))
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    GradientRing(0.34f, Modifier.size(200.dp))
                    androidx.compose.foundation.layout.Box(Modifier.size(136.dp).then(Modifier), contentAlignment = Alignment.Center) {
                        Text("УВІМК", style = MaterialTheme.typography.titleLarge, color = c.text)
                    }
                }
                GradientButton("Пауза на 5 хвилин", {}, Modifier.fillMaxWidth().padding(top = 16.dp))
            }
            NeuCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(0.4f)) {
                        Text("Останні 7 днів", color = c.textMuted)
                        Text("▲ 2 214", style = MaterialTheme.typography.titleMedium, color = c.text)
                    }
                    AreaChart(listOf(120, 340, 210, 280, 190, 520, 410), Modifier.weight(0.6f).height(72.dp))
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeuChip(true, "День") {}
                NeuChip(false, "Тиждень") {}
                NeuChip(false, "Місяць") {}
            }
            NeuButton("Заблокувати рекламу", {}, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), icon = Icons.Filled.Add, accentIcon = true)
            NeuButton("Вимкнути сповіщення сайтів", {}, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), icon = Icons.Filled.Notifications)
            SectionLabel("Списки фільтрів")
            NeuGroup {
                NeuRow("HaGeZi Pop-Up Ads", "Правил: 76592 · оновлено 06.11.26") { NeuSwitch(true, {}) }
                RowDivider()
                NeuRow("HaGeZi Multi PRO", "Ще не завантажено") { NeuSwitch(false, {}) }
            }
        }
    }

    @Test fun light() {
        compose.setContent { CleanWebTheme(ThemeMode.LIGHT) { Sample() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/design_light.png")
    }

    @Test fun dark() {
        compose.setContent { CleanWebTheme(ThemeMode.DARK) { Sample() } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/design_dark.png")
    }
}
