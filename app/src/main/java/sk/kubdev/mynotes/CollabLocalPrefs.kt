package sk.kubdev.mynotes

import android.content.Context

// Personal, device-local preferences for collaborative notes. A collaborative note's
// Firestore document is shared by everyone, so anything "just for me" - like which
// color I want its card/editor to use - lives here instead, keyed by the note id.
// Other collaborators keep their own colors.
object CollabLocalPrefs {
    private fun prefs(context: Context) =
        context.getSharedPreferences("collab_local_prefs", Context.MODE_PRIVATE)

    // Default 17 = Blue Grey - a neutral tone that keeps shared notes visually
    // distinct from the Light Yellow default of local notes without pushing a
    // strong hue on every collaborator.
    fun getColorIndex(context: Context, collaborativeId: String): Int =
        prefs(context).getInt("color_$collaborativeId", 17)

    fun setColorIndex(context: Context, collaborativeId: String, colorIndex: Int) {
        prefs(context).edit().putInt("color_$collaborativeId", colorIndex).apply()
    }

    // Same idea as the color index above: the abstract background pattern is a
    // per-device preference, not a shared document field, so each collaborator can
    // pick their own without conflicting with anyone else's. 0 = none.
    fun getPatternIndex(context: Context, collaborativeId: String): Int =
        prefs(context).getInt("pattern_$collaborativeId", 0)

    fun setPatternIndex(context: Context, collaborativeId: String, patternIndex: Int) {
        prefs(context).edit().putInt("pattern_$collaborativeId", patternIndex).apply()
    }
}
