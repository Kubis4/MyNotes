package sk.kubisdev.mynotes.settings.model

import sk.kubisdev.mynotes.ui.screens.NoteLayoutType
import sk.kubisdev.mynotes.ui.screens.NoteSortType

data class SettingsData(
    val theme: AppTheme = AppTheme.SYSTEM,
    val colorScheme: AppColorScheme = AppColorScheme.OCEAN_BLUE,
    val biometricEnabled: Boolean = false,
    val passwordEnabled: Boolean = false,
    val noteSwipeEnabled: Boolean = true,
    val swipeLeftAction: SwipeAction = SwipeAction.DELETE, // 🆕 NEW
    val swipeRightAction: SwipeAction = SwipeAction.ARCHIVE, // 🆕 NEW
    val language: AppLanguage = AppLanguage.SYSTEM,
    val noteLayoutType: NoteLayoutType = NoteLayoutType.LIST,
    val noteSortType: NoteSortType = NoteSortType.DATE_NEWEST,
    val hasCompletedOnboarding: Boolean = false,
    val gradientHeadersEnabled: Boolean = true
)

enum class AppTheme(val displayName: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System Default")
}

enum class AppColorScheme(
    val displayName: String,
    val primaryColor: Long,
    val emoji: String
) {
    // Blues
    SKY_BLUE("Sky Blue", 0xFF64B5F6, "☁️"),
    OCEAN_BLUE("Ocean Blue", 0xFF1976D2, "🌊"),
    NAVY("Navy", 0xFF0D47A1, "⚓"),

    // Greens
    MINT("Mint", 0xFF81C784, "🍃"),
    FOREST("Forest", 0xFF388E3C, "🌲"),
    OLIVE("Olive", 0xFF33691E, "🫒"),

    // Oranges
    PEACH("Peach", 0xFFFFAB91, "🍑"),
    TANGERINE("Tangerine", 0xFFFF6F00, "🍊"),
    RUST("Rust", 0xFFD84315, "🍂"),

    // Reds & Pinks
    ROSE("Rose", 0xFFF48FB1, "🌹"),
    CHERRY("Cherry", 0xFFE53935, "🍒"),
    // The previous hex (0xFF880E4F) was very high-saturation magenta - its hue and
    // saturation sit in true "hot pink" territory, not muted wine-red. Any dark-mode
    // brightening of a color that saturated (whether by blending toward white or by
    // raising HSV value) inevitably lands on vivid pink, since there's no brownish/
    // muted character in the base color to begin with. This hex is a standard "Wine"
    // tone: same reddish hue family, much lower saturation, so it reads as wine both
    // raw (header/FAB) and brightened (body text/accents in dark mode).
    WINE("Wine", 0xFF722F37, "🍷"),

    // Purples
    LAVENDER("Lavender", 0xFFB39DDB, "💜"),
    VIOLET("Violet", 0xFF7B1FA2, "🟣"),
    PLUM("Plum", 0xFF4A148C, "🍇"),

    // Earth Tones
    SAND("Sand", 0xFFBCAAA4, "🏖️"),
    TERRACOTTA("Terracotta", 0xFF8D6E63, "🏺"),
    COFFEE("Coffee", 0xFF4E342E, "☕"),

    // Grays
    SILVER("Silver", 0xFFB0BEC5, "🪙"),
    STORM("Storm", 0xFF607D8B, "⛈️"),
    CHARCOAL("Charcoal", 0xFF37474F, "🌑");

}
// 🆕 NEW: Swipe Action enum
enum class SwipeAction(
    val displayName: String,
    val icon: String,
    val description: String
) {
    DELETE("Delete", "🗑️", "Move note to trash"),
    ARCHIVE("Archive", "📦", "Archive note for later"),
    NONE("None", "🚫", "No action")
}
