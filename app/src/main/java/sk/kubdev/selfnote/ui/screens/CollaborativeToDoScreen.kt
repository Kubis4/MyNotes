package sk.kubdev.selfnote.ui.screens

import androidx.compose.runtime.*
import androidx.navigation.NavController
import sk.kubdev.selfnote.NoteViewModel

@Composable
fun CollaborativeToDoScreen(
    collaborativeNoteId: String,
    navController: NavController,
    viewModel: NoteViewModel
) {
    // Simply delegate to ToDoListScreen with collaborative parameters
    ToDoListScreen(
        noteIdArg = 0,
        navController = navController,
        viewModel = viewModel,
        isCollaborative = true,
        collaborativeNoteId = collaborativeNoteId
    )
}
