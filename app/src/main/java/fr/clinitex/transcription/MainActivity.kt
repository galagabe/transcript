package fr.clinitex.transcription

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import fr.clinitex.transcription.data.SharedStore
import fr.clinitex.transcription.dictionary.CommonDictionaryWorker
import fr.clinitex.transcription.engine.SherpaRecognizerHolder
import fr.clinitex.transcription.meetings.AudioPurgeWorker
import fr.clinitex.transcription.model.ModelManager
import fr.clinitex.transcription.model.SpeechModels
import fr.clinitex.transcription.theme.AppChrome
import fr.clinitex.transcription.theme.ClinitexPalette
import fr.clinitex.transcription.theme.MuesliTheme
import fr.clinitex.transcription.ui.launch.LaunchWarmupScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

  companion object {
    /** iOS LaunchWarmupContainer: max time the warmup screen stays up. */
    private const val WARMUP_DISPLAY_CAP_MS = 5_000L
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Lot 5: audio kept 7 days — purge now, then once a day.
    AudioPurgeWorker.schedule(applicationContext)
    // Lot 6a: common Clinitex dictionary — download when online, then every 12 h.
    CommonDictionaryWorker.schedule(applicationContext)

    // Light-only app: dark system-bar icons whatever the system dark mode
    // (switched to light icons below while the full-blue recording screen shows).
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
    )

    setContent {
      MuesliTheme {
        val view = LocalView.current
        val onBlue = AppChrome.background == ClinitexPalette.Blue
        SideEffect {
          WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !onBlue
            isAppearanceLightNavigationBars = !onBlue
          }
        }

        Surface(
          modifier = Modifier.fillMaxSize(),
          // Painted behind the system bars: follows the current screen's background.
          color = AppChrome.background
        ) {
          val context = this@MainActivity
          val shouldWarmup = remember {
            // Post-onboarding launches only, and only when the model exists
            // and isn't already warm (iOS shouldShowLaunchWarmup parity)
            SharedStore(context).isOnboardingCompleted &&
              ModelManager(context).isDownloaded() &&
              SherpaRecognizerHolder.warmState != SherpaRecognizerHolder.WarmState.READY
          }
          var showWarmup by remember { mutableStateOf(shouldWarmup) }
          var warmState by remember { mutableStateOf(SherpaRecognizerHolder.warmState) }

          LaunchedEffect(Unit) {
            if (!shouldWarmup) return@LaunchedEffect
            SherpaRecognizerHolder.prewarmAsync(context)
            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < WARMUP_DISPLAY_CAP_MS) {
              warmState = SherpaRecognizerHolder.warmState
              if (warmState == SherpaRecognizerHolder.WarmState.READY ||
                warmState == SherpaRecognizerHolder.WarmState.FAILED
              ) {
                break
              }
              delay(120)
            }
            showWarmup = false
          }

          Box {
            MainNavigation()
            androidx.compose.animation.AnimatedVisibility(
              visible = showWarmup,
              exit = fadeOut(animationSpec = tween(320)),
            ) {
              LaunchWarmupScreen(
                warmState = warmState,
                modelName = SpeechModels.selected(context).shortName,
                versionText = "v${packageManager.getPackageInfo(packageName, 0).versionName} (${packageManager.getPackageInfo(packageName, 0).longVersionCode})"
              )
            }
          }
        }
      }
    }
  }
}
