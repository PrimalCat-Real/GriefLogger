package com.daqem.grieflogger.util;

import com.daqem.grieflogger.GriefLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Utility class for compressing and decompressing data using GZIP.
 * Used primarily for NBT/DataComponentPatch data which can be large.
 */
public final class CompressionUtils {

    private static final int BUFFER_SIZE = 1024;
    private static final int COMPRESSION_THRESHOLD = 64; 

    private CompressionUtils() {
    }

    /**
     * Compresses a byte array using GZIP.
     * Returns original data if compression doesn't reduce size or data is too small.
     *
     * @param data the data to compress
     * @return compressed data with a header byte (0x00 = uncompressed, 0x01 = compressed)
     */
    public static byte[] compress(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        if (data.length < COMPRESSION_THRESHOLD) {
            return addHeader(data, false);
        }

        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(data.length);
            try (GZIPOutputStream gzipStream = new GZIPOutputStream(byteStream)) {
                gzipStream.write(data);
            }

            byte[] compressed = byteStream.toByteArray();

            if (compressed.length < data.length) {
                return addHeader(compressed, true);
            } else {
                return addHeader(data, false);
            }
        } catch (IOException e) {
            GriefLogger.LOGGER.warn("Failed to compress data, storing uncompressed", e);
            return addHeader(data, false);
        }
    }

    /**
     * Decompresses a byte array that was compressed with {@link #compress(byte[])}.
     *
     * @param data the data to decompress (with header byte)
     * @return decompressed data
     */
    public static byte[] decompress(byte[] data) {
        if (data == null || data.length <= 1) {
            return data;
        }

        boolean isCompressed = data[0] == 0x01;
        byte[] payload = new byte[data.length - 1];
        System.arraycopy(data, 1, payload, 0, payload.length);

        if (!isCompressed) {
            return payload;
        }

        try {
            ByteArrayInputStream byteStream = new ByteArrayInputStream(payload);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            try (GZIPInputStream gzipStream = new GZIPInputStream(byteStream)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int length;
                while ((length = gzipStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }
            }

            return outputStream.toByteArray();
        } catch (IOException e) {
            GriefLogger.LOGGER.error("Failed to decompress data", e);
            return payload; 
        }
    }

    /**
     * Adds a header byte to indicate compression status.
     */
    private static byte[] addHeader(byte[] data, boolean compressed) {
        byte[] result = new byte[data.length + 1];
        result[0] = (byte) (compressed ? 0x01 : 0x00);
        System.arraycopy(data, 0, result, 1, data.length);
        return result;
    }

    /**
     * Checks if data appears to be compressed (has compression header).
     */
    public static boolean hasCompressionHeader(byte[] data) {
        return data != null && data.length > 0 && (data[0] == 0x00 || data[0] == 0x01);
    }
}
