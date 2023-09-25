import org.gradle.api.Project
import java.io.File
import java.util.Properties


class IncludedBuildPluginInfo(
    val classesDir: File,
    val resourcesDir: File,
)

class IncludedBuildCache(
    private val project: Project,
    private val includedBuildDir: File,
) {
    private val markerFileLookup = "**/META-INF/gradle-plugins/*.properties"

    private val markerFiles: Set<File> =
        project.fileTree(includedBuildDir) {
            it.include(markerFileLookup)
        }.files

    fun resolvePluginById(id:String): IncludedBuildPluginInfo? {
        markerFiles.first {
            it.name == "$id.properties"
        }?.let { markerFile ->
            loadPluginMarkerFile(markerFile)?.let { implementationClassName ->

                val classFile = project.fileTree(includedBuildDir) {
                    it.include("**/$implementationClassName.class")
                }

                val resourcesDir = markerFile.parentFile.parentFile.parentFile

                val classesDir = classFile.singleFile.absolutePath.substring(0,
                    classFile.singleFile.absolutePath.length -
                            (implementationClassName.length + ".class".length + File.separator.length)
                )

                return IncludedBuildPluginInfo(File(classesDir), resourcesDir)
            }
        }
        return null
    }

    private fun loadPluginMarkerFile(markerFile: File): String =
        Properties().also {
            it.load(markerFile.reader())
        }.getProperty("implementation-class")
}