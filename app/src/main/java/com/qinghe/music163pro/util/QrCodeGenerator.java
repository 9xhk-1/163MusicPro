package com.qinghe.music163pro.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Minimal QR Code generator (versions 1-10, error correction level M, byte mode).
 * Pure Java, no external dependencies. Produces a boolean[][] matrix where
 * true = black module, false = white module.
 */
public final class QrCodeGenerator {

    private QrCodeGenerator() {}

    // --- Version tables (indices 1-10) ---

    // Total codewords per version
    private static final int[] TOTAL_CODEWORDS = {
        0, 26, 44, 70, 100, 134, 172, 196, 242, 292, 346
    };

    // EC codewords per block for level M
    private static final int[] EC_CODEWORDS_PER_BLOCK = {
        0, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26
    };

    // Number of EC blocks (group1Count, group1DataCw, group2Count, group2DataCw) for level M
    private static final int[][] BLOCK_INFO = {
        {},                  // 0 unused
        {1, 16, 0, 0},      // v1
        {1, 28, 0, 0},      // v2
        {1, 44, 0, 0},      // v3
        {2, 32, 0, 0},      // v4
        {2, 43, 0, 0},      // v5
        {4, 27, 0, 0},      // v6
        {4, 31, 0, 0},      // v7
        {2, 38, 2, 39},     // v8
        {3, 36, 2, 37},     // v9
        {4, 43, 1, 44},     // v10
    };

    // Byte-mode capacity at EC level M per version
    private static final int[] BYTE_CAPACITY_M = {
        0, 14, 26, 42, 62, 84, 106, 122, 152, 180, 213
    };

    // Alignment pattern center positions per version (empty for v1)
    private static final int[][] ALIGNMENT_POSITIONS = {
        {},          // 0
        {},          // 1
        {6, 18},     // 2
        {6, 22},     // 3
        {6, 26},     // 4
        {6, 30},     // 5
        {6, 34},     // 6
        {6, 22, 38}, // 7
        {6, 24, 42}, // 8
        {6, 26, 46}, // 9
        {6, 28, 50}, // 10
    };

    // Version information bit strings for versions 7-10
    private static final int[] VERSION_INFO_BITS = {
        0, 0, 0, 0, 0, 0, 0,
        0b000111110010010100, // v7
        0b001000010110111100, // v8
        0b001001101010011001, // v9
        0b001010010011010011, // v10
    };

    /**
     * Encode a string into a QR code boolean matrix.
     * @param content the text to encode (must fit in versions 1-10 at EC level M)
     * @return boolean[][] where true = black, false = white
     */
    public static boolean[][] encode(String content) {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        int version = 0;
        for (int v = 1; v <= 10; v++) {
            if (data.length <= BYTE_CAPACITY_M[v]) {
                version = v;
                break;
            }
        }
        if (version == 0) {
            throw new IllegalArgumentException(
                "Content too long for QR versions 1-10 at EC level M");
        }

        int size = 17 + version * 4;

        // Step 1: Build data codewords
        int[] dataCodewords = buildDataCodewords(data, version);

        // Step 2: Build EC codewords and interleave
        int[] finalMessage = buildFinalMessage(dataCodewords, version);

        // Step 3: Try all 8 masks, pick best
        boolean[][] bestMatrix = null;
        int bestPenalty = Integer.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] modules = new boolean[size][size];
            boolean[][] isFunction = new boolean[size][size];

            placeFinderPatterns(modules, isFunction, size);
            placeTimingPatterns(modules, isFunction, size);
            placeAlignmentPatterns(modules, isFunction, size, version);
            placeDarkModule(modules, isFunction, version);
            reserveFormatArea(isFunction, size, version);

            placeDataBits(modules, isFunction, finalMessage, size);
            applyMask(modules, isFunction, mask, size);
            placeFormatInfo(modules, size, mask);
            if (version >= 7) {
                placeVersionInfo(modules, size, version);
            }

            int penalty = computePenalty(modules, size);
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMatrix = modules;
            }
        }
        return bestMatrix;
    }

    // ----- Data encoding (byte mode) -----

    private static int[] buildDataCodewords(byte[] data, int version) {
        int[] blockInfo = BLOCK_INFO[version];
        int g1Count = blockInfo[0], g1Data = blockInfo[1];
        int g2Count = blockInfo[2], g2Data = blockInfo[3];
        int totalDataCw = g1Count * g1Data + g2Count * g2Data;

        // Character count indicator length: 8 bits for versions 1-9, 16 for 10+
        int cciBits = (version <= 9) ? 8 : 16;

        // Build bit stream
        BitBuffer bits = new BitBuffer();
        bits.append(0b0100, 4);          // Byte mode indicator
        bits.append(data.length, cciBits); // Character count
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }

        // Terminator (up to 4 zero bits)
        int capacityBits = totalDataCw * 8;
        int terminatorLen = Math.min(4, capacityBits - bits.length());
        bits.append(0, terminatorLen);

        // Pad to byte boundary
        while (bits.length() % 8 != 0) {
            bits.append(0, 1);
        }

        // Pad codewords 0xEC, 0x11
        int padBytes = (capacityBits - bits.length()) / 8;
        for (int i = 0; i < padBytes; i++) {
            bits.append((i % 2 == 0) ? 0xEC : 0x11, 8);
        }

        int[] codewords = new int[totalDataCw];
        for (int i = 0; i < totalDataCw; i++) {
            codewords[i] = bits.getByte(i);
        }
        return codewords;
    }

    // ----- Final message: EC + interleave -----

    private static int[] buildFinalMessage(int[] dataCodewords, int version) {
        int[] blockInfo = BLOCK_INFO[version];
        int g1Count = blockInfo[0], g1Data = blockInfo[1];
        int g2Count = blockInfo[2], g2Data = blockInfo[3];
        int ecPerBlock = EC_CODEWORDS_PER_BLOCK[version];
        int totalBlocks = g1Count + g2Count;

        int[][] dataBlocks = new int[totalBlocks][];
        int[][] ecBlocks = new int[totalBlocks][];
        int offset = 0;

        for (int i = 0; i < totalBlocks; i++) {
            int blockLen = (i < g1Count) ? g1Data : g2Data;
            dataBlocks[i] = Arrays.copyOfRange(dataCodewords, offset, offset + blockLen);
            offset += blockLen;
            ecBlocks[i] = reedSolomonEncode(dataBlocks[i], ecPerBlock);
        }

        // Interleave data codewords
        int totalCw = TOTAL_CODEWORDS[version];
        int[] result = new int[totalCw];
        int idx = 0;

        int maxDataLen = (g2Count > 0) ? g2Data : g1Data;
        for (int col = 0; col < maxDataLen; col++) {
            for (int blk = 0; blk < totalBlocks; blk++) {
                if (col < dataBlocks[blk].length) {
                    result[idx++] = dataBlocks[blk][col];
                }
            }
        }

        // Interleave EC codewords
        for (int col = 0; col < ecPerBlock; col++) {
            for (int blk = 0; blk < totalBlocks; blk++) {
                result[idx++] = ecBlocks[blk][col];
            }
        }

        return result;
    }

    // ----- Reed-Solomon over GF(256) -----

    private static final int[] GF_EXP = new int[512];
    private static final int[] GF_LOG = new int[256];

    static {
        int val = 1;
        for (int i = 0; i < 255; i++) {
            GF_EXP[i] = val;
            GF_LOG[val] = i;
            val <<= 1;
            if (val >= 256) val ^= 0x11D; // primitive polynomial x^8+x^4+x^3+x^2+1
        }
        // Duplicate for simpler modular arithmetic
        for (int i = 255; i < 512; i++) {
            GF_EXP[i] = GF_EXP[i - 255];
        }
    }

    private static int gfMul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return GF_EXP[GF_LOG[a] + GF_LOG[b]];
    }

    private static int[] reedSolomonEncode(int[] data, int ecCount) {
        // Build generator polynomial
        int[] gen = {1};
        for (int i = 0; i < ecCount; i++) {
            int[] factor = {1, GF_EXP[i]};
            gen = polyMul(gen, factor);
        }

        // Polynomial division
        int[] msg = new int[data.length + ecCount];
        System.arraycopy(data, 0, msg, 0, data.length);

        for (int i = 0; i < data.length; i++) {
            int coef = msg[i];
            if (coef != 0) {
                for (int j = 1; j < gen.length; j++) {
                    msg[i + j] ^= gfMul(gen[j], coef);
                }
            }
        }

        return Arrays.copyOfRange(msg, data.length, msg.length);
    }

    private static int[] polyMul(int[] a, int[] b) {
        int[] result = new int[a.length + b.length - 1];
        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < b.length; j++) {
                result[i + j] ^= gfMul(a[i], b[j]);
            }
        }
        return result;
    }

    // ----- Matrix construction -----

    private static void placeFinderPatterns(boolean[][] m, boolean[][] f, int size) {
        int[][] origins = {{0, 0}, {size - 7, 0}, {0, size - 7}};
        for (int[] o : origins) {
            int r0 = o[0], c0 = o[1];
            for (int r = -1; r <= 7; r++) {
                for (int c = -1; c <= 7; c++) {
                    int rr = r0 + r, cc = c0 + c;
                    if (rr < 0 || rr >= size || cc < 0 || cc >= size) continue;
                    boolean black;
                    if (r == -1 || r == 7 || c == -1 || c == 7) {
                        black = false; // separator
                    } else if (r == 0 || r == 6 || c == 0 || c == 6) {
                        black = true;
                    } else if (r >= 2 && r <= 4 && c >= 2 && c <= 4) {
                        black = true;
                    } else {
                        black = false;
                    }
                    m[rr][cc] = black;
                    f[rr][cc] = true;
                }
            }
        }
    }

    private static void placeTimingPatterns(boolean[][] m, boolean[][] f, int size) {
        for (int i = 8; i < size - 8; i++) {
            boolean black = (i % 2 == 0);
            if (!f[6][i]) {
                m[6][i] = black;
                f[6][i] = true;
            }
            if (!f[i][6]) {
                m[i][6] = black;
                f[i][6] = true;
            }
        }
    }

    private static void placeAlignmentPatterns(boolean[][] m, boolean[][] f, int size, int version) {
        int[] positions = ALIGNMENT_POSITIONS[version];
        if (positions.length == 0) return;

        for (int cy : positions) {
            for (int cx : positions) {
                // Skip if overlapping with finder patterns
                if (isNearFinder(cy, cx, size)) continue;

                for (int dr = -2; dr <= 2; dr++) {
                    for (int dc = -2; dc <= 2; dc++) {
                        int r = cy + dr, c = cx + dc;
                        boolean black = (dr == -2 || dr == 2 || dc == -2 || dc == 2
                                || (dr == 0 && dc == 0));
                        m[r][c] = black;
                        f[r][c] = true;
                    }
                }
            }
        }
    }

    private static boolean isNearFinder(int row, int col, int size) {
        // Top-left finder: rows 0-8, cols 0-8
        if (row <= 8 && col <= 8) return true;
        // Top-right finder: rows 0-8, cols size-9 to size-1
        if (row <= 8 && col >= size - 9) return true;
        // Bottom-left finder: rows size-9 to size-1, cols 0-8
        if (row >= size - 9 && col <= 8) return true;
        return false;
    }

    private static void placeDarkModule(boolean[][] m, boolean[][] f, int version) {
        int row = 4 * version + 9;
        m[row][8] = true;
        f[row][8] = true;
    }

    private static void reserveFormatArea(boolean[][] f, int size, int version) {
        // Around top-left finder
        for (int i = 0; i <= 8; i++) {
            f[8][i] = true;
            f[i][8] = true;
        }
        // Around top-right finder
        for (int i = 0; i <= 7; i++) {
            f[8][size - 1 - i] = true;
        }
        // Around bottom-left finder
        for (int i = 0; i <= 7; i++) {
            f[size - 1 - i][8] = true;
        }

        // Version info areas (versions >= 7)
        if (version >= 7) {
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 3; j++) {
                    f[i][size - 11 + j] = true;
                    f[size - 11 + j][i] = true;
                }
            }
        }
    }

    private static void placeDataBits(boolean[][] m, boolean[][] f, int[] codewords, int size) {
        int bitIndex = 0;
        int totalBits = codewords.length * 8;

        // Columns go right-to-left in pairs; skip column 6 (timing)
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5; // skip timing column

            // Determine direction: upward or downward
            boolean upward = ((size - 1 - right) / 2) % 2 == 0;

            for (int cnt = 0; cnt < size; cnt++) {
                int row = upward ? (size - 1 - cnt) : cnt;
                for (int dx = 0; dx <= 1; dx++) {
                    int col = right - dx;
                    if (col < 0) continue;
                    if (f[row][col]) continue;
                    if (bitIndex < totalBits) {
                        int cw = codewords[bitIndex / 8];
                        int bit = (cw >> (7 - (bitIndex % 8))) & 1;
                        m[row][col] = (bit == 1);
                        bitIndex++;
                    }
                }
            }
        }
    }

    private static void applyMask(boolean[][] m, boolean[][] f, int maskPattern, int size) {
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (f[r][c]) continue;
                boolean invert;
                switch (maskPattern) {
                    case 0: invert = (r + c) % 2 == 0; break;
                    case 1: invert = r % 2 == 0; break;
                    case 2: invert = c % 3 == 0; break;
                    case 3: invert = (r + c) % 3 == 0; break;
                    case 4: invert = (r / 2 + c / 3) % 2 == 0; break;
                    case 5: invert = (r * c) % 2 + (r * c) % 3 == 0; break;
                    case 6: invert = ((r * c) % 2 + (r * c) % 3) % 2 == 0; break;
                    case 7: invert = ((r + c) % 2 + (r * c) % 3) % 2 == 0; break;
                    default: invert = false;
                }
                if (invert) {
                    m[r][c] = !m[r][c];
                }
            }
        }
    }

    // Format info: EC level M = 00, mask 0-7
    // 15-bit BCH encoded with generator 10100110111
    private static final int[] FORMAT_BITS = {
        0x5412, // mask 0
        0x5125, // mask 1
        0x5E7C, // mask 2
        0x5B4B, // mask 3
        0x45F9, // mask 4
        0x40CE, // mask 5
        0x4F97, // mask 6
        0x4AA0, // mask 7
    };

    private static void placeFormatInfo(boolean[][] m, int size, int mask) {
        int bits = FORMAT_BITS[mask];
        // Around top-left
        int[] row1 = {0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8};
        int[] col1 = {8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0};

        for (int i = 0; i < 15; i++) {
            boolean black = ((bits >> (14 - i)) & 1) == 1;
            m[row1[i]][col1[i]] = black;
        }

        // Second copy: bottom-left and top-right
        for (int i = 0; i < 7; i++) {
            boolean black = ((bits >> (14 - i)) & 1) == 1;
            m[size - 1 - i][8] = black;
        }
        for (int i = 7; i < 15; i++) {
            boolean black = ((bits >> (14 - i)) & 1) == 1;
            m[8][size - 15 + i] = black;
        }
    }

    private static void placeVersionInfo(boolean[][] m, int size, int version) {
        int bits = VERSION_INFO_BITS[version];
        for (int i = 0; i < 18; i++) {
            boolean black = ((bits >> i) & 1) == 1;
            int row = i / 3;
            int col = size - 11 + (i % 3);
            m[row][col] = black;
            m[col][row] = black; // transposed copy
        }
    }

    // ----- Penalty scoring -----

    private static int computePenalty(boolean[][] m, int size) {
        return penalty1(m, size) + penalty2(m, size)
             + penalty3(m, size) + penalty4(m, size);
    }

    // Rule 1: runs of 5+ same-color in a row/column
    private static int penalty1(boolean[][] m, int size) {
        int score = 0;
        for (int r = 0; r < size; r++) {
            int run = 1;
            for (int c = 1; c < size; c++) {
                if (m[r][c] == m[r][c - 1]) {
                    run++;
                } else {
                    if (run >= 5) score += run - 2;
                    run = 1;
                }
            }
            if (run >= 5) score += run - 2;
        }
        for (int c = 0; c < size; c++) {
            int run = 1;
            for (int r = 1; r < size; r++) {
                if (m[r][c] == m[r - 1][c]) {
                    run++;
                } else {
                    if (run >= 5) score += run - 2;
                    run = 1;
                }
            }
            if (run >= 5) score += run - 2;
        }
        return score;
    }

    // Rule 2: 2x2 blocks of same color
    private static int penalty2(boolean[][] m, int size) {
        int score = 0;
        for (int r = 0; r < size - 1; r++) {
            for (int c = 0; c < size - 1; c++) {
                boolean v = m[r][c];
                if (v == m[r][c + 1] && v == m[r + 1][c] && v == m[r + 1][c + 1]) {
                    score += 3;
                }
            }
        }
        return score;
    }

    // Rule 3: finder-like patterns (1011101 0000 or 0000 1011101)
    private static int penalty3(boolean[][] m, int size) {
        int score = 0;
        boolean[] p1 = {true,false,true,true,true,false,true,false,false,false,false};
        boolean[] p2 = {false,false,false,false,true,false,true,true,true,false,true};
        for (int r = 0; r < size; r++) {
            for (int c = 0; c <= size - 11; c++) {
                boolean m1 = true, m2 = true;
                for (int k = 0; k < 11; k++) {
                    if (m[r][c + k] != p1[k]) m1 = false;
                    if (m[r][c + k] != p2[k]) m2 = false;
                    if (!m1 && !m2) break;
                }
                if (m1) score += 40;
                if (m2) score += 40;
            }
        }
        for (int c = 0; c < size; c++) {
            for (int r = 0; r <= size - 11; r++) {
                boolean m1 = true, m2 = true;
                for (int k = 0; k < 11; k++) {
                    if (m[r + k][c] != p1[k]) m1 = false;
                    if (m[r + k][c] != p2[k]) m2 = false;
                    if (!m1 && !m2) break;
                }
                if (m1) score += 40;
                if (m2) score += 40;
            }
        }
        return score;
    }

    // Rule 4: proportion of dark modules
    private static int penalty4(boolean[][] m, int size) {
        int dark = 0;
        int total = size * size;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (m[r][c]) dark++;
            }
        }
        int pct = (dark * 100) / total;
        int prev5 = (pct / 5) * 5;
        int next5 = prev5 + 5;
        return Math.min(Math.abs(prev5 - 50) / 5, Math.abs(next5 - 50) / 5) * 10;
    }

    // ----- Bit buffer utility -----

    private static final class BitBuffer {
        private int[] data = new int[256];
        private int bitLen = 0;

        void append(int value, int numBits) {
            ensureCapacity(bitLen + numBits);
            for (int i = numBits - 1; i >= 0; i--) {
                int byteIdx = bitLen / 8;
                int bitIdx = 7 - (bitLen % 8);
                if (((value >> i) & 1) == 1) {
                    data[byteIdx] |= (1 << bitIdx);
                }
                bitLen++;
            }
        }

        int length() { return bitLen; }

        int getByte(int index) { return data[index] & 0xFF; }

        private void ensureCapacity(int bits) {
            int needed = (bits + 7) / 8;
            if (needed > data.length) {
                data = Arrays.copyOf(data, needed * 2);
            }
        }
    }
}
