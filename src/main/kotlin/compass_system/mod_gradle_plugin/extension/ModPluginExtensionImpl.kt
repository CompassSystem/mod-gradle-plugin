package compass_system.mod_gradle_plugin.extension

import compass_system.mod_gradle_plugin.ConfigurationMethods
import compass_system.mod_gradle_plugin.task.AbstractRestrictedTask
import compass_system.mod_gradle_plugin.task.BuildModTask
import compass_system.mod_gradle_plugin.task.ReleaseModTask
import org.gradle.api.Project

class ModPluginExtensionImpl(private val project: Project) : ModPluginExtensionApi {
    override fun projects(vararg projects: String) {
        val buildTask = project.tasks.create("buildMod", BuildModTask::class.java)
        val releaseTask = project.tasks.create("releaseMod", ReleaseModTask::class.java, project.projectDir)

        project.gradle.taskGraph.whenReady {
            allTasks.forEach {
                (it as? AbstractRestrictedTask)?.doChecks()
            }
        }

        projects.forEach { projectPath ->
            val sub = project.project(projectPath)

            val platform = sub.findProperty("template.platform") ?: throw IllegalArgumentException("Project $projectPath does not have a template.platform property")

            (sub.properties as MutableMap<String, Any?>)["loom.platform"] = platform

            if (platform == "fabric") {
                ConfigurationMethods.configureFabric(sub, buildTask, releaseTask)
            } else {
                throw IllegalArgumentException("Project $projectPath has an invalid template.platform value: $platform")
            }
        }
    }
}
