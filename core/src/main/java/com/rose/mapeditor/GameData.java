package com.rose.mapeditor;

import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.data.Stl;
import com.rose.mapeditor.model.Chr;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.model.Zmo;
import com.rose.mapeditor.model.Zsc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Central registry for Rose game data files. */
public final class GameData {

    private static GameData instance;

    private Path gameRoot = Paths.get(".");
    public final Map<String, Stb> stbs = new HashMap<>();
    public final Map<String, Stl> stls = new HashMap<>();
    public final Map<String, Zsc> zscs = new HashMap<>();
    private final Map<String, Zms> zmsCache = new ConcurrentHashMap<>();
    private final Map<String, Zmo> zmoCache = new ConcurrentHashMap<>();
    private Chr listNpcChr;

    public Chr getOrLoadListNpcChr() throws IOException {
        if (listNpcChr == null) {
            listNpcChr = new Chr(resolve("3DDATA/NPC/LIST_NPC.CHR").toString());
        }
        return listNpcChr;
    }

    public Zms getOrLoadZms(String relativePath) throws IOException {
        String key = relativePath.replace('\\', '/');
        Zms cached = zmsCache.get(key);
        if (cached != null) {
            return cached;
        }
        Zms zms = new Zms(resolve(key).toString());
        zmsCache.put(key, zms);
        return zms;
    }

    public Zmo getOrLoadZmo(String relativePath) throws IOException {
        String key = relativePath.replace('\\', '/').trim();
        if (key.isEmpty()) {
            throw new IOException("Empty ZMO path");
        }
        Zmo cached = zmoCache.get(key);
        if (cached != null) {
            return cached;
        }
        Zmo zmo = new Zmo(resolve(key).toString(), false, true);
        zmoCache.put(key, zmo);
        return zmo;
    }

    public void clearMapCaches() {
        zmsCache.clear();
        zmoCache.clear();
        zscs.remove("Decoration");
        zscs.remove("Construction");
    }

    /** Clears all cached tables and assets after switching the client root folder. */
    public void resetForNewGameRoot() {
        stbs.clear();
        stls.clear();
        zscs.clear();
        zmsCache.clear();
        zmoCache.clear();
        listNpcChr = null;
    }

    public static GameData get() {
        if (instance == null) {
            instance = new GameData();
        }
        return instance;
    }

    public void setGameRoot(String path) {
        gameRoot = Paths.get(path);
    }

    public Path getGameRoot() {
        return gameRoot;
    }

    public Path resolve(String relativePath) {
        return gameRoot.resolve(relativePath.replace('\\', File.separatorChar));
    }

    public void loadCoreTables() throws IOException {
        stbs.clear();
        stls.clear();

        addStb("LIST_ZONE", "3DDATA/STB/LIST_ZONE.STB");
        addStb("LIST_SKY", "3DDATA/STB/LIST_SKY.STB");
        addStb("LIST_NPC", "3DDATA/STB/LIST_NPC.STB");
        addStb("LIST_MORPH_OBJECT", "3DDATA/STB/LIST_MORPH_OBJECT.STB");
        addStb("ZONETYPEINFO", "3Ddata/TERRAIN/TILES/ZONETYPEINFO.STB");

        addStl("LIST_ZONE_S", "3DDATA/STB/LIST_ZONE_S.STL");
        addStl("LIST_NPC_S", "3DDATA/STB/LIST_NPC_S.STL");
    }

    /** Loads ZSC files shared across all maps. */
    public void loadCoreZscs() throws IOException {
        if (zscs.containsKey("PART_NPC")) {
            return;
        }
        addZsc("PART_NPC", "3DDATA/NPC/PART_NPC.ZSC");
        addZsc("EVENT_OBJECT", "3DDATA/SPECIAL/EVENT_OBJECT.ZSC");
        addZsc("LIST_DECO_SPECIAL", "3DDATA/SPECIAL/LIST_DECO_SPECIAL.ZSC");
    }

    /** Loads per-map Decoration and Construction ZSC files from LIST_ZONE. */
    public void loadMapZscs(int mapId) throws IOException {
        if (stbs.isEmpty()) {
            loadCoreTables();
        }
        Stb listZone = stbs.get("LIST_ZONE");
        zscs.remove("Decoration");
        zscs.remove("Construction");
        addZsc("Decoration", listZone.cell(mapId, 12));
        addZsc("Construction", listZone.cell(mapId, 13));
    }

    public void prepareMapAssets(int mapId) throws IOException {
        loadCoreZscs();
        loadMapZscs(mapId);
    }

    private void addStb(String key, String relativePath) throws IOException {
        stbs.put(key, new Stb(resolve(relativePath).toString()));
    }

    private void addStl(String key, String relativePath) throws IOException {
        stls.put(key, new Stl(resolve(relativePath).toString()));
    }

    private void addZsc(String key, String relativePath) throws IOException {
        String trimmed = relativePath.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        zscs.put(key, new Zsc(resolve(trimmed).toString()));
    }
}
