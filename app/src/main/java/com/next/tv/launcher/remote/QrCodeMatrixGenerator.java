package com.next.tv.launcher.remote;

import com.next.tv.launcher.Config;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Lightweight, zero-dependency QR Code generator (QR Model 2, Version 3, 29x29).
 * Encodes URLs up to 53 bytes (suitable for http://192.168.x.x:8888).
 * Keeps APK size strictly minimal without pulling in 600KB+ ZXing library.
 */
public final class QrCodeMatrixGenerator {

    private static final int VERSION = 3;
    private static final int SIZE = 29; // 17 + 4 * 3
    private static final int TOTAL_CODEWORDS = 70;
    private static final int DATA_CODEWORDS = 44; // Error Correction Level L
    private static final int EC_CODEWORDS = 26;

    // Reed-Solomon Galois Field GF(256) tables
    private static final int[] EXP_TABLE = new int[256];
    private static final int[] LOG_TABLE = new int[256];

    static {
        int x = 1;
        for (int i = 0; i < 255; i++) {
            EXP_TABLE[i] = x;
            LOG_TABLE[x] = i;
            x <<= 1;
            if ((x & 0x100) != 0) {
                x ^= 0x11D;
            }
        }
        EXP_TABLE[255] = EXP_TABLE[0];
    }

    private static int gmult(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP_TABLE[(LOG_TABLE[a] + LOG_TABLE[b]) % 255];
    }

    private static int[] generateEcPolynomial(int numEc) {
        int[] poly = new int[]{1};
        for (int i = 0; i < numEc; i++) {
            int factor = EXP_TABLE[i];
            int[] next = new int[poly.length + 1];
            for (int j = 0; j < poly.length; j++) {
                next[j] ^= gmult(poly[j], factor);
                next[j + 1] ^= poly[j];
            }
            poly = next;
        }
        return poly;
    }

    private static int[] computeReedSolomon(int[] data, int numEc) {
        int[] poly = generateEcPolynomial(numEc);
        int[] res = new int[numEc];
        for (int d : data) {
            int lead = d ^ res[0];
            System.arraycopy(res, 1, res, 0, numEc - 1);
            res[numEc - 1] = 0;
            for (int i = 0; i < numEc; i++) {
                res[i] ^= gmult(poly[poly.length - 2 - i], lead);
            }
        }
        return res;
    }

    public static Bitmap generateQrBitmap(String text, int targetPx) {
        byte[] rawBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        if (rawBytes.length > 53) {
            // Trim if exceeds Version 3-L capacity
            rawBytes = Arrays.copyOf(rawBytes, 53);
        }

        // 1. Bitstream assembly: 4 bits mode (0100 = Byte), 8 bits length, data, 4 bits terminator
        BitBuffer bits = new BitBuffer();
        bits.appendBits(0b0100, 4); // Byte mode
        bits.appendBits(rawBytes.length, 8); // Character count for Ver 1-9
        for (byte b : rawBytes) {
            bits.appendBits(b & 0xFF, 8);
        }
        // Terminator
        int pad = Math.min(4, DATA_CODEWORDS * 8 - bits.bitLength);
        bits.appendBits(0, pad);
        // Align to byte
        if (bits.bitLength % 8 != 0) {
            bits.appendBits(0, 8 - (bits.bitLength % 8));
        }
        // Pad bytes
        int[] padBytes = new int[]{0xEC, 0x11};
        int padIdx = 0;
        while (bits.bitLength < DATA_CODEWORDS * 8) {
            bits.appendBits(padBytes[padIdx % 2], 8);
            padIdx++;
        }

        int[] dataWords = new int[DATA_CODEWORDS];
        for (int i = 0; i < DATA_CODEWORDS; i++) {
            dataWords[i] = bits.getByte(i);
        }

        // 2. Reed-Solomon EC computation
        int[] ecWords = computeReedSolomon(dataWords, EC_CODEWORDS);

        int[] finalWords = new int[TOTAL_CODEWORDS];
        System.arraycopy(dataWords, 0, finalWords, 0, DATA_CODEWORDS);
        System.arraycopy(ecWords, 0, finalWords, DATA_CODEWORDS, EC_CODEWORDS);

        // 3. Matrix construction
        int[][] matrix = new int[SIZE][SIZE];
        boolean[][] isFunction = new boolean[SIZE][SIZE];

        // Place Finder Patterns (Top-Left, Top-Right, Bottom-Left)
        placeFinderPattern(matrix, isFunction, 0, 0);
        placeFinderPattern(matrix, isFunction, SIZE - 7, 0);
        placeFinderPattern(matrix, isFunction, 0, SIZE - 7);

        // Alignment pattern for Version 3 (center at row 22, col 22)
        placeAlignmentPattern(matrix, isFunction, 22, 22);

        // Timing patterns
        for (int i = 8; i < SIZE - 8; i++) {
            int val = (i % 2 == 0) ? 1 : 0;
            if (!isFunction[6][i]) {
                matrix[6][i] = val;
                isFunction[6][i] = true;
            }
            if (!isFunction[i][6]) {
                matrix[i][6] = val;
                isFunction[i][6] = true;
            }
        }

        // Dark module
        matrix[4 * VERSION + 9][8] = 1;
        isFunction[4 * VERSION + 9][8] = true;

        // Reserve format info areas
        reserveFormatInfo(isFunction);

        // 4. Place data bits (Zigzag scan right-to-left in 2-column pairs)
        BitBuffer allBits = new BitBuffer();
        for (int w : finalWords) {
            allBits.appendBits(w, 8);
        }

        int bitIdx = 0;
        boolean upwards = true;
        for (int right = SIZE - 1; right > 0; right -= 2) {
            if (right == 6) right--; // Skip vertical timing column
            for (int vert = 0; vert < SIZE; vert++) {
                int r = upwards ? (SIZE - 1 - vert) : vert;
                for (int c = right; c >= right - 1; c--) {
                    if (!isFunction[r][c]) {
                        int bit = (bitIdx < allBits.bitLength) ? allBits.getBit(bitIdx) : 0;
                        bitIdx++;
                        // Apply Mask Pattern 0: (row + col) % 2 == 0
                        boolean invert = ((r + c) % 2 == 0);
                        matrix[r][c] = invert ? (bit ^ 1) : bit;
                    }
                }
            }
            upwards = !upwards;
        }

        // Place Format Info: Level L, Mask 0 = 0b01 000 -> Format string 0x77C4 (masked with 0x5412)
        int formatBits = 0x77C4 ^ 0x5412; // 15 bits
        placeFormatInfo(matrix, formatBits);

        // 5. Render to ARGB Bitmap with 2-module quiet zone
        int quietZone = 2;
        int totalModules = SIZE + quietZone * 2;
        int scale = Math.max(1, targetPx / totalModules);
        int finalPx = totalModules * scale;

        Bitmap bitmap = Bitmap.createBitmap(finalPx, finalPx, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[finalPx * finalPx];
        Arrays.fill(pixels, Color.WHITE);

        for (int r = 0; r < SIZE; r++) {
            for (int c = 0; c < SIZE; c++) {
                if (matrix[r][c] == 1) {
                    int startX = (c + quietZone) * scale;
                    int startY = (r + quietZone) * scale;
                    for (int dy = 0; dy < scale; dy++) {
                        int yIdx = (startY + dy) * finalPx;
                        for (int dx = 0; dx < scale; dx++) {
                            pixels[yIdx + startX + dx] = Color.BLACK;
                        }
                    }
                }
            }
        }

        bitmap.setPixels(pixels, 0, finalPx, 0, 0, finalPx, finalPx);
        return bitmap;
    }

    private static void placeFinderPattern(int[][] mat, boolean[][] isFunc, int startR, int startC) {
        for (int r = -1; r <= 7; r++) {
            for (int c = -1; c <= 7; c++) {
                int qr = startR + r;
                int qc = startC + c;
                if (qr >= 0 && qr < SIZE && qc >= 0 && qc < SIZE) {
                    isFunc[qr][qc] = true;
                    if (r >= 0 && r <= 6 && c >= 0 && c <= 6) {
                        if (r == 0 || r == 6 || c == 0 || c == 6 || (r >= 2 && r <= 4 && c >= 2 && c <= 4)) {
                            mat[qr][qc] = 1;
                        } else {
                            mat[qr][qc] = 0;
                        }
                    } else {
                        mat[qr][qc] = 0; // Separator
                    }
                }
            }
        }
    }

    private static void placeAlignmentPattern(int[][] mat, boolean[][] isFunc, int centerR, int centerC) {
        for (int r = -2; r <= 2; r++) {
            for (int c = -2; c <= 2; c++) {
                int qr = centerR + r;
                int qc = centerC + c;
                isFunc[qr][qc] = true;
                if (Math.abs(r) == 2 || Math.abs(c) == 2 || (r == 0 && c == 0)) {
                    mat[qr][qc] = 1;
                } else {
                    mat[qr][qc] = 0;
                }
            }
        }
    }

    private static void reserveFormatInfo(boolean[][] isFunc) {
        for (int i = 0; i <= 8; i++) {
            isFunc[8][i] = true;
            isFunc[i][8] = true;
        }
        for (int i = SIZE - 8; i < SIZE; i++) {
            isFunc[8][i] = true;
        }
        for (int i = SIZE - 7; i < SIZE; i++) {
            isFunc[i][8] = true;
        }
    }

    private static void placeFormatInfo(int[][] mat, int formatBits) {
        // 15 bits of format info
        int[] b = new int[15];
        for (int i = 0; i < 15; i++) {
            b[i] = (formatBits >> i) & 1;
        }

        // Top-left finder surrounding
        mat[8][0] = b[0];
        mat[8][1] = b[1];
        mat[8][2] = b[2];
        mat[8][3] = b[3];
        mat[8][4] = b[4];
        mat[8][5] = b[5];
        mat[8][7] = b[6];
        mat[8][8] = b[7];
        mat[7][8] = b[8];
        mat[5][8] = b[9];
        mat[4][8] = b[10];
        mat[3][8] = b[11];
        mat[2][8] = b[12];
        mat[1][8] = b[13];
        mat[0][8] = b[14];

        // Top-right and bottom-left
        for (int i = 0; i < 7; i++) {
            mat[8][SIZE - 1 - i] = b[i];
        }
        for (int i = 0; i < 8; i++) {
            mat[SIZE - 8 + i][8] = b[7 + i];
        }
    }

    private static final class BitBuffer {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private int currentByte = 0;
        private int numBitsInCurrent = 0;
        int bitLength = 0;

        void appendBits(int value, int numBits) {
            for (int i = numBits - 1; i >= 0; i--) {
                int bit = (value >> i) & 1;
                currentByte = (currentByte << 1) | bit;
                numBitsInCurrent++;
                bitLength++;
                if (numBitsInCurrent == 8) {
                    bytes.write(currentByte);
                    currentByte = 0;
                    numBitsInCurrent = 0;
                }
            }
        }

        int getByte(int index) {
            byte[] arr = bytes.toByteArray();
            return (index < arr.length) ? (arr[index] & 0xFF) : 0;
        }

        int getBit(int index) {
            byte[] arr = bytes.toByteArray();
            int byteIdx = index / 8;
            int bitIdx = 7 - (index % 8);
            if (byteIdx < arr.length) {
                return (arr[byteIdx] >> bitIdx) & 1;
            }
            return 0;
        }
    }
}
