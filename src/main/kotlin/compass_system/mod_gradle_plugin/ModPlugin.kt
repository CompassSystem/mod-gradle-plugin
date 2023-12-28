package compass_system.mod_gradle_plugin

import compass_system.mod_gradle_plugin.extension.ModPluginExtensionApi
import compass_system.mod_gradle_plugin.extension.ModPluginExtensionImpl
import org.gradle.api.Plugin
import org.gradle.api.Project

class ModPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.extensions.add(ModPluginExtensionApi::class.java, "mod", ModPluginExtensionImpl(project))
    }
}
