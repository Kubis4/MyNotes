package sk.kubdev.selfnote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel // <-- ADD THIS IMPORT
import sk.kubdev.selfnote.ui.theme.SelfNoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SelfNoteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // --- FIX: Create the ViewModel here and pass it down ---
                    val noteViewModel: NoteViewModel = viewModel()
                    AppNavigation(viewModel = noteViewModel)
                }
            }
        }
    }
}