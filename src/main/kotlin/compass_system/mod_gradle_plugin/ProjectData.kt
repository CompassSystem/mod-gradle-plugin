package compass_system.mod_gradle_plugin

import org.gradle.api.Project

class ProjectData(project: Project) {
    val modId = project.property("mod_id") as String
    val modVersion: String = project.property("mod_version") as String

    val usesDatagen = project.findProperty("template.usesDataGen") == "true"
    val producesReleaseArtifact = project.findProperty("template.producesReleaseArtifact") == "true"

    val minecraftVersion: String = project.property("minecraft_version") as String
    val parchmentVersion: String? = project.findProperty("parchment_version") as String?
}
