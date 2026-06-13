package com.rose.mapeditor.map;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.model.Zms;
import com.rose.mapeditor.model.Zsc;
import com.rose.mapeditor.render.RenderOptions;
import com.rose.mapeditor.render.RoseTransform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bakes per-object lightmap atlases (.LIT + .DDS) for decorations and constructions. */
public final class ObjectLightmapBaker {

    private static final int OBJECTS_PER_WIDTH = 2;
    private static final float AMBIENT = 0.38f;
    private static final float DIFFUSE = 0.62f;
    private static final float BASE = 136f;

    private ObjectLightmapBaker() {
    }

    public static int bakeLoadedMap(GameData data, Path mapFolder, List<Ifo> ifoFiles, int mapId) throws IOException {
        if (ifoFiles == null || ifoFiles.isEmpty()) {
            return 0;
        }
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }
        String decorationPath = data.stbs.get("LIST_ZONE").cell(mapId, 12);
        String constructionPath = data.stbs.get("LIST_ZONE").cell(mapId, 13);
        return bakeSectors(data, mapFolder, ifoFiles, decorationPath, constructionPath);
    }

    public static int bakeSectors(GameData data, Path mapFolder, List<Ifo> ifoFiles,
                                  String decorationPath, String constructionPath) throws IOException {
        if (ifoFiles == null || ifoFiles.isEmpty()) {
            return 0;
        }
        data.loadCoreZscs();
        Zsc decorationZsc = loadZsc(data, decorationPath);
        Zsc constructionZsc = loadZsc(data, constructionPath);

        int count = 0;
        for (Ifo ifo : ifoFiles) {
            if (ifo == null || ifo.fileName == null || ifo.fileName.length() < 5) {
                continue;
            }
            String sectorName = ifo.fileName.substring(0, 5);
            Path sectorDataDir = mapFolder.resolve(sectorName);
            if (bakeSector(data, sectorDataDir, sectorName, ifo, decorationZsc, constructionZsc)) {
                count++;
            }
        }
        return count;
    }

    public static int bakeSectors(GameData data, Path mapFolder, Map<String, Ifo> ifosBySector,
                                  String decorationPath, String constructionPath) throws IOException {
        if (ifosBySector == null || ifosBySector.isEmpty()) {
            return 0;
        }
        data.loadCoreZscs();
        Zsc decorationZsc = loadZsc(data, decorationPath);
        Zsc constructionZsc = loadZsc(data, constructionPath);

        int count = 0;
        for (Map.Entry<String, Ifo> entry : ifosBySector.entrySet()) {
            Path sectorDataDir = mapFolder.resolve(entry.getKey());
            if (bakeSector(data, sectorDataDir, entry.getKey(), entry.getValue(),
                decorationZsc, constructionZsc)) {
                count++;
            }
        }
        return count;
    }

    /** Copies all files from a source sector LIGHTMAP folder (LIT + DDS atlases). */
    public static void copyLightmapFolder(Path sourceSectorDir, Path destSectorDir) throws IOException {
        Path sourceLm = sourceSectorDir.resolve("LIGHTMAP");
        if (!Files.isDirectory(sourceLm)) {
            return;
        }
        Path destLm = destSectorDir.resolve("LIGHTMAP");
        Files.createDirectories(destLm);
        try (var stream = Files.list(sourceLm)) {
            for (Path file : stream.toList()) {
                if (Files.isRegularFile(file)) {
                    Files.copy(file, destLm.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static boolean bakeSector(GameData data, Path sectorDataDir, String sectorName, Ifo ifo,
                                      Zsc decorationZsc, Zsc constructionZsc) throws IOException {
        boolean wroteDecoration = bakeCategory(data, sectorDataDir, ifo.decoration, decorationZsc,
            "OBJECTLIGHTMAPDATA.LIT", "Object");
        boolean wroteConstruction = bakeCategory(data, sectorDataDir, ifo.construction, constructionZsc,
            "BUILDINGLIGHTMAPDATA.LIT", "Building");
        return wroteDecoration || wroteConstruction;
    }

    private static boolean bakeCategory(GameData data, Path sectorDataDir, List<Ifo.BaseEntry> entries,
                                        Zsc zsc, String litFileName, String atlasPrefix) throws IOException {
        if (entries == null || entries.isEmpty() || zsc == null) {
            return false;
        }

        Lit lit = new Lit();
        List<Atlas> atlases = new ArrayList<>();

        for (int entryIndex = 0; entryIndex < entries.size(); entryIndex++) {
            Ifo.BaseEntry entry = entries.get(entryIndex);
            if (entry.objectId < 0 || entry.objectId >= zsc.objects.size()) {
                continue;
            }

            Zsc.SceneObject sceneObject = zsc.objects.get(entry.objectId);
            Lit.LitObject litObject = new Lit.LitObject();
            litObject.objectId = entryIndex + 1;

            Matrix4 objectWorld = RoseTransform.fromRose(entry.position, entry.rotation, entry.scale);

            for (int partIndex = 0; partIndex < sceneObject.models.size(); partIndex++) {
                Zsc.PartModel part = sceneObject.models.get(partIndex);
                if (part.modelId < 0 || part.modelId >= zsc.models.size()) {
                    continue;
                }

                Zms zms;
                try {
                    zms = data.getOrLoadZms(zsc.models.get(part.modelId));
                } catch (IOException e) {
                    continue;
                }
                if (!hasLightmapUv(zms)) {
                    continue;
                }

                int atlasSize = chooseAtlasSize(zms);
                Atlas atlas = findAtlas(atlases, atlasSize, atlasPrefix);
                int mapPosition = atlas.allocateSlot();

                Matrix4 partLocal = RoseTransform.fromRose(part.position, part.rotation, part.scale);
                Matrix4 world = new Matrix4();
                RoseTransform.combinePartObject(world, partLocal, objectWorld);

                rasterizePart(atlas, mapPosition, zms, world);

                Lit.Part litPart = new Lit.Part();
                litPart.tgaName = "";
                litPart.partId = partIndex;
                litPart.ddsName = atlas.fileName;
                litPart.lightmapId = 0;
                litPart.pixelsPerObject = atlasSize;
                litPart.objectsPerWidth = OBJECTS_PER_WIDTH;
                litPart.mapPosition = mapPosition;
                litObject.parts.add(litPart);
            }

            if (!litObject.parts.isEmpty()) {
                lit.objects.add(litObject);
            }
        }

        if (lit.objects.isEmpty()) {
            return false;
        }

        Path lightmapDir = sectorDataDir.resolve("LIGHTMAP");
        Files.createDirectories(lightmapDir);

        lit.ddsFiles.clear();
        for (Atlas atlas : atlases) {
            if (atlas.usedSlots == 0) {
                continue;
            }
            Lit.DdsEntry ddsEntry = new Lit.DdsEntry();
            ddsEntry.fileName = atlas.fileName;
            lit.ddsFiles.add(ddsEntry);
            DdsBgraWriter.write(lightmapDir.resolve(atlas.fileName), atlas.size, atlas.size, atlas.pixels);
        }

        for (Lit.LitObject litObject : lit.objects) {
            for (Lit.Part part : litObject.parts) {
                part.lightmapId = indexOfDdsFile(lit.ddsFiles, part.ddsName);
            }
        }

        lit.save(lightmapDir.resolve(litFileName).toString());
        return true;
    }

    private static boolean hasLightmapUv(Zms zms) {
        if (zms.vertices == null || zms.vertices.length == 0) {
            return false;
        }
        for (Zms.Vertex vertex : zms.vertices) {
            if (vertex.lightmapCoord.len2() > 1e-6f) {
                return true;
            }
        }
        return false;
    }

    private static int chooseAtlasSize(Zms zms) {
        float maxExtent = 0f;
        for (Zms.Vertex vertex : zms.vertices) {
            maxExtent = Math.max(maxExtent, Math.abs(vertex.position.x));
            maxExtent = Math.max(maxExtent, Math.abs(vertex.position.y));
            maxExtent = Math.max(maxExtent, Math.abs(vertex.position.z));
        }
        return maxExtent <= 8f && zms.vertexCount <= 128 ? 128 : 256;
    }

    private static Atlas findAtlas(List<Atlas> atlases, int size, String prefix) {
        for (Atlas atlas : atlases) {
            if (atlas.size == size && atlas.hasRoom()) {
                return atlas;
            }
        }
        int indexForSize = 0;
        for (Atlas atlas : atlases) {
            if (atlas.size == size) {
                indexForSize++;
            }
        }
        Atlas created = new Atlas(size, prefix, indexForSize);
        atlases.add(created);
        return created;
    }

    private static int indexOfDdsFile(List<Lit.DdsEntry> ddsFiles, String fileName) {
        for (int i = 0; i < ddsFiles.size(); i++) {
            if (fileName.equalsIgnoreCase(ddsFiles.get(i).fileName)) {
                return i;
            }
        }
        return 0;
    }

    private static void rasterizePart(Atlas atlas, int mapPosition, Zms zms, Matrix4 world) {
        int slotSize = atlas.size / OBJECTS_PER_WIDTH;
        int slotCol = mapPosition % OBJECTS_PER_WIDTH;
        int slotRow = mapPosition / OBJECTS_PER_WIDTH;
        int slotOriginX = slotCol * slotSize;
        int slotOriginY = slotRow * slotSize;

        Vector3 lightDir = lightDirection();

        for (int i = 0; i + 2 < zms.indices.length; i += 3) {
            Zms.Vertex v0 = zms.vertices[zms.indices[i] & 0xFFFF];
            Zms.Vertex v1 = zms.vertices[zms.indices[i + 1] & 0xFFFF];
            Zms.Vertex v2 = zms.vertices[zms.indices[i + 2] & 0xFFFF];

            float[] u = {
                v0.lightmapCoord.x, v1.lightmapCoord.x, v2.lightmapCoord.x
            };
            float[] v = {
                v0.lightmapCoord.y, v1.lightmapCoord.y, v2.lightmapCoord.y
            };

            Vector3 n0 = transformNormal(world, v0.normal);
            Vector3 n1 = transformNormal(world, v1.normal);
            Vector3 n2 = transformNormal(world, v2.normal);

            int minX = clampInt((int) Math.floor(Math.min(Math.min(u[0], u[1]), u[2]) * slotSize), 0, slotSize - 1);
            int maxX = clampInt((int) Math.ceil(Math.max(Math.max(u[0], u[1]), u[2]) * slotSize), 0, slotSize - 1);
            int minY = clampInt((int) Math.floor(Math.min(Math.min(v[0], v[1]), v[2]) * slotSize), 0, slotSize - 1);
            int maxY = clampInt((int) Math.ceil(Math.max(Math.max(v[0], v[1]), v[2]) * slotSize), 0, slotSize - 1);

            for (int py = minY; py <= maxY; py++) {
                for (int px = minX; px <= maxX; px++) {
                    float fu = (px + 0.5f) / slotSize;
                    float fv = (py + 0.5f) / slotSize;
                    float[] w = barycentric(fu, fv, u[0], v[0], u[1], v[1], u[2], v[2]);
                    if (w[0] < 0f || w[1] < 0f || w[2] < 0f) {
                        continue;
                    }

                    Vector3 normal = new Vector3(n0).scl(w[0]).add(
                        new Vector3(n1).scl(w[1])).add(new Vector3(n2).scl(w[2]));
                    normal.nor();

                    float ndotl = normal.dot(lightDir);
                    if (ndotl < 0f) {
                        ndotl = 0f;
                    }
                    float shade = AMBIENT + DIFFUSE * ndotl;
                    writePixel(atlas, slotOriginX + px, slotOriginY + py, shade);
                }
            }
        }
    }

    private static Vector3 transformNormal(Matrix4 world, Vector3 normal) {
        float nx = normal.x * world.val[Matrix4.M00] + normal.y * world.val[Matrix4.M01] + normal.z * world.val[Matrix4.M02];
        float ny = normal.x * world.val[Matrix4.M10] + normal.y * world.val[Matrix4.M11] + normal.z * world.val[Matrix4.M12];
        float nz = normal.x * world.val[Matrix4.M20] + normal.y * world.val[Matrix4.M21] + normal.z * world.val[Matrix4.M22];
        return new Vector3(nx, nz, ny).nor();
    }

    private static Vector3 lightDirection() {
        float x = RenderOptions.LIGHT_DIR_X;
        float y = RenderOptions.LIGHT_DIR_Y;
        float z = RenderOptions.LIGHT_DIR_Z;
        return new Vector3(x, y, z).nor();
    }

    private static void writePixel(Atlas atlas, int x, int y, float shade) {
        if (x < 0 || y < 0 || x >= atlas.size || y >= atlas.size) {
            return;
        }
        int value = Math.round(clamp(BASE * shade, 32f, 240f));
        int offset = (y * atlas.size + x) * 4;
        atlas.pixels[offset] = (byte) value;
        atlas.pixels[offset + 1] = (byte) value;
        atlas.pixels[offset + 2] = (byte) Math.min(255, value + 17);
        atlas.pixels[offset + 3] = (byte) 255;
    }

    private static float[] barycentric(float px, float py,
                                       float x0, float y0, float x1, float y1, float x2, float y2) {
        float denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(denom) < 1e-8f) {
            return new float[] { -1f, -1f, -1f };
        }
        float w0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / denom;
        float w1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / denom;
        float w2 = 1f - w0 - w1;
        return new float[] { w0, w1, w2 };
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Zsc loadZsc(GameData data, String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        Path path = data.resolve(relativePath.trim());
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return new Zsc(path.toString());
    }

    private static final class Atlas {
        final int size;
        final String fileName;
        final byte[] pixels;
        int usedSlots;

        Atlas(int size, String prefix, int index) {
            this.size = size;
            this.fileName = String.format(Locale.ROOT, "%s_%d_%d.dds", prefix, size, index);
            this.pixels = DdsBgraWriter.neutralPixels(size, size);
        }

        boolean hasRoom() {
            return usedSlots < OBJECTS_PER_WIDTH * OBJECTS_PER_WIDTH;
        }

        int allocateSlot() {
            return usedSlots++;
        }
    }
}
