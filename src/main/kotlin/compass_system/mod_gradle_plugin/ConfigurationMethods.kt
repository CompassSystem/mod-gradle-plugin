package compass_system.mod_gradle_plugin

import com.modrinth.minotaur.Minotaur
import com.modrinth.minotaur.ModrinthExtension
import compass_system.mod_gradle_plugin.misc.JsonNormalizerReader
import compass_system.mod_gradle_plugin.task.BuildModTask
import compass_system.mod_gradle_plugin.task.ReleaseModTask
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.configuration.FabricApiExtension
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources

object ConfigurationMethods {
    private val javaVersion = JavaVersion.VERSION_17

    fun configureFabric(project: Project, buildTask: BuildModTask, releaseTask: ReleaseModTask) {
        val projectData = ProjectData(project)

        configureGenericProject(project, projectData)

        if (projectData.producesReleaseArtifact) {
            configureReleaseProject(project, projectData)
        }

        configureFabricProject(project, projectData)
    }

    private fun configureGenericProject(project: Project, projectData: ProjectData) {
        project.plugins.apply("java-library")

        project.version = projectData.modVersion
        project.extensions.getByName<BasePluginExtension>("base").archivesName.set(project.property("archives_base_name") as String)

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

    private fun configureReleaseProject(project: Project, projectData: ProjectData) {
        project.plugins.apply("com.modrinth.minotaur")

        project.tasks.apply {
            val baseTask = findByName("remapJar") ?: getByName("jar")

            create("minJar", Jar::class.java) {
                inputs.files(baseTask.outputs.files)

                duplicatesStrategy = DuplicatesStrategy.FAIL

                inputs.files.forEach {
                    if (it.extension == "jar") {
                        this.from(project.zipTree(it)) {
                            exclude("**/MANIFEST.MF")
                        }
                    }
                }

                filesMatching(listOf("**/*.json", "**/*.mcmeta")) {
                    filter(JsonNormalizerReader::class.java)
                }

                dependsOn(baseTask)
            }

            named("build").get().dependsOn("minJar")
        }

        project.extensions.getByName<ModrinthExtension>("modrinth").apply {
            debugMode.set(System.getProperty("MOD_UPLOAD_DEBUG", "false") == "true")
            autoAddDependsOn.set(false)
            detectLoaders.set(false)
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
