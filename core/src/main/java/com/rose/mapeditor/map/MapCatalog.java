package com.rose.mapeditor.map;

import com.rose.mapeditor.GameData;
import com.rose.mapeditor.data.Stb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Reads playable maps from LIST_ZONE.STB */
public final class MapCatalog {

    public static final class Entry {
        public int id;
        public String name;
        public String zonePath;
    }

    private MapCatalog() {
    }

    public static List<Entry> listValidMaps(GameData data) throws IOException {
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        var listZoneS = data.stls.get("LIST_ZONE_S");
        List<Entry> entries = new ArrayList<>();

        for (int id = 1; id < listZone.cells.size(); id++) {
            if (!isValidMap(listZone, id)) {
                continue;
            }
            Entry entry = new Entry();
            entry.id = id;
            String stringId = listZone.cell(id, 27);
            entry.name = listZoneS != null ? listZoneS.search(stringId) : stringId;
            if (entry.name == null || entry.name.isBlank()) {
                entry.name = listZone.cell(id, 1);
            }
            if (entry.name == null || entry.name.isBlank()) {
                entry.name = "Map " + id;
            }
            entry.zonePath = listZone.cell(id, 2);
            entries.add(entry);
        }
        return entries;
    }

    /** Maps that have a zone path registered in LIST_ZONE (valid or incomplete). */
    public static List<Entry> listDeletableMaps(GameData data) throws IOException {
        if (data.stbs.isEmpty()) {
            data.loadCoreTables();
        }

        Stb listZone = data.stbs.get("LIST_ZONE");
        var listZoneS = data.stls.get("LIST_ZONE_S");
        List<Entry> entries = new ArrayList<>();

        for (int id = 1; id < listZone.cells.size(); id++) {
            String zonePath = listZone.cell(id, 2);
            if (zonePath == null || zonePath.trim().isEmpty()) {
                continue;
            }
            Entry entry = new Entry();
            entry.id = id;
            String stringId = listZone.cell(id, 27);
            entry.name = listZoneS != null ? listZoneS.search(stringId) : stringId;
            if (entry.name == null || entry.name.isBlank()) {
                entry.name = listZone.cell(id, 1);
            }
            if (entry.name == null || entry.name.isBlank()) {
                entry.name = "Map " + id;
            }
            entry.zonePath = zonePath.trim();
            entries.add(entry);
        }
        return entries;
    }

    static boolean isValidMap(Stb listZone, int id) {
        if (id < 1 || id >= listZone.cells.size()) {
            return false;
        }
        return hasText(listZone, id, 2)
            && hasText(listZone, id, 3)
            && hasText(listZone, id, 4)
            && hasText(listZone, id, 8)
            && hasText(listZone, id, 10)
            && hasText(listZone, id, 11)
            && hasText(listZone, id, 12)
            && hasText(listZone, id, 13);
    }

    private static boolean hasText(Stb listZone, int row, int column) {
        String value = listZone.cell(row, column);
        return value != null && !value.trim().isEmpty();
    }
}
