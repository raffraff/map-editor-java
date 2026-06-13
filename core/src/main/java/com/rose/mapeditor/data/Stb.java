package com.rose.mapeditor.data;

import com.rose.mapeditor.io.FileHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rose Online STB table file. */
public final class Stb {

    public int rowSize;
    public String filePath;
    public List<Short> columnSizes = new ArrayList<>();
    public List<String> columnNames = new ArrayList<>();
    public List<List<String>> cells = new ArrayList<>();

    public Stb() {
    }

    public Stb(String filePath) throws IOException {
        load(filePath);
    }

    public void load(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.READING, FileHandler.eucKr())) {
            fh.readBaseString(4);
            fh.readInt(); // offset

            int rowCount = fh.readInt();
            int columnCount = fh.readInt();
            rowSize = fh.readInt();

            for (int i = 0; i < columnCount + 1; i++) {
                columnSizes.add(fh.readShort());
            }

            for (int i = 0; i < columnCount + 1; i++) {
                int nameLen = fh.readShort();
                columnNames.add(fh.readLengthString(nameLen));
            }

            cells = new ArrayList<>(rowCount);
            for (int i = 0; i < rowCount - 1; i++) {
                List<String> row = new ArrayList<>();
                int firstLen = fh.readShort();
                row.add(fh.readLengthString(firstLen));
                cells.add(row);
            }

            for (int i = 0; i < rowCount - 1; i++) {
                for (int j = 0; j < columnCount - 1; j++) {
                    int len = fh.readShort();
                    cells.get(i).add(fh.readLengthString(len));
                }
            }
        }
    }

    public String cell(int row, int column) {
        return cells.get(row).get(column);
    }

    public void save() throws IOException {
        save(filePath);
    }

    public void save(String filePath) throws IOException {
        this.filePath = filePath;
        try (FileHandler fh = new FileHandler(filePath, FileHandler.FileOpenMode.WRITING, FileHandler.eucKr())) {
            fh.writeBaseString("STB1");
            fh.writeInt(0);

            int rowCount = cells.size();
            int columnCount = rowCount > 0 ? cells.get(0).size() : 0;

            fh.writeInt(rowCount + 1);
            fh.writeInt(columnCount);
            fh.writeInt(rowSize);

            for (short columnSize : columnSizes) {
                fh.writeShort(columnSize);
            }

            for (String columnName : columnNames) {
                fh.writeLengthString(columnName);
            }

            for (List<String> row : cells) {
                fh.writeLengthString(row.get(0));
            }

            int dataOffset = (int) fh.tell();

            for (List<String> row : cells) {
                for (int j = 1; j < columnCount; j++) {
                    fh.writeLengthString(row.get(j));
                }
            }

            fh.seek(4);
            fh.writeInt(dataOffset);
        }
    }
}
