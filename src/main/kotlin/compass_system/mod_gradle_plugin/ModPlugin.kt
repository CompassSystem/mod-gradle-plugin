package compass_system.mod_gradle_plugin

import compass_system.mod_gradle_plugin.misc.JsonNormalizerReader
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.configuration.FabricApiExtension
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources

class ModPlugin : Plugin<Project> {
    private val JAVA_VERSION = JavaVersion.VERSION_17

    override fun apply(project: Project) {
        val projectData = ProjectData(project)

        configureGenericProject(project, projectData)
        configureFabricProject(project, projectData)
    }

    private fun configureGenericProject(project: Project, projectData: ProjectData) {
        project.version = projectData.modVersion
        project.extensions.getByName<BasePluginExtension>("base").archivesName.set(project.property("archives_base_name") as String)

        project.tasks.apply {
            withType(JavaCompile::class.java).configureEach {
                options.encoding = "UTF-8"
                options.release = JAVA_VERSION.ordinal + 1
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

    private fun configureFabricProject(project: Project, projectData: ProjectData) {
        val loom = project.extensions.getByName<LoomGradleExtensionAPI>("loom")

        loom.apply {
            silentMojangMappingsLicense()

            splitEnvironmentSourceSets()
            mods.create(projectData.modId) {
                sourceSet("main")
                sourceSet("client")
            }

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
            add("mappings", loom.layered {
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
                if (projectData.producesReleaseArtifact) {
                    injectAccessWidener = true

                    archiveClassifier = "fat"
                }
            }

            if (projectData.producesReleaseArtifact) {
                create("minJar", Jar::class.java) {
                    inputs.files(getByName("remapJar").outputs.files)

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

                    dependsOn("remapJar")
                }

                named("build").get().dependsOn("minJar")
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
