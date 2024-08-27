package me.shedaniel.linkie.parser

import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.utils.filterNotBlank
import kotlin.properties.Delegates

fun srg(content: String): SrgParser = SrgParser(content)

class SrgParser(content: String) : AbstractParser() {
    val groups = content.lineSequence().filterNotBlank().groupBy { it.split(' ')[0] }
    override val namespaces: MutableMap<String, Int> = mutableMapOf(
        "obf" to 0,
        "srg" to 1,
    )
    override val source: MappingsSource
        get() = MappingsSource.SRG

    val field by lazy(::SimpleEntryComplex)
    val method by lazy(::SimpleEntryComplex)

    inner class SimpleEntryComplex : MappingsEntryComplex {
        override val namespaces: Set<String>
            get() = this@SrgParser.namespaces.keys

        var obf by Delegates.notNull<String>()
        var srg by Delegates.notNull<String>()

        override fun get(namespace: String?): String? = when (namespace) {
            "obf" -> obf
            "srg" -> srg
            else -> null
        }
    }

    override fun parse(visitor: MappingsVisitor) = withVisitor(visitor) {
        visitor.visitStart(MappingsNamespaces.of(namespaces.keys))
        groups["CL:"]?.forEach { classLine ->
            val split = classLine.substring(4).split(" ")
            lastClass.split = split::get
            val classVisitor = visitor.visitClass(lastClass)!!
            groups["FD:"]?.forEach { fieldLine ->
                val fieldSplit = fieldLine.substring(4).split(" ")
                if (fieldSplit[0].substringBeforeLast('/') != split[0]) return@forEach

                field.obf = fieldSplit[0].substringAfterLast('/')
                field.srg = fieldSplit[1].substringAfterLast('/')
                classVisitor.visitField(field)
            }
            groups["MD:"]?.forEach { methodLine ->
                val methodSplit = methodLine.substring(4).split(" ")
                if (methodSplit[0].substringBeforeLast('/') != split[0]) return@forEach

                val obfDesc = methodSplit[1]
                method.obf = methodSplit[0].substringAfterLast('/')
                method.srg = methodSplit[2].substringAfterLast('/')
                classVisitor.visitMethod(method, obfDesc)
            }
        }
        visitor.visitEnd()
    }
}
