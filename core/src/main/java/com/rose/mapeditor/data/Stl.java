package com.rose.mapeditor.data;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online STL string table. */
public final class Stl {

    public static final class Entry {
        public String stringId;
        public int id;
    }

    public static final class Row {
        public String text;
        public String comment;
        public String quest1;
        public String quest2;
    }

    public String type;
    public String filePath;
    public List<Entry> entries = new ArrayList<>();
    public List<List<Row>> rows = new ArrayList<>();

    public Stl() {
    }

    public Stl(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            type = fh.readBString();
            int entryCount = fh.readInt();

            entries = new ArrayList<>(entryCount);
            for (int i = 0; i < entryCount; i++) {
                Entry entry = new Entry();
                entry.stringId = fh.readBString();
                entry.id = fh.readInt();
                entries.add(entry);
            }

            int languageCount = fh.readInt();
            int[] languageOffsets = new int[languageCount];
            for (int i = 0; i < languageCount; i++) {
                languageOffsets[i] = fh.readInt();
            }

            int[][] entryOffsets = new int[languageCount][entryCount];
            for (int i = 0; i < languageCount; i++) {
                fh.seek(languageOffsets[i]);
                for (int j = 0; j < entryCount; j++) {
                    entryOffsets[i][j] = fh.readInt();
                }
            }

            rows = new ArrayList<>(languageCount);
            for (int i = 0; i < languageCount; i++) {
                List<Row> languageRows = new ArrayList<>(entryCount);
                for (int j = 0; j < entryCount; j++) {
                    Row row = new Row();
                    fh.seek(entryOffsets[i][j]);
                    row.text = fh.readBString();
                    if ("QEST01".equals(type) || "ITST01".equals(type)) {
                        row.comment = fh.readBString();
                        if ("QEST01".equals(type)) {
                            row.quest1 = fh.readBString();
                            row.quest2 = fh.readBString();
                        }
                    }
                    languageRows.add(row);
                }
                rows.add(languageRows);
            }
        }
    }

    /** Returns localized text for the given string ID (language index 1). */
    public String search(String stringId) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).stringId.equalsIgnoreCase(stringId)) {
                if (rows.size() > 1 && i < rows.get(1).size()) {
                    return rows.get(1).get(i).text;
                }
                if (!rows.isEmpty() && i < rows.get(0).size()) {
                    return rows.get(0).get(i).text;
                }
            }
        }
        return stringId;
    }

    public void save() throws IOException {
        save(filePath);
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeBString(type != null ? type : "");

            fh.writeInt(entries.size());
            for (Entry entry : entries) {
                fh.writeBString(entry.stringId != null ? entry.stringId : "");
                fh.writeInt(entry.id);
            }

            fh.writeInt(rows.size());
            int languageOffset = (int) fh.tell();
            fh.writeBytes(new byte[rows.size() * 4]);

            int[] languageOffsets = new int[rows.size()];
            for (int i = 0; i < rows.size(); i++) {
                languageOffsets[i] = (int) fh.tell();
                fh.writeBytes(new byte[entries.size() * 4]);
            }

            for (int i = 0; i < rows.size(); i++) {
                int[] entryOffsets = new int[entries.size()];
                for (int j = 0; j < entries.size(); j++) {
                    entryOffsets[j] = (int) fh.tell();
                    Row row = rows.get(i).get(j);
                    fh.writeBString(row.text != null ? row.text : "");
                    if ("QEST01".equals(type) || "ITST01".equals(type)) {
                        fh.writeBString(row.comment != null ? row.comment : "");
                        if ("QEST01".equals(type)) {
                            fh.writeBString(row.quest1 != null ? row.quest1 : "");
                            fh.writeBString(row.quest2 != null ? row.quest2 : "");
                        }
                    }
                }

                int rowEnd = (int) fh.tell();
                fh.seek(languageOffsets[i]);
                for (int entryOffset : entryOffsets) {
                    fh.writeInt(entryOffset);
                }
                fh.seek(rowEnd);
            }

            fh.seek(languageOffset);
            for (int languageOffsetValue : languageOffsets) {
                fh.writeInt(languageOffsetValue);
            }
        }
    }
}
