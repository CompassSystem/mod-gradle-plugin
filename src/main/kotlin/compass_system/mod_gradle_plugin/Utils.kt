package compass_system.mod_gradle_plugin

import org.codehaus.groovy.runtime.ProcessGroovyMethods

object Utils {
    fun titleCase(input: String): String {
        return input.substring(0, 1).uppercase() + input.substring(1)
    }

    fun getGitCommit(): String {
        return ProcessGroovyMethods.getText(ProcessGroovyMethods.execute("git rev-parse HEAD"))
    }
}
