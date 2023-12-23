package compass_system.mod_gradle_plugin

class ModDependencies {
    private val dependencies = mutableMapOf<String, ModDependency>()
    private val enabledMods = mutableListOf<String>()

    fun add(modName: String, dependencyName: String = modName, configure: ModDependency.() -> Unit) {
        dependencies[modName] = ModDependency(modName, dependencyName).apply(configure)
    }

    fun enableMods(vararg modNames: String) {
        enabledMods.addAll(modNames)
    }

    fun iterateCompileDependencies(action: (String) -> Unit) {
        dependencies.map { (_, modDependency) ->
            val compileOnly = modDependency.dependencies["compileOnly"] ?: mutableListOf()
            val implementation = modDependency.dependencies["implementation"] ?: mutableListOf()

            compileOnly + implementation
        }.flatten().distinct().forEach(action)
    }

    fun iterateRuntimeDependencies(action: (String) -> Unit) {
        dependencies.filter { it.key in enabledMods }
            .map { (_, modDependency) ->
                val runtimeOnly = modDependency.dependencies["runtimeOnly"] ?: mutableListOf()
                val implementation = modDependency.dependencies["implementation"] ?: mutableListOf()

                runtimeOnly + implementation
            }.flatten().distinct().forEach(action)
    }
}

class ModDependency(val modName: String, val modrinthName: String) {
    val dependencies: MutableMap<String, MutableList<String>> = mutableMapOf()

    fun compileOnly(dependency: String) {
        dependencies.computeIfAbsent("compileOnly") { mutableListOf() }.add(dependency)
    }

    fun runtimeOnly(dependency: String) {
        dependencies.computeIfAbsent("runtimeOnly") { mutableListOf() }.add(dependency)
    }

    fun implementation(dependency: String) {
        dependencies.computeIfAbsent("implementation") { mutableListOf() }.add(dependency)
    }
}
