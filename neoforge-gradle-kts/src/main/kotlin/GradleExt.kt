import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer

inline fun <reified T> ExtensionContainer.getByNameAndType(name: String): T {
  return getByName(name) as T
}

val Project.mainSourceSet: SourceSet
  get() = extensions
    .getByNameAndType<SourceSetContainer>("sourceSets")
    .getByName("main")

fun Project.propertyString(name: String): String {
  return findProperty(name) as? String
    ?: rootProject.findProperty(name) as? String
    ?: throw IllegalArgumentException("Property '$name' is not set.")
}

val Project.isBuildTask: Boolean
  get() = gradle.startParameter.taskNames.any { it.endsWith("build") }

val Project.mod_id get() = propertyString("mod_id")
val Project.mod_name get() = propertyString("mod_name")
val Project.mod_license get() = propertyString("mod_license")
val Project.mod_version get() = propertyString("mod_version")
val Project.mod_authors get() = propertyString("mod_authors")
val Project.mod_description get() = propertyString("mod_description")
val Project.mod_group_id get() = propertyString("mod_group_id")
val Project.minecraft_version get() = propertyString("minecraft_version")
val Project.minecraft_version_range get() = propertyString("minecraft_version_range")
val Project.neo_version get() = propertyString("neo_version")
val Project.neo_version_range get() = propertyString("neo_version_range")
val Project.loader_version_range get() = propertyString("loader_version_range")

// 提供便捷方法
val Project.libraries
  get() = configurations.getByName("libraries")