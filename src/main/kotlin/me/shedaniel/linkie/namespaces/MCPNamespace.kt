package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.shedaniel.linkie.MappingsBuilder
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.MethodArg
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.parser.apply
import me.shedaniel.linkie.parser.srg
import me.shedaniel.linkie.parser.tsrg
import me.shedaniel.linkie.utils.*

object MCPNamespace : Namespace("mcp") {
    const val tmpMcpVersionsUrl = "https://gist.githubusercontent.com/shedaniel/afc2748c6d5dd827d4cde161a49687ec/raw/mcp_versions.json"
    private val mcpConfigSnapshots = mutableMapOf<Version, MutableList<String>>()
    private val newMcpVersions = mutableMapOf<Version, MCPVersion>()

    init {
        buildSupplier {
            cached()

            buildVersions {
                versionsSeq(::getAllBotVersions)
                uuid { "$it-${mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!}" }

                buildMappings(name = "MCP") {
                    val latestSnapshot = mcpConfigSnapshots[it.toVersion()]?.maxOrNull()!!
                    source(
                        if (it.toVersion() >= Version(1, 13)) {
                            loadTsrgFromURLZip(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip"))
                            MappingsSource.MCP_TSRG
                        } else {
                            loadSrgFromURLZip(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/$it/mcp-$it-srg.zip"))
                            MappingsSource.MCP_SRG
                        }
                    )
                    loadMCPFromURLZip(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_snapshot/$latestSnapshot-$it/mcp_snapshot-$latestSnapshot-$it.zip"))
                }
            }

            buildVersions {
                versions { newMcpVersions.keys.map(Version::toString) }
                uuid { newMcpVersions[it.toVersion()]!!.name }

                buildMappings(name = "MCP") {
                    val mcpVersion = newMcpVersions[it.toVersion()]!!
                    loadTsrgFromURLZip(URL(mcpVersion.mcp_config))
                    loadMCPFromURLZip(URL(mcpVersion.mcp))
                    source(MappingsSource.MCP_TSRG)
                }
            }
        }
    }

    override fun supportsFieldDescription(): Boolean = false
    override fun getDefaultLoadedVersions(): List<String> = listOf(defaultVersion!!)
    fun getAllBotVersions(): Sequence<String> = mcpConfigSnapshots.keys.asSequence().map { it.toString() }
    override fun getAllVersions(): Sequence<String> = getAllBotVersions() + newMcpVersions.keys.map(Version::toString)

    override fun supportsAT(): Boolean = true
    override fun supportsMixin(): Boolean = true
    override fun supportsSource(): Boolean = true
    override suspend fun reloadData() {
        mcpConfigSnapshots.clear()
        json.parseToJsonElement(URL("https://maven.minecraftforge.net/de/oceanlabs/mcp/versions.json").readText()).jsonObject.forEach { mcVersion, mcpVersionsObj ->
            val list = mcpConfigSnapshots.getOrPut(mcVersion.toVersion(), ::mutableListOf)
            mcpVersionsObj.jsonObject["snapshot"]?.jsonArray?.forEach {
                list.add(it.jsonPrimitive.content)
            }
        }
        mcpConfigSnapshots.filterValues { it.isEmpty() }.keys.toMutableList().forEach { mcpConfigSnapshots.remove(it) }
        newMcpVersions.clear()
        val tmpMcpVersionsJson = json.decodeFromString(MapSerializer(String.serializer(), MCPVersion.serializer()), URL(tmpMcpVersionsUrl).readText())
        tmpMcpVersionsJson.forEach { (mcVersion, mcpVersion) ->
            newMcpVersions[mcVersion.toVersion()] = mcpVersion
        }
    }

    suspend fun MappingsBuilder.loadTsrgFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.tsrg") {
                loadTsrgFromInputStream(entry.bytes.decodeToString())
            }
        }
    }

    suspend fun MappingsBuilder.loadSrgFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory && path.split("/").lastOrNull() == "joined.srg") {
                loadSrgFromInputStream(entry.bytes.decodeToString())
            }
        }
    }

    private fun MappingsBuilder.loadSrgFromInputStream(content: String) {
        apply(
            srg(content),
            obfMerged = "obf",
            intermediary = "srg",
        )
    }

    private fun MappingsBuilder.loadTsrgFromInputStream(content: String) {
        apply(
            tsrg(content),
            obfMerged = "obf",
            intermediary = "srg",
        )
    }

    suspend fun MappingsBuilder.loadMCPFromURLZip(url: URL) {
        url.toAsyncZip().forEachEntry { path, entry ->
            if (!entry.isDirectory) {
                when (path.split("/").lastOrNull()) {
                    "fields.csv" -> loadMCPFieldsCSVFromInputStream(entry.bytes.lines())
                    "methods.csv" -> loadMCPMethodsCSVFromInputStream(entry.bytes.lines())
                    "params.csv" -> loadMCPParamsCSVFromInputStream(entry.bytes.lines())
                }
            }
        }
    }

    private fun MappingsBuilder.loadMCPFieldsCSVFromInputStream(lines: Sequence<String>) {
        val map = mutableMapOf<String, String>()
        lines.filterNotBlank().forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        container.classes.forEach { (_, it) ->
            it.fields.forEach { field ->
                field.mappedName = map[field.intermediaryName]
            }
        }
    }

    private fun MappingsBuilder.loadMCPMethodsCSVFromInputStream(lines: Sequence<String>) {
        val map = mutableMapOf<String, String>()
        lines.filterNotBlank().forEach {
            val split = it.split(',')
            map[split[0]] = split[1]
        }
        container.classes.forEach { (_, it) ->
            it.methods.forEach { method ->
                method.mappedName = map[method.intermediaryName]
            }
        }
    }

    private fun MappingsBuilder.loadMCPParamsCSVFromInputStream(lines: Sequence<String>) {
        val map = mutableMapOf<String, MutableList<MethodArg>>()
        val paramRegex = """p_(\d+)_(\d+)_?""".toRegex()
        val funcRegex = """func_(\d+)_([^_]+)_?""".toRegex()
        lines.filterNotBlank().forEach {
            val split = it.split(',')
            val match = paramRegex.matchEntire(split[0]) ?: return@forEach
            map.getOrPut(match.groups[1]!!.value) { mutableListOf() }
                .add(MethodArg(match.groups[2]!!.value.toInt(), split[1]))
        }
        container.classes.forEach { (_, it) ->
            it.methods.forEach inner@{ method ->
                val match = funcRegex.matchEntire(method.intermediaryName) ?: return@inner
                map[match.groups[1]!!.value]?.apply {
                    if (method.args == null) method.args = mutableListOf()
                    method.args!!.addAll(this)
                }
            }
        }
    }

    @Serializable
    data class MCPVersion(
        val name: String,
        val mcp_config: String,
        val mcp: String,
    )
}
