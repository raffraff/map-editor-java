package com.rose.mapeditor.map;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Loads .IFO files from a Rose map folder. */
public final class IfoLoader {

    private IfoLoader() {
    }

    /**
     * Loads all {@code *.IFO} files in {@code mapFolder} (map root first, then one level of subfolders).
     */
    public static List<Ifo> loadFromFolder(String mapFolder) throws IOException {
        File dir = new File(mapFolder);
        File[] ifoFiles = dir.listFiles((d, name) -> name.toUpperCase().endsWith(".IFO"));
        if (ifoFiles == null) {
            ifoFiles = new File[0];
        }

        if (ifoFiles.length == 0) {
            List<File> nested = new ArrayList<>();
            File[] children = dir.listFiles(File::isDirectory);
            if (children != null) {
                for (File child : children) {
                    File[] sectorIfo = child.listFiles((d, name) -> name.toUpperCase().endsWith(".IFO"));
                    if (sectorIfo != null) {
                        nested.addAll(Arrays.asList(sectorIfo));
                    }
                }
            }
            ifoFiles = nested.toArray(new File[0]);
        }

        if (ifoFiles.length == 0) {
            return new ArrayList<>();
        }

        Arrays.sort(ifoFiles, Comparator.comparing(File::getName));
        List<Ifo> result = new ArrayList<>(ifoFiles.length);
        for (File file : ifoFiles) {
            result.add(new Ifo(file.getPath()));
        }
        return result;
    }

    /** Aggregates object counts across multiple IFO segments (large maps use several files). */
    public static IfoSummary summarize(List<Ifo> files) {
        IfoSummary s = new IfoSummary();
        s.fileCount = files.size();
        for (Ifo ifo : files) {
            s.decoration += ifo.decoration.size();
            s.construction += ifo.construction.size();
            s.npcs += ifo.npcs.size();
            s.monsters += ifo.monsters.size();
            s.water += ifo.water.size();
            s.warpGates += ifo.warpGates.size();
            s.effects += ifo.effects.size();
            s.sounds += ifo.sounds.size();
            s.animation += ifo.animation.size();
            s.collision += ifo.collision.size();
            s.eventTriggers += ifo.eventTriggers.size();
        }
        return s;
    }

    public static final class IfoSummary {
        public int fileCount;
        public int decoration;
        public int construction;
        public int npcs;
        public int monsters;
        public int water;
        public int warpGates;
        public int effects;
        public int sounds;
        public int animation;
        public int collision;
        public int eventTriggers;

        public int total() {
            return decoration + construction + npcs + monsters + water + warpGates
                + effects + sounds + animation + collision + eventTriggers;
        }

        @Override
        public String toString() {
            return String.format(
                "IFO x%d: %d objects (deco=%d build=%d npc=%d spawn=%d water=%d warp=%d anim=%d)",
                fileCount, total(), decoration, construction, npcs, monsters, water, warpGates, animation
            );
        }
    }
}
