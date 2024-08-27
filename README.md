#### Fork of `linkie-core` that's contains a few updates and changes.

**Changes**
- Export multiple mappings in a single tiny file
- Fix `SRGParser` (old MCP mappings finally works)

Note: 1.20.2+ doesn't have SRG mappings, in NeoForge mojmap is available at runtime.\
Not sure what Forge has though

**TODO**
- Check if Forge still has SRG mappings or did they also switch to mojmap at runtime?
- A way to have field descriptors for old MCP mappings
---
# linkie-core
The absolute core of linkie.

### Mappings Targets
- Legacy Yarn (1.3-1.13.2)
- MCP (1.8+)
- Mojang (1.14.4+)
- Yarn (1.14+)
- Yarrn (Infdev 20100618)
- Plasma (Beta 1.7.3)
- Feather (1.3-1.13.2)

### Platform Targets
- JVM
- Native?