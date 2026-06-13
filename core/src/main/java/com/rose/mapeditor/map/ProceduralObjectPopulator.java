package com.rose.mapeditor.map;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.rose.mapeditor.GameData;
import com.rose.mapeditor.model.Zsc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Places decorations and constructions on procedurally generated maps. */
public final class ProceduralObjectPopulator {

    private static final float SECTOR_SIZE = 160f;
    private static final float CELL = 2.5f;
    private static final float ROSE_Y_BASE = 10400f;

    public static final class ObjectPool {
        public final List<Ifo.BaseEntry> decorations = new ArrayList<>();
        public final List<Ifo.BaseEntry> constructions = new ArrayList<>();
    }

    private ProceduralObjectPopulator() {
    }

    /** Collects decoration/construction templates from source map sectors. */
    public static ObjectPool buildPool(GameData data, List<MapSectorCatalog.SectorRef> sectors,
                                       String decorationPath, String constructionPath) throws IOException {
        ObjectPool pool = new ObjectPool();
        if (sectors == null || sectors.isEmpty()) {
            return pool;
        }

        Zsc decorationZsc = loadZsc(data, decorationPath);
        Zsc constructionZsc = loadZsc(data, constructionPath);

        for (MapSectorCatalog.SectorRef sector : sectors) {
            Path ifoPath = sector.mapFolder.resolve(sector.sectorName + ".IFO");
            if (!Files.isRegularFile(ifoPath)) {
                continue;
            }
            Ifo ifo = new Ifo(ifoPath.toString());
            for (Ifo.BaseEntry entry : ifo.decoration) {
                if (isValid(entry, decorationZsc)) {
                    pool.decorations.add(cloneEntry(entry));
                }
            }
            for (Ifo.BaseEntry entry : ifo.construction) {
                if (isValid(entry, constructionZsc)) {
                    pool.constructions.add(cloneEntry(entry));
                }
            }
        }
        return pool;
    }

    /** Copies decorations and constructions from a source sector into the output sector IFO. */
    public static void copyFromSource(MapSectorCatalog.SectorRef source, int dstCellY, int dstCellX,
                                      Ifo output) throws IOException {
        if (source == null || output == null) {
            return;
        }
        Path ifoPath = source.mapFolder.resolve(source.sectorName + ".IFO");
        if (!Files.isRegularFile(ifoPath)) {
            return;
        }

        Ifo sourceIfo = new Ifo(ifoPath.toString());
        int dCellY = dstCellY - source.cellY;
        int dCellX = dstCellX - source.cellX;

        for (Ifo.BaseEntry entry : sourceIfo.decoration) {
            output.decoration.add(translateEntry(entry, dCellY, dCellX));
        }
        for (Ifo.BaseEntry entry : sourceIfo.construction) {
            output.construction.add(translateEntry(entry, dCellY, dCellX));
        }
    }

    /** Scatters random props from the template pool across one sector. */
    public static void scatter(Random random, Ifo output, Him him, int cellY, int cellX,
                               int objectsPerSector, ObjectPool pool, boolean includeConstructions) {
        if (output == null || pool == null || objectsPerSector <= 0) {
            return;
        }
        if (pool.decorations.isEmpty() && (!includeConstructions || pool.constructions.isEmpty())) {
            return;
        }

        for (int i = 0; i < objectsPerSector; i++) {
            boolean useConstruction = includeConstructions
                && !pool.constructions.isEmpty()
                && (pool.decorations.isEmpty() || random.nextFloat() < 0.15f);
            Ifo.BaseEntry template = useConstruction
                ? pool.constructions.get(random.nextInt(pool.constructions.size()))
                : pool.decorations.get(random.nextInt(pool.decorations.size()));

            Ifo.BaseEntry placed = cloneEntry(template);
            placeOnTerrain(random, placed, him, cellY, cellX);
            if (useConstruction) {
                output.construction.add(placed);
            } else {
                output.decoration.add(placed);
            }
        }
    }

    private static void placeOnTerrain(Random random, Ifo.BaseEntry entry, Him him, int cellY, int cellX) {
        int dx = random.nextInt(65);
        int dy = random.nextInt(65);

        entry.position.x = dy * CELL + cellY * SECTOR_SIZE;
        entry.position.y = ROSE_Y_BASE - (dx * CELL + cellX * SECTOR_SIZE);
        entry.position.z = sampleHeight(him, dx, dy);

        entry.mapPosition.set(0f, 0f);
        entry.warpId = 0;
        entry.eventId = 0;

        float yaw = random.nextFloat() * 360f;
        entry.rotation.setFromAxis(Vector3.Z, yaw);
    }

    private static float sampleHeight(Him him, int dx, int dy) {
        if (him == null || him.position == null) {
            return 0f;
        }
        dx = Math.max(0, Math.min(64, dx));
        dy = Math.max(0, Math.min(64, dy));
        return him.position[dx][dy];
    }

    private static Ifo.BaseEntry translateEntry(Ifo.BaseEntry source, int dCellY, int dCellX) {
        Ifo.BaseEntry copy = cloneEntry(source);
        copy.position.x += dCellY * SECTOR_SIZE;
        copy.position.y -= dCellX * SECTOR_SIZE;
        copy.mapPosition.x += dCellY * 16;
        copy.mapPosition.y += dCellX * 16;
        return copy;
    }

    private static Ifo.BaseEntry cloneEntry(Ifo.BaseEntry source) {
        Ifo.BaseEntry copy = new Ifo.BaseEntry();
        copy.description = source.description != null ? source.description : "";
        copy.warpId = source.warpId;
        copy.eventId = source.eventId;
        copy.objectType = source.objectType;
        copy.objectId = source.objectId;
        copy.mapPosition = new Vector2(source.mapPosition);
        copy.rotation = new Quaternion(source.rotation);
        copy.position = new Vector3(source.position);
        copy.scale = new Vector3(source.scale);
        return copy;
    }

    private static boolean isValid(Ifo.BaseEntry entry, Zsc zsc) {
        return zsc != null && entry != null
            && entry.objectId >= 0
            && entry.objectId < zsc.objects.size();
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
}
