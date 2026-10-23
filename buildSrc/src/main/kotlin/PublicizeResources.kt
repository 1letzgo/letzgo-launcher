import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Kopiert ein AOSP-Ressourcenverzeichnis und ersetzt dabei Verweise auf Framework-Ressourcen, die
 * eine normale App nicht nutzen kann:
 * - Zeilen mit privaten Ressourcen (`@androidprv:`) entfallen.
 * - Nicht-öffentliche `@android:color/system_*`-Farben werden auf öffentliche (API 34) abgebildet.
 *   aapt2 linkt sie zwar gegen android.jar, ihre IDs sind auf Geräten aber nicht stabil.
 */
abstract class PublicizeResources : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val from = inputDir.get().asFile
        val to = outputDir.get().asFile
        to.deleteRecursively()
        from.walkTopDown().filter { it.isFile }.forEach { file ->
            val target = to.resolve(file.relativeTo(from))
            target.parentFile.mkdirs()
            if (file.extension != "xml") {
                file.copyTo(target)
                return@forEach
            }
            val text =
                file.readLines()
                    .filterNot { "@androidprv:" in it }
                    .joinToString("\n", postfix = "\n")
            target.writeText(SYSTEM_COLOR.replace(text) { publicColor(it.groupValues[1]) })
        }
    }

    private fun publicColor(name: String): String {
        val mapped =
            FIXED.matchEntire(name)?.let { it.groupValues[1] }
                ?: DIM.matchEntire(name)?.let { "${it.groupValues[1]}_${it.groupValues[2]}" }
                ?: when (name) {
                    "inverse_on_surface_light" -> "on_surface_dark"
                    "inverse_on_surface_dark" -> "on_surface_light"
                    "inverse_surface_light" -> "surface_container_highest_dark"
                    "inverse_surface_dark" -> "surface_container_highest_light"
                    "inverse_primary_light" -> "primary_dark"
                    "inverse_primary_dark" -> "primary_light"
                    "surface_tint_light" -> "primary_light"
                    "surface_tint_dark" -> "primary_dark"
                    "palette_key_color_error_light" -> "error_light"
                    "palette_key_color_error_dark" -> "error_dark"
                    "scrim_light", "scrim_dark", "shadow_light", "shadow_dark" ->
                        return "@android:color/black"
                    else -> name
                }
        return "@android:color/system_$mapped"
    }

    private companion object {
        val SYSTEM_COLOR = Regex("""@android:color/system_([a-z0-9_]+)""")
        // system_*_fixed* gibt es öffentlich nur ohne _light/_dark
        val FIXED = Regex("""((?:on_)?(?:primary|secondary|tertiary)_fixed(?:_dim|_variant)?)_(?:light|dark)""")
        val DIM = Regex("""(primary|secondary|tertiary|error)_dim_(light|dark)""")
    }
}
