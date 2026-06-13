package com.rose.mapeditor.debug;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;
import com.rose.mapeditor.map.Ifo;
import com.rose.mapeditor.model.Zsc;
import com.rose.mapeditor.render.DdsLoadUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** One-off diagnostic: compare construction object #16 assets across two game roots. */
public final class CompareConstructionObject {

    private static final String IFO_PATH = "3DDATA/Maps/Junon/JPT01/34_32.IFO";
    private static final int TARGET_OBJECT_ID = 16;

    public static void main(String[] args) throws IOException {
        String gameRoot = args.length > 0 ? args[0] : "D:/workspace/irose/game2";
        String game2Root = args.length > 1 ? args[1] : "D:/workspace/irose/game";

        System.out.println("=== IFO construction entries with objectId " + TARGET_OBJECT_ID + " ===");
        dumpIfoEntries(Paths.get(gameRoot, IFO_PATH));

        System.out.println();
        System.out.println("=== game2: " + gameRoot + " ===");
        dumpAssets(gameRoot);

        System.out.println();
        System.out.println("=== game: " + game2Root + " ===");
        dumpAssets(game2Root);
    }

    private static void dumpIfoEntries(Path ifoPath) throws IOException {
        Ifo ifo = new Ifo(ifoPath.toString());
        int count = 0;
        for (int i = 0; i < ifo.construction.size(); i++) {
            Ifo.BaseEntry entry = ifo.construction.get(i);
            if (entry.objectId != TARGET_OBJECT_ID) {
                continue;
            }
            count++;
            System.out.printf("  entry[%d] pos=(%.2f, %.2f, %.2f) mapCell=(%.1f, %.1f) desc=%s%n",
                i, entry.position.x, entry.position.y, entry.position.z,
                entry.mapPosition.x, entry.mapPosition.y,
                entry.description != null ? entry.description : "");
        }
        System.out.println("  total matching entries: " + count);
    }

    private static void dumpAssets(String gameRoot) throws IOException {
        GameData data = GameData.get();
        data.setGameRoot(gameRoot);
        data.loadCoreTables();

        Stb listZone = data.stbs.get("LIST_ZONE");
        int mapId = findMapId(listZone, "JPT01");
        System.out.println("JPT01 mapId: " + mapId);

        String decoZsc = listZone.cell(mapId, 12).trim();
        String constructionZsc = listZone.cell(mapId, 13).trim();
        System.out.println("Decoration ZSC: " + decoZsc);
        System.out.println("Construction ZSC: " + constructionZsc);

        data.loadMapZscs(mapId);
        Zsc construction = data.zscs.get("Construction");
        if (construction == null) {
            System.out.println("Construction ZSC failed to load.");
            return;
        }

        System.out.println("Construction ZSC objects: " + construction.objects.size());
        if (TARGET_OBJECT_ID < 0 || TARGET_OBJECT_ID >= construction.objects.size()) {
            System.out.println("Object ID " + TARGET_OBJECT_ID + " out of range.");
            return;
        }

        Zsc.SceneObject obj = construction.objects.get(TARGET_OBJECT_ID);
        System.out.println("Object #" + TARGET_OBJECT_ID + " parts: " + obj.models.size());
        for (int p = 0; p < obj.models.size(); p++) {
            Zsc.PartModel part = obj.models.get(p);
            String mesh = part.modelId >= 0 && part.modelId < construction.models.size()
                ? construction.models.get(part.modelId) : "?";
            String texture = "?";
            if (part.textureId >= 0 && part.textureId < construction.textures.size()) {
                texture = construction.textures.get(part.textureId).path;
            }
            String motion = part.motion != null ? part.motion.trim() : "";
            System.out.printf("  part[%d] mesh=%s exists=%s%n", p, mesh, fileExists(data, mesh));
            System.out.printf("           texture=%s exists=%s format=%s load=%s%n", texture, fileExists(data, texture),
                ddsSummary(data, texture), ddsLoadTest(data, texture));
            if (!motion.isEmpty()) {
                System.out.printf("           motion=%s exists=%s%n", motion, fileExists(data, motion));
            }
        }

        Path litPath = Paths.get(gameRoot, "3DDATA/Maps/Junon/JPT01/34_32/LIGHTMAP/BUILDINGLIGHTMAPDATA.LIT");
        if (Files.isRegularFile(litPath)) {
            com.rose.mapeditor.map.Lit lit = new com.rose.mapeditor.map.Lit(litPath.toString());
            dumpConstructionLightmap(data, lit, ifoConstructionIndex(gameRoot), obj.models.size());
        }
    }

    private static int ifoConstructionIndex(String gameRoot) throws IOException {
        Ifo ifo = new Ifo(Paths.get(gameRoot, IFO_PATH).toString());
        for (int i = 0; i < ifo.construction.size(); i++) {
            if (ifo.construction.get(i).objectId == TARGET_OBJECT_ID) {
                return i;
            }
        }
        return -1;
    }

    private static void dumpConstructionLightmap(GameData data, com.rose.mapeditor.map.Lit lit,
                                                   int entryIndex, int partCount) {
        System.out.println("Construction entry index in IFO: " + entryIndex);
        try {
            int litObjectIdx = lit.searchObject(entryIndex + 1);
            if (litObjectIdx < 0) {
                System.out.println("  No LIT object entry for construction index " + entryIndex);
                return;
            }
            com.rose.mapeditor.map.Lit.LitObject litObject = lit.objects.get(litObjectIdx);
            for (int partIndex = 0; partIndex < partCount; partIndex++) {
                int litPartIdx = lit.searchPart(litObjectIdx, partIndex);
                if (litPartIdx < 0) {
                    System.out.printf("  part[%d] lightmap: none%n", partIndex);
                    continue;
                }
                String dds = lit.folder + "/" + litObject.parts.get(litPartIdx).ddsName;
                System.out.printf("  part[%d] lightmap=%s exists=%s format=%s load=%s%n", partIndex, dds,
                    fileExists(data, dds), ddsSummary(data, dds), ddsLoadTest(data, dds));
            }
        } catch (Exception e) {
            System.out.println("  LIT read error: " + e.getMessage());
        }
    }

    private static String ddsLoadTest(GameData data, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || "?".equals(relativePath)) {
            return "n/a";
        }
        try {
            Path path = relativePath.contains(":") || relativePath.startsWith("/")
                ? Path.of(relativePath.trim()) : data.resolve(relativePath.trim());
            DdsLoadUtil.BgraImage image = DdsLoadUtil.readBgra8888(path);
            return "OK " + image.width + "x" + image.height;
        } catch (Exception e) {
            return "FAILED: " + e.getMessage();
        }
    }

    private static String ddsSummary(GameData data, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || "?".equals(relativePath)) {
            return "n/a";
        }
        try {
            byte[] file = Files.readAllBytes(data.resolve(relativePath.trim()));
            if (file.length < 128 || file[0] != 'D') {
                return "not-dds";
            }
            int h = java.nio.ByteBuffer.wrap(file, 12, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
            int w = java.nio.ByteBuffer.wrap(file, 16, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
            int flags = java.nio.ByteBuffer.wrap(file, 80, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
            String fourCc = new String(file, 84, 4, java.nio.charset.StandardCharsets.US_ASCII).trim();
            if (fourCc.isEmpty()) {
                fourCc = (flags == 0x41 ? "BGRA8888" : flags == 0x40 ? "RGB8888-uncompressed" : "uncompressed");
            }
            return w + "x" + h + " " + fourCc + " (" + file.length + " bytes)";
        } catch (Exception e) {
            return "error:" + e.getMessage();
        }
    }

    private static String fileExists(GameData data, String relativePath) {
        if (relativePath == null || relativePath.isBlank() || "?".equals(relativePath)) {
            return "n/a";
        }
        Path path = data.resolve(relativePath.trim());
        return Files.isRegularFile(path) ? "YES" : "MISSING (" + path + ")";
    }

    private static int findMapId(Stb listZone, String mapName) throws IOException {
        for (int i = 0; i < listZone.cells.size(); i++) {
            String zonePath = listZone.cell(i, 2).trim();
            if (zonePath.toUpperCase().contains(mapName.toUpperCase())) {
                return i;
            }
        }
        throw new IOException("Map not found in LIST_ZONE: " + mapName);
    }
}
