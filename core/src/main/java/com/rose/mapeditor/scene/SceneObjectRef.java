package com.rose.mapeditor.scene;

import com.rose.mapeditor.map.Ifo;

/** Links a rendered mesh or marker back to its IFO source entry. */
public final class SceneObjectRef {

    public MapObjectKind kind;
    public String ifoPath;
    public String ifoFileName;
    /** IFO block label, e.g. Decoration, NPC, Sound. */
    public String blockName;
    public int entryIndex = -1;
    public Ifo.BaseEntry entry;
    /** ZSC table name when the object comes from a ZSC (Decoration, Construction, etc.). */
    public String zscName;
    /** Resolved LIST_NPC character id for NPCs and monsters. */
    public int chrId = -1;

    public static SceneObjectRef forEntry(Ifo ifo, MapObjectKind kind, String blockName, int entryIndex,
                                          Ifo.BaseEntry entry, String zscName) {
        SceneObjectRef ref = new SceneObjectRef();
        ref.kind = kind;
        ref.ifoPath = ifo != null ? ifo.filePath : null;
        ref.ifoFileName = ifo != null ? ifo.fileName : null;
        ref.blockName = blockName;
        ref.entryIndex = entryIndex;
        ref.entry = entry;
        ref.zscName = zscName;
        return ref;
    }
}
