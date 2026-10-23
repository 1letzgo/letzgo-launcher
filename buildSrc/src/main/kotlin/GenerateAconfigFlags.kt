import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import java.util.Properties

/**
 * Ersetzt das AOSP-Tool `aconfig`: liest *.aconfig-Deklarationen und erzeugt pro Paket eine
 * `Flags`-Klasse mit statischen Gettern und `FLAG_*`-Konstanten.
 *
 * Werte (`<paket>.<flag_name>=true|false`): zuerst aus [overrides], dann aus [releaseValues]
 * (Werte des AOSP-Release-Configs, siehe tools/import_flag_values.py), sonst aus – wie bei aconfig.
 */
abstract class GenerateAconfigFlags : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val declarations: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val releaseValues: RegularFileProperty

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val overrides: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    private data class Flag(val name: String)

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()

        val release = Properties()
        releaseValues.get().asFile.reader().use(release::load)
        val props = Properties()
        overrides.orNull?.asFile?.takeIf { it.exists() }?.reader()?.use(props::load)

        val byPackage = sortedMapOf<String, MutableList<Flag>>()
        for (file in declarations.files.sortedBy { it.path }) {
            val text = file.readText().replace(Regex("(?m)^\\s*#.*$"), "")
            val pkg = Regex("""package:\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
                ?: error("Kein package in $file")
            val list = byPackage.getOrPut(pkg) { mutableListOf() }
            for (block in flagBlocks(text)) {
                val name = Regex("""name:\s*"([^"]+)"""").find(block)?.groupValues?.get(1) ?: continue
                list += Flag(name)
            }
        }

        val used = mutableSetOf<String>()
        for ((pkg, flags) in byPackage) {
            val dir = out.resolve(pkg.replace('.', '/')).apply { mkdirs() }
            val body = StringBuilder()
            body.append("// Generiert von GenerateAconfigFlags, nicht bearbeiten.\n")
            body.append("package $pkg;\n\n")
            body.append("public final class Flags {\n")
            body.append("    private Flags() {}\n\n")
            for (flag in flags.distinctBy { it.name }.sortedBy { it.name }) {
                val key = "$pkg.${flag.name}"
                val value = (props.getProperty(key) ?: release.getProperty(key))?.trim()?.toBoolean() ?: false
                if (props.containsKey(key)) used += key
                body.append("    public static final String FLAG_${flag.name.uppercase()} = \"$key\";\n")
                body.append("    public static boolean ${camel(flag.name)}() { return $value; }\n\n")
            }
            body.append("}\n")
            dir.resolve("Flags.java").writeText(body.toString())
        }

        val unknown = props.stringPropertyNames() - used
        if (unknown.isNotEmpty()) error("Unbekannte Flags in Overrides: ${unknown.sorted()}")
    }

    /** Liefert den Inhalt jedes `flag { ... }`-Blocks (Klammern können verschachtelt sein). */
    private fun flagBlocks(text: String): List<String> {
        val result = mutableListOf<String>()
        val start = Regex("""\bflag\s*\{""")
        var idx = 0
        while (true) {
            val m = start.find(text, idx) ?: break
            var depth = 1
            var i = m.range.last + 1
            while (i < text.length && depth > 0) {
                when (text[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            result += text.substring(m.range.last + 1, i - 1)
            idx = i
        }
        return result
    }

    private fun camel(name: String): String =
        name.split('_').filter { it.isNotEmpty() }.mapIndexed { i, part ->
            if (i == 0) part else part.replaceFirstChar(Char::uppercaseChar)
        }.joinToString("")
}
