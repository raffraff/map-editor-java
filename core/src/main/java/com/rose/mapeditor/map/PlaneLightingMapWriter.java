package com.rose.mapeditor.map;



import java.io.IOException;

import java.io.OutputStream;

import java.nio.ByteBuffer;

import java.nio.ByteOrder;

import java.nio.file.Files;

import java.nio.file.Path;



/** Writes Rose terrain plane lighting maps (.DDS). */

public final class PlaneLightingMapWriter {



    public static final int SIZE = 512;



    private PlaneLightingMapWriter() {

    }



    /** Copies an existing lightmap when available, otherwise writes a flat neutral DDS. */

    public static void write(Path destination, Path template) throws IOException {

        if (template != null && Files.isRegularFile(template)) {

            Files.createDirectories(destination.getParent());

            Files.copy(template, destination);

            return;

        }

        writeNeutral(destination);

    }



    public static void writeNeutral(Path destination) throws IOException {

        writeBgra8888(destination, DdsBgraWriter.neutralPixels(SIZE, SIZE));

    }



    /** Writes a 512×512 uncompressed BGRA8888 DDS. */

    public static void writeBgra8888(Path destination, byte[] pixels) throws IOException {

        DdsBgraWriter.write(destination, SIZE, SIZE, pixels);

    }

}

