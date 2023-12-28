package compass_system.mod_gradle_plugin

import com.modrinth.minotaur.ModrinthExtension
import compass_system.mod_gradle_plugin.Utils.getGitCommit
import compass_system.mod_gradle_plugin.Utils.titleCase
import compass_system.mod_gradle_plugin.Utils.exclusiveRepo
import compass_system.mod_gradle_plugin.misc.JsonNormalizerReader
import compass_system.mod_gradle_plugin.task.AbstractJsonTask
import compass_system.mod_gradle_plugin.task.BuildModTask
import compass_system.mod_gradle_plugin.task.ReleaseModTask
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.configuration.FabricApiExtension
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import kotlin.io.path.readText

object ConfigurationMethods {
    private val javaVersion = JavaVersion.VERSION_17

    fun configureFabric(project: Project, buildTask: BuildModTask, releaseTask: ReleaseModTask) {
        val projectData = ProjectData(project)

        configureGenericProject(project, projectData)
        configureFabricProject(project, projectData)

        if (projectData.producesReleaseArtifact) {
            configureReleaseProject(project, projectData, buildTask, releaseTask)
        }
    }

    private fun configureGenericProject(project: Project, projectData: ProjectData) {
        project.plugins.apply("java-library")

        project.version = projectData.modVersion
        project.extensions.getByName<BasePluginExtension>("base").archivesName.set(project.property("archives_base_name") as String)

        project.repositories.apply {
            exclusiveRepo("Unnofficial Curseforge", "https://cursemaven.com") {
                includeGroup("curse.maven")
            }

            exclusiveRepo("Modrinth", "https://api.modrinth.com/maven") {
                includeGroup("maven.modrinth")
            }

            exclusiveRepo("ParchmentMC", "https://maven.parchmentmc.org") {
                includeGroup("org.parchmentmc")
                includeGroup("org.parchmentmc.data")
            }
        }

        project.tasks.apply {
            withType(JavaCompile::class.java).configureEach {
                options.encoding = "UTF-8"
                options.release.set(javaVersion.ordinal + 1)
            }

            named<Jar>("jar") {
                if (projectData.usesDatagen) {
                    exclude("**/datagen")
                }

                if (projectData.producesReleaseArtifact) {
                    archiveClassifier.set("dev")
                }
            }
        }
    }

    private fun configureReleaseProject(project: Project, projectData: ProjectData, buildTask: BuildModTask, releaseTask: ReleaseModTask) {
        project.plugins.apply("com.modrinth.minotaur")

        project.tasks.apply {
            val baseTask = (findByName("remapJar") ?: getByName("jar")) as AbstractArchiveTask

            val minJarTask = project.tasks.register("minJar", AbstractJsonTask::class.java, JsonNormalizerReader::class.java)

            minJarTask.configure {
                input.set(baseTask.outputs.files.singleFile)
                archiveClassifier.set(projectData.platform)

                dependsOn(baseTask)
            }

            getByName("assemble").dependsOn(minJarTask)
        }

        val modRelaseType = if (projectData.modVersion.contains("alpha")) "alpha" else if (projectData.modVersion.contains("beta")) "beta" else "release"

        val targetVersions = buildList {
            add(projectData.minecraftVersion)
            (project.findProperty("extra_game_versions") as String?)?.split(",")?.forEach {
                if (it.isNotBlank()) {
                    add(it)
                }
            }
        }

        val repoBaseUrl = project.property("repo_base_url") as String
        val modChangelog = buildString {
            append(project.rootDir.toPath().resolve("changelog.md").readText(Charsets.UTF_8).replace("\r\n", "\n"))
            append("\nCommit: $repoBaseUrl/commit/${getGitCommit()}")
        }

        project.extensions.getByName<ModrinthExtension>("modrinth").apply {
            debugMode.set(System.getProperty("MOD_UPLOAD_DEBUG", "false") == "true")
            autoAddDependsOn.set(false)
            detectLoaders.set(false)

            projectId.set(project.property("modrinth_project_id") as String?)
            versionType.set(modRelaseType)
            versionNumber.set(projectData.modVersion + "+" + project.name)
            versionName.set(titleCase(projectData.platform) + " " + projectData.modVersion)
            file.set((project.tasks.getByName("minJar") as AbstractArchiveTask).archiveFile)
            changelog.set(modChangelog)
            gameVersions.set(targetVersions)
            loaders.set(listOf(projectData.platform))
        }

        buildTask.dependsOn(project.tasks.getByName("build"))

        project.afterEvaluate {
            releaseTask.finalizedBy(tasks.getByName("modrinth"))
        }
    }

    private fun configureFabricProject(project: Project, projectData: ProjectData) {
        project.plugins.apply("dev.architectury.loom")

        val loom = project.extensions.getByName<LoomGradleExtensionAPI>("loom")

        loom.apply {
            silentMojangMappingsLicense()

            splitEnvironmentSourceSets()
            mods.create(projectData.modId) {
                sourceSet("main")
                sourceSet("client")
            }

            @Suppress("UnstableApiUsage")
            mixin.defaultRefmapName.set("${projectData.modId}.refmap.json")

            if (project.hasProperty("access_widener_path")) {
                accessWidenerPath.set(project.file(project.property("access_widener_path") as String))
            }
        }

        if (projectData.usesDatagen) {
            project.extensions.getByName<FabricApiExtension>("fabricApi").apply {
                configureDataGeneration {
                    modId.set(projectData.modId)
                    outputDirectory.set(project.file("src/generated/resources"))
                }
            }
        }

        project.dependencies {
            add("minecraft", "com.mojang:minecraft:${projectData.minecraftVersion}")
            add("mappings", @Suppress("UnstableApiUsage") loom.layered {
                officialMojangMappings()
                projectData.parchmentVersion?.let {
                    parchment("org.parchmentmc.data:parchment-${it}@zip")
                }
            })

            add("modImplementation", "net.fabricmc:fabric-loader:${project.property("fabric_loader_version")}")
            add("modImplementation", "net.fabricmc.fabric-api:fabric-api:${project.property("fabric_api_version")}")

            add("compileOnly", "org.jetbrains:annotations:24.1.0")
        }

        project.tasks.apply {
            named<ProcessResources>("processResources") {
                inputs.properties(mutableMapOf("version" to projectData.modVersion))

                filesMatching("fabric.mod.json") {
                    expand(inputs.properties)
                }
            }

            named<RemapJarTask>("remapJar") {
                if (loom.accessWidenerPath.isPresent) {
                    injectAccessWidener.set(true)
                }

                if (projectData.producesReleaseArtifact) {
                    archiveClassifier.set("fat")
                }
            }

            project.afterEvaluate {
                all {
                    if (name == "genSources" || name.startsWith("gen") && name.contains("Sources") && !name.contains("With")) {
                        setDependsOn(setOf(named("${name}WithVineflower")))
                    }
                }
            }
        }
    }
}
