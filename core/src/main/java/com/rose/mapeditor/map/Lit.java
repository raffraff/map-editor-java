package com.rose.mapeditor.map;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Rose Online object lightmap atlas metadata (.LIT). */
public final class Lit {

    public static final class Part {
        public String tgaName;
        public int partId;
        public String ddsName;
        public int lightmapId;
        public int pixelsPerObject;
        public int objectsPerWidth;
        public int mapPosition;
    }

    public static final class LitObject {
        public int objectId;
        public List<Part> parts = new ArrayList<>();
    }

    public static final class DdsEntry {
        public String fileName;
    }

    public String folder;
    public String filePath;
    public List<LitObject> objects = new ArrayList<>();
    public List<DdsEntry> ddsFiles = new ArrayList<>();

    public Lit() {
    }

    public Lit(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        this.folder = Path.of(filePath).getParent().toString();
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            int objectCount = fh.readInt();
            objects = new ArrayList<>(objectCount);
            for (int i = 0; i < objectCount; i++) {
                int partCount = fh.readInt();
                LitObject obj = new LitObject();
                obj.objectId = fh.readInt();
                obj.parts = new ArrayList<>(partCount);
                for (int j = 0; j < partCount; j++) {
                    Part part = new Part();
                    part.tgaName = fh.readBString();
                    part.partId = fh.readInt();
                    part.ddsName = fh.readBString();
                    part.lightmapId = fh.readInt();
                    part.pixelsPerObject = fh.readInt();
                    part.objectsPerWidth = fh.readInt();
                    part.mapPosition = fh.readInt();
                    obj.parts.add(part);
                }
                objects.add(obj);
            }

            int ddsCount = fh.readInt();
            ddsFiles = new ArrayList<>(ddsCount);
            for (int i = 0; i < ddsCount; i++) {
                DdsEntry dds = new DdsEntry();
                dds.fileName = fh.readBString();
                ddsFiles.add(dds);
            }
        }
    }

    public int searchObject(int id) {
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i).objectId == id) {
                return i;
            }
        }
        return -1;
    }

    public int searchPart(int objectIndex, int partId) {
        List<Part> parts = objects.get(objectIndex).parts;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).partId == partId) {
                return i;
            }
        }
        return -1;
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        this.folder = Path.of(filePath).getParent().toString();
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeInt(objects.size());
            for (LitObject obj : objects) {
                fh.writeInt(obj.parts.size());
                fh.writeInt(obj.objectId);
                for (Part part : obj.parts) {
                    fh.writeBString(part.tgaName != null ? part.tgaName : "");
                    fh.writeInt(part.partId);
                    fh.writeBString(part.ddsName != null ? part.ddsName : "");
                    fh.writeInt(part.lightmapId);
                    fh.writeInt(part.pixelsPerObject);
                    fh.writeInt(part.objectsPerWidth);
                    fh.writeInt(part.mapPosition);
                }
            }
            fh.writeInt(ddsFiles.size());
            for (DdsEntry dds : ddsFiles) {
                fh.writeBString(dds.fileName != null ? dds.fileName : "");
            }
        }
    }

    public void save() throws IOException {
        save(filePath);
    }
}
