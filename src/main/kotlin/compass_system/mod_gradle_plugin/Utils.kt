package compass_system.mod_gradle_plugin

import org.codehaus.groovy.runtime.ProcessGroovyMethods
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.InclusiveRepositoryContentDescriptor
import java.net.URI

object Utils {
    fun titleCase(input: String): String {
        return input.substring(0, 1).uppercase() + input.substring(1)
    }

    fun getGitCommit(): String {
        return ProcessGroovyMethods.getText(ProcessGroovyMethods.execute("git rev-parse HEAD"))
    }

    fun RepositoryHandler.exclusiveRepo(mavenName: String, mavenUrl: String, groups: InclusiveRepositoryContentDescriptor.() -> Unit) {
        exclusiveContent {
            forRepository {
                maven {
                    name = mavenName
                    url = URI.create(mavenUrl)
                }
            }

            filter { groups() }
        }
    }
}
