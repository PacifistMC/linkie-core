package me.shedaniel.linkie

import me.shedaniel.linkie.utils.warn
import net.fabricmc.stitch.commands.tinyv2.TinyClass
import net.fabricmc.stitch.commands.tinyv2.TinyField
import net.fabricmc.stitch.commands.tinyv2.TinyFile
import net.fabricmc.stitch.commands.tinyv2.TinyHeader
import net.fabricmc.stitch.commands.tinyv2.TinyMethod
import net.fabricmc.stitch.commands.tinyv2.TinyV2Writer
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.*

object TinyExporter {
    @OptIn(ExperimentalStdlibApi::class)
    fun export(
        container: MappingsContainer,
        intermediary: String,
        named: String? = null,
        obfMerged: String? = null,
        obfClient: String? = null,
        obfServer: String? = null,
    ): InputStream {
        val namespaces = mutableListOf(intermediary, named, obfMerged, obfClient, obfServer).filterNotNull()
        val tinyFile = TinyFile(TinyHeader(namespaces, 2, 0, mapOf()), buildList {
            container.classes.forEach { (_, clazz) ->
                val tinyClass = TinyClass(buildList {
                    add(clazz.intermediaryName)
                    named?.also { add(clazz.mappedName ?: clazz.intermediaryName) }
                    obfMerged?.also { add(clazz.obfMergedName ?: clazz.intermediaryName) }
                    obfClient?.also { add(clazz.obfClientName ?: clazz.intermediaryName) }
                    obfServer?.also { add(clazz.obfServerName ?: clazz.intermediaryName) }
                })
                tinyClass.methods.addAll(buildList {
                    clazz.methods.forEach { method ->
                        if (method.intermediaryDesc.isBlank())
                            println(method)
                        val tinyMethod = TinyMethod(method.intermediaryDesc, buildList {
                            add(method.intermediaryName)
                            named?.also { add(method.optimumName) }
                            obfMerged?.also { add(method.obfMergedName ?: method.intermediaryName) }
                            obfClient?.also { add(method.obfClientName ?: method.intermediaryName) }
                            obfServer?.also { add(method.obfServerName ?: method.intermediaryName) }
                        }, emptyList(), emptyList(), emptyList())
                        add(tinyMethod)
                    }
                })
                tinyClass.fields.addAll(buildList {
                    clazz.fields.forEach { field ->
                        val tinyMethod = TinyField(field.intermediaryDesc, buildList {
                            add(field.intermediaryName)
                            named?.also { add(field.optimumName) }
                            obfMerged?.also { add(field.obfMergedName ?: field.intermediaryName) }
                            obfClient?.also { add(field.obfClientName ?: field.intermediaryName) }
                            obfServer?.also { add(field.obfServerName ?: field.intermediaryName) }
                        }, emptyList())
                        add(tinyMethod)
                    }
                })
                add(tinyClass)
            }
        })
        val tmpPath = File(Namespaces.cacheFolder.absolutePath).toPath().resolve(UUID.randomUUID().toString())
        TinyV2Writer.write(tinyFile, tmpPath)
        val bytes = Files.readAllBytes(tmpPath)
        Files.deleteIfExists(tmpPath)
        return bytes.inputStream()
    }

    fun mergedExport(
        containers: List<MappingsContainer>,
        ignoreMissing: Boolean = true,
        obfMerged: String = "vanilla",
        warnings: Boolean = true,
    ): InputStream {
        val namespaces = mutableListOf(obfMerged)
        containers.forEach { container ->
            val name = container.namespace.lowercase().replace(" ", "_")
            namespaces.addAll(listOf("intermediary_$name", name))
        }

        // Group classes by their obfuscated names across all containers
        val groupedClasses = containers.flatMap { it.classes.values }.groupBy { it.optimumObfName }

        val tinyFile = TinyFile(TinyHeader(namespaces, 2, 0, mapOf()), buildList {
            groupedClasses.forEach { (obfName, classList) ->
                val classes = classList.toMutableList()
                // Group methods and fields by their obfuscated signatures
                // Add a special token `%%%` to prevent problems such as same descriptors but different names
                val groupedMethods = classes.flatMap { it.methods }.groupBy { "${it.getObfDesc(containers)}%%%${it.optimumObfName}" }
                val groupedFields = classes.flatMap { it.fields }.groupBy { it.optimumObfName }

                if (classes.size < containers.size) {
                    if (ignoreMissing) {
                        if (warnings) warn("Skipping class `${classes.first().optimumName}` as it doesn't exist in all containers")
                        return@forEach
                    }
                    // Duplicate the class to prevent missing classes
                    repeat(containers.size - classes.size) { classes.add(classes.first()) }
                }

                val tinyClass = TinyClass(buildList {
                    add(obfName)
                    classes.forEach { clazz ->
                        add(clazz.intermediaryName)
                        add(clazz.optimumName)
                    }
                })
                tinyClass.methods.addAll(buildList {
                    groupedMethods.forEach { (signature, methodList) ->
                        val methods = methodList.toMutableList()
                        val (obfDesc, _) = signature.split("%%%")
                        if (methods.size < containers.size) {
                            val method = methods.last()
                            if (ignoreMissing) {
                                if (warnings) warn("Skipping a method with name `${method.optimumName}` and `${method.intermediaryDesc}` as it doesn't exist in all containers")
                                return@forEach
                            }
                            // Duplicate the method to prevent missing methods
                            repeat(containers.size - methods.size) { methods.add(method) }
                        }
                        val tinyMethod = TinyMethod(obfDesc, buildList {
                            add(methods.first().optimumObfName)
                            methods.forEach { method ->
                                add(method.intermediaryName)
                                add(method.optimumName)
                            }
                        }, emptyList(), emptyList(), emptyList())
                        add(tinyMethod)
                    }
                })
                tinyClass.fields.addAll(buildList {
                    groupedFields.forEach { (obfName, fieldList) ->
                        val fields = fieldList.toMutableList()
                        val obfDesc = fields.first { it.intermediaryDesc.isNotBlank() }.getObfDesc(containers)
                        if (fields.size < containers.size) {
                            val field = fields.last()
                            if (ignoreMissing) {
                                if (warnings) warn("Skipping a field with name `${field.optimumName}` and `${field.intermediaryDesc}` as it doesn't exist in all containers")
                                return@forEach
                            }
                            // Duplicate the field to prevent missing fields
                            repeat(containers.size - fields.size) { fields.add(field) }
                        }
                        val tinyField = TinyField(obfDesc, buildList {
                                add(obfName)
                                fields.forEach { field ->
                                    add(field.intermediaryName)
                                    add(field.optimumName)
                                }
                            }, emptyList())
                        add(tinyField)
                    }
                })
                add(tinyClass)
            }
        })
        val tmpPath = File(Namespaces.cacheFolder.absolutePath).toPath().resolve(UUID.randomUUID().toString())
        TinyV2Writer.write(tinyFile, tmpPath)
        val bytes = Files.readAllBytes(tmpPath)
        Files.deleteIfExists(tmpPath)
        return bytes.inputStream()
    }
}