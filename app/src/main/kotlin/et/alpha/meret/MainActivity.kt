package et.alpha.meret

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import et.alpha.meret.core.designsystem.theme.MeretTheme
import et.alpha.meret.ui.MeretApp

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MeretTheme {
                MeretApp()
            }
        }
    }
}
