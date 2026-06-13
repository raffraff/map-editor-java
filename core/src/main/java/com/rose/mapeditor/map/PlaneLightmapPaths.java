package com.rose.mapeditor.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Resolves Rose terrain plane lightmap (.DDS) paths for map-root and legacy nested layouts. */
public final class PlaneLightmapPaths {

    private PlaneLightmapPaths() {
    }

    /** Map folder that contains sector HIM/TIL files (parent of the .ZON file). */
    public static Path resolveMapRoot(Path himPath) {
        Path parent = himPath.getParent();
        if (parent == null) {
            return himPath;
        }
        String sectorName = sectorNameFromHim(himPath);
        if (sectorName != null && parent.getFileName().toString().equalsIgnoreCase(sectorName)) {
            Path mapRoot = parent.getParent();
            return mapRoot != null ? mapRoot : parent;
        }
        return parent;
    }

    public static Path resolvePlaneLightmapPath(Path mapRoot, int cellY, int cellX) {
        String sectorName = cellY + "_" + cellX;
        return mapRoot.resolve(sectorName + "/" + sectorName + "_PLANELIGHTINGMAP.DDS");
    }

    /** Candidate paths ordered by Rose canonical layout, then legacy fallbacks. */
    public static List<Path> listCandidates(Path himPath, int cellY, int cellX) {
        Path mapRoot = resolveMapRoot(himPath);
        String sectorName = cellY + "_" + cellX;
        List<Path> candidates = new ArrayList<>(4);
        candidates.add(mapRoot.resolve(sectorName + "/" + sectorName + "_PLANELIGHTINGMAP.DDS"));
        candidates.add(mapRoot.resolve(sectorName + "_PLANELIGHTINGMAP.DDS"));

        Path himParent = himPath.getParent();
        if (himParent != null) {
            Path nested = himParent.resolve(sectorName + "_PLANELIGHTINGMAP.DDS");
            if (!candidates.contains(nested)) {
                candidates.add(nested);
            }
        }
        return candidates;
    }

    public static Path firstExisting(Path himPath, int cellY, int cellX) {
        for (Path candidate : listCandidates(himPath, cellY, cellX)) {
            if (candidate.toFile().exists()) {
                return candidate;
            }
        }
        return resolvePlaneLightmapPath(resolveMapRoot(himPath), cellY, cellX);
    }

    private static String sectorNameFromHim(Path himPath) {
        String fileName = himPath.getFileName().toString();
        if (fileName.length() < 9 || !fileName.toUpperCase().endsWith(".HIM")) {
            return null;
        }
        String sectorName = fileName.substring(0, fileName.length() - 4);
        return sectorName.length() >= 5 && sectorName.charAt(2) == '_' ? sectorName : null;
    }
}
