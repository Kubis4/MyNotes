package sk.kubdev.mynotes.backup

data class BackupSettings(
    val frequency: BackupFrequency,
    val hour: Int,
    val minute: Int,
    val dayOfWeek: Int,
    val dayOfMonth: Int
)
