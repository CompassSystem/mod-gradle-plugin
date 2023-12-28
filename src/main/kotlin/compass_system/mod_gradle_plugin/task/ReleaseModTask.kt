package compass_system.mod_gradle_plugin.task

import java.io.File
import java.nio.charset.StandardCharsets
import javax.inject.Inject

open class ReleaseModTask @Inject constructor(private val gitParentDirectory: File) : AbstractRestrictedTask() {
    override fun doChecks() {
        checkComments("release")

        if (System.getProperty("MOD_IGNORE_CHANGES", "false") != "false") {
            return
        }

        try {
            val process = ProcessBuilder()
                .directory(gitParentDirectory)
                .command("git", "status", "--porcelain")
                .start()

            process.waitFor()

            process.inputReader(StandardCharsets.UTF_8).use { reader ->
                if (reader.readLine() != null) {
                    throw IllegalStateException("Cannot release with uncommitted changes.")
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Error occurred whilst checking for uncommitted changes.", error)
        }

        try {
            val process = ProcessBuilder()
                .directory(gitParentDirectory)
                .command("git", "status", "-b", "--porcelain=2")
                .start()

            process.waitFor()

            process.inputReader(StandardCharsets.UTF_8).use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split(" ")
                    if (parts.size >= 2 && "branch.ab" == parts[1]) {
                        if (parts[2] != "+0" || parts[3] != "-0") {
                            throw IllegalStateException("Cannot release with un-pushed changes.")
                        }
                    }
                }
            }
        } catch (error: Exception) {
            throw IllegalStateException("Error occurred whilst checking for un-pushed changes.", error)
        }
    }
}
