package compass_system.mod_gradle_plugin.task

open class BuildModTask : AbstractRestrictedTask() {
    override fun doChecks() {
        checkComments("build")
    }
}
