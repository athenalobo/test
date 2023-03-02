//package com.castsoftware.webi.common.utils;
package bns.testcarl;
import com.google.common.collect.ImmutableList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for finding or guessing the encoding of a file according to its BOM or
 * by analyzing a sample of the characters that are found at the beginning of the file.
 * Note: for the moment, this class only recognize the BOMs of the following encodings:
 * UTF-8, UTF-16 and UTF-32 (whatever the Endianness), and GB-18030.
 */
public class FileEncodingUtils {

    // Minimal quantity of bytes required to be read from a
    // file whose encoding must be found thanks to its BOM.
    // Current value 4 is defined by the bodies of methods
    // getBomLength(...) and getEncodingFromBom(...)
    private static final int BOM_REQUIRED_NUMBER_OF_BYTES = 4;

    // Number of bytes read from a file whose encoding must be guessed; currently 1024 because
    // is enough for up to 256 UTF-32 chars., 512 UTF-16 chars., 256 to 1024 ASCII/UTF-8 chars.
    // With 1024, the performances are same or even better than with 128, 256, 512, and 2048.
    private static final int SAMPLING_READ_NUMBER_OF_BYTES = 1024;

    // List all the Charsets handled by this class and whose BOM will be recognized
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final Charset UTF_16 = StandardCharsets.UTF_16;
    private static final Charset UTF_16LE = StandardCharsets.UTF_16LE;
    private static final Charset UTF_16BE = StandardCharsets.UTF_16BE;
    private static final Charset UTF_32 = Charset.forName("UTF-32");
    private static final Charset UTF_32LE = Charset.forName("UTF-32LE");
    private static final Charset UTF_32BE = Charset.forName("UTF-32BE");
    private static final Charset GB_18030 = Charset.forName("GB18030");

    // List all the Charsets handled by this class for which there
    // is no BOM (or maybe there exist a BOM, but I don't know it)
    private static final Charset ISO_8859_1 = StandardCharsets.ISO_8859_1;

    // Arbitrarily define the threshold density of 0x0 telling if the file is
    // encoded in UTF-16 rather than in UTF-32 as the average density of 0x0
    // of an ASCII string encoded in UTF-16 and UTF-32:
    // -the density of 0x0 inside a pure ASCII string encoded in UTF-16 is 50%
    // -the density of 0x0 inside a pure ASCII string encoded in UTF-32 is 75%
    private static final float THRESHOLD_DENSITY_UTF_16_VS_32 = ((50f + 75f) / 2) / 100;

    private FileEncodingUtils() {
    }

    /**
     * @return the encoding found thanks to recognition of a BOM located at the beginning
     * of the file (provided this BOM is one of those known by this class), or else the
     * encoding guessed by analysis of a sample of the beginning of the file. If neither
     * a BOM was recognized nor the analysis resulted in the guess of the file encoding,
     * this method returns {@code null}.<p>
     * By convention, an empty file (length=0) will be said to be encoded in UTF-8 to avoid
     * pollution of statistics with encoding said to be "unknown" because of empty files.<p>
     * If {@code isEncodingCertain} is non-null, then this output arg. tells whether the
     * returned encoding is certain (thanks to the recognition of a file BOM, or because
     * file sampling allowed to guess the encoding with much confidence).<p>
     * If {@code bomLength} is non-null, then this output arg. gives the number of bytes
     * of the BOM that has been found, if any, or will be set to 0 otherwise.
     */
    public static Charset getOrGuessEncoding(File file, boolean[] isCertain, int[] bomLength) throws IOException {
        // Performance measures have shown that it doesn't worth having a dedicated method
        // that takes a FileInputStream or a RandomAccessFile in argument so that the File
        // resource would be left open after this method will have returned the encoding.
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] file1stBytes = new byte[Math.max(BOM_REQUIRED_NUMBER_OF_BYTES, SAMPLING_READ_NUMBER_OF_BYTES)];
            int bomReadByteCount = fis.read(file1stBytes, 0, BOM_REQUIRED_NUMBER_OF_BYTES);
            if (bomReadByteCount <= 0) {
                // An empty file is, by convention, said to be UTF-8 encoded so that no
                // "unknown" encoding will be reported in stats. because of empty files
                if (isCertain != null) {
                    isCertain[0] = true;
                }
                if (bomLength != null) {
                    bomLength[0] = 0;
                }
                return UTF_8;
            }
            Charset result = getEncodingFromBom(file1stBytes, bomReadByteCount);
            if (result != null) {
                if (isCertain != null) {
                    // BOM found: encoding is certain
                    isCertain[0] = true;
                }
                if (bomLength != null) {
                    bomLength[0] = getBomLength(result);
                }
                return result;
            }
            int moreReadByteCount = fis.read(file1stBytes, bomReadByteCount, SAMPLING_READ_NUMBER_OF_BYTES - bomReadByteCount);
            int sampleLength = moreReadByteCount > 0 ? bomReadByteCount + moreReadByteCount : bomReadByteCount;
            long fileLength = file.length();
            result = getGuessedEncodingFromSample(file1stBytes, sampleLength, fileLength);
            if (isCertain != null) {
                // If an encoding could be guessed, and that encoding is not a single-byte one
                // into which most probably any sequence of bytes can be encoded, and encoding
                // attempt has succeeded for the entire file, then the guess is said certain.
                isCertain[0] = result != null && !result.equals(ISO_8859_1) && sampleLength == fileLength;
            }
            if (bomLength != null) {
                bomLength[0] = 0;
            }
            return result;
        }
    }

    /**
     * @return the encoding found thanks to recognition of a BOM located at the beginning
     * of the file (provided this BOM is one of those known by this class), or else the
     * encoding guessed by analysis of a sample of the beginning of the file. If neither
     * a BOM was recognized nor the analysis resulted in the guess of the file encoding,
     * this method returns {@code null}.
     * By convention, an empty file (length=0) will be said to be UTF-8 encoded to avoid
     * pollution of statistics with encoding said to be "unknown" because of empty files.
     * If {@code isEncodingCertain} is non-null, then this output arg. tells whether the
     * returned encoding is certain (thanks to the recognition of a file BOM, or because
     * file sampling allowed to guess the encoding with much confidence).
     */
    public static Charset getOrGuessEncoding(File file, boolean[] isCertain) throws IOException {
        return getOrGuessEncoding(file, isCertain, null);
    }

    /**
     * @return the detected encoding if one could be determined by a BOM
     * found at the beginning of the file, or {@code null} if there is no
     * BOM, or there is one, but it hasn't been recognized by this method.
     */
    public static Charset getEncodingFromBom(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] fileBomBytes = new byte[BOM_REQUIRED_NUMBER_OF_BYTES];
            int bomReadByteCount = fis.read(fileBomBytes, 0, fileBomBytes.length);
            return getEncodingFromBom(fileBomBytes, bomReadByteCount);
        }
    }

    /**
     * @return the detected encoding if one could be determined by a BOM
     * found at the beginning of the file, or {@code null} if there is no
     * BOM, or there is one, but it hasn't been recognized by this method.
     */
    public static Charset getEncodingFromBom(byte[] bomBytes, int arrayLength) {
        byte NON_EXISTENT_BYTE = (byte) 0xAA; // any value different from all bytes that can appear in any BOM
        byte byte1 = arrayLength > 0 ? bomBytes[0] : NON_EXISTENT_BYTE;
        byte byte2 = arrayLength > 1 ? bomBytes[1] : NON_EXISTENT_BYTE;
        byte byte3 = arrayLength > 2 ? bomBytes[2] : NON_EXISTENT_BYTE;

        // Update BOM_REQUIRED_NUMBER_OF_BYTES and getBomLength()
        // accordingly if the below code recognizes new BOMs
        if (byte1 == (byte) 0xEF && byte2 == (byte) 0xBB && byte3 == (byte) 0xBF) {
            return UTF_8;
        } else {
            // Read the 4th byte now because we must check for
            // UTF-32 before UTF-16 since their BOMs overlap.
            byte byte4 = arrayLength > 3 ? bomBytes[3] : NON_EXISTENT_BYTE;
            if (byte1 == (byte) 0x00 && byte2 == (byte) 0x00 && byte3 == (byte) 0xFE && byte4 == (byte) 0xFF) {
                return UTF_32BE;
            } else if (byte1 == (byte) 0xFF && byte2 == (byte) 0xFE && byte3 == (byte) 0x00 && byte4 == (byte) 0x00) {
                return UTF_32LE;
            } else if (byte1 == (byte) 0xFF && byte2 == (byte) 0xFE) {
                return UTF_16LE;
            } else if (byte1 == (byte) 0xFE && byte2 == (byte) 0xFF) {
                return UTF_16BE;
            } else if (byte1 == (byte) 0x84 && byte2 == (byte) 0x31 && byte3 == (byte) 0x95 && byte4 == (byte) 0x33) {
                return GB_18030;
            }
        }
        return null;
    }

    /**
     * @return the length >= 0, in bytes, of the BOM that would allow the
     * recognition of the encoding of a file having that BOM, or {@code -1} if
     * the provided {@code charset} is not one of those known by this class.
     */
    private static int getBomLength(Charset charset) {
        if (ISO_8859_1.equals(charset)) {
            return 0; // No single-byte encodings has a BOM
        } else if (UTF_16LE.equals(charset) || UTF_16BE.equals(charset) || UTF_16.equals(charset)) {
            return 2; // { 0xFF, 0xFE } in any order
        } else if (UTF_8.equals(charset)) {
            return 3; // [ 0xEF, 0xBB, 0xBF ]
        } else if (UTF_32BE.equals(charset) || UTF_32LE.equals(charset) || UTF_32.equals(charset)) {
            return 4; // = BOM_REQUIRED_NUMBER_OF_BYTES for [ 0x0, 0x0, 0xFE, 0xFF ] or [ 0xFF, 0xFE, 0x0, 0x0 ]
        } else if (GB_18030.equals(charset)) {
            return 4; // = BOM_REQUIRED_NUMBER_OF_BYTES for [ 0x84, 0x31, 0x95, 0x33 ]
        }
        return -1;
    }

    /**
     * @return the said "guessed" encoding if one could be determined by analysis
     * of the beginning of the file, or {@code null} if file encoding is unknown.
     */
    public static Charset getGuessedEncodingFromSample(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] sampleBytes = new byte[SAMPLING_READ_NUMBER_OF_BYTES];
            int sampleLength = fis.read(sampleBytes, 0, sampleBytes.length);
            // This method designed for unit-tests: if the file starts with a BOM, it is ignored,
            // skipped, and encoding is attempted to be guessed only using the bytes which follow.
            Charset fileEncodingFoundInBom = getEncodingFromBom(sampleBytes, sampleLength);
            int nbOfBomBytesToSkip = Math.max(0, getBomLength(fileEncodingFoundInBom));
            if (nbOfBomBytesToSkip != 0) {
                sampleLength -= nbOfBomBytesToSkip;
                System.arraycopy(sampleBytes, nbOfBomBytesToSkip, sampleBytes, 0, sampleLength);
            }
            return getGuessedEncodingFromSample(sampleBytes, sampleLength, file.length());
        }
    }

    /**
     * @return the said "guessed" encoding if one could be determined by analysis
     * of the beginning of the file, or {@code null} if file encoding is unknown.
     */
    private static Charset getGuessedEncodingFromSample(byte[] sampleBytes, int sampleLength, long fileLength) {
        if (sampleLength <= 0) {
            return null;
        }

        int nbOf0x0 = 0;
        for (int i = 0; i != sampleLength; ++i) {
            if (sampleBytes[i] == 0) {
                ++nbOf0x0;
            }
        }

        // Avoids re-allocations if several encodings are checked to be the right one...
        ByteBuffer wrappedSampleWith0x0 = ByteBuffer.wrap(sampleBytes, 0, sampleLength);
        CharBuffer decodingBuffer = CharBuffer.wrap(new char[sampleLength * 2 /* x2 is by safety, Ok because samples are small */]);
        boolean sampleMakesTheWholeFile = sampleLength == fileLength;

        if (nbOf0x0 != 0) {
            float densityOf0x0 = (float) nbOf0x0 / (float) sampleLength;
            if ((fileLength & 0x3L) == 0x0L && densityOf0x0 >= THRESHOLD_DENSITY_UTF_16_VS_32) {
                // File-size % 4 = 0 and sampling gives a density of 0x0 >= threshold:
                // -most probably UTF-32, and the position of the 0x0 gives the Endianness
                // -if density is exactly 75%, most probably UTF-32 containing only ASCII
                //  as for example "xyz" encoded as "x000y000z000" or "000x000y000z"
                int[] nbOfZerosPerByteIndex = {0, 0, 0, 0};
                for (int i = 0; i != sampleLength; ++i) {
                    if (sampleBytes[i] == 0) {
                        ++(nbOfZerosPerByteIndex[i & 3]);
                    }
                }
                // Endianness is initially guessed thanks to the output of the following code:
                // "A".getBytes(UTF_32LE) -> [65, 0, 0,  0] BMP characters are at index 0 and 1
                // "A".getBytes(UTF_32BE) -> [ 0, 0, 0, 65] BMP characters are at index 2 and 3
                int nbOfZerosForBytes01 = nbOfZerosPerByteIndex[0] + nbOfZerosPerByteIndex[1];
                int nbOfZerosForBytes23 = nbOfZerosPerByteIndex[2] + nbOfZerosPerByteIndex[3];
                Charset guessedEncoding = nbOfZerosForBytes23 > nbOfZerosForBytes01 ? UTF_32LE : UTF_32BE;
                if (isEncodingSuccessful(guessedEncoding, wrappedSampleWith0x0, decodingBuffer, sampleMakesTheWholeFile)) {
                    return guessedEncoding;
                }
                guessedEncoding = guessedEncoding.equals(UTF_32LE) ? UTF_32BE : UTF_32LE;
                if (isEncodingSuccessful(guessedEncoding, wrappedSampleWith0x0, decodingBuffer, sampleMakesTheWholeFile)) {
                    return guessedEncoding;
                }
                // Not UTF-32: continuation of the execution flow
                // will try with UTF-16 if file length is even...
            }
            if ((fileLength & 0x1L) == 0x0L) {
                // File-size % 2 = 0, or else file-size % 4 = 0 but sampling gives
                // a density of 0x0 <= threshold:
                // -most probably UTF-16, and the position of the 0x0 gives the Endianness
                // -if density is exactly 50%, most probably UTF-16 containing only ASCII
                //  as for example "abcde" encoded as "a0b0c0d0e0" or "0a0b0c0d0e"
                int[] nbOfZerosPerByteIndex = {0, 0};
                for (int i = 0; i != sampleLength; ++i) {
                    if (sampleBytes[i] == 0) {
                        ++(nbOfZerosPerByteIndex[i & 1]);
                    }
                }
                // Endianness is initially guessed thanks to the output of the following code:
                // "A".getBytes(UTF_16LE) -> [65, 0] ASCII characters are at index 0
                // "A".getBytes(UTF_16BE) -> [0, 65] ASCII characters are at index 1
                Charset guessedEncoding = nbOfZerosPerByteIndex[1] > nbOfZerosPerByteIndex[0] ? UTF_16LE : UTF_16BE;
                if (isEncodingSuccessful(guessedEncoding, wrappedSampleWith0x0, decodingBuffer, sampleMakesTheWholeFile)) {
                    return guessedEncoding;
                }
                guessedEncoding = guessedEncoding.equals(UTF_16LE) ? UTF_16BE : UTF_16LE;
                if (isEncodingSuccessful(guessedEncoding, wrappedSampleWith0x0, decodingBuffer, sampleMakesTheWholeFile)) {
                    return guessedEncoding;
                }
                // Not UTF-16 (and maybe neither UTF-32 if this encoding was tried before also)
                // Continuation of the execution flow will try with other encodings, in case the
                // file contains some embedded 0x0 as it has been seen for some Mainframe files.
            }
        }

        // As it happened to meet 0x0 in some Mainframe files, we replace them by
        // blanks so that they won't disturb UTF-8 or ISO-xxx encoding detection.
        // Since 0x0 and 0x20 are both ASCII, this transformation won't corrupt
        // the sample if it is encoded in UTF-8 or ISO-8859-1.
        ByteBuffer wrappedSampleWithout0x0;
        if (nbOf0x0 != 0) {
            byte[] sampleWithout0x0 = new byte[sampleLength]; // no call to clone() because sampleLength can be < sampleBytes.length()
            for (int i = 0; i != sampleLength; ++i) {
                sampleWithout0x0[i] = sampleBytes[i] == 0x0 ? (byte) 0x20 : sampleBytes[i];
            }
            wrappedSampleWithout0x0 = ByteBuffer.wrap(sampleWithout0x0, 0, sampleLength);
        } else {
            // wrappedSampleWith0x0 already does not contain any 0x0
            wrappedSampleWithout0x0 = wrappedSampleWith0x0;
        }

        // Check for various multi-bytes encodings, then single-byte encodings.
        // Even though quite probably any sequence of bytes can be encoded into
        // ISO_8859_1 because it's a single-byte encoding, we have a real try by
        // safety. Also, since ISO_8859_1 is one of the few encodings defined in
        // StandardCharsets, there are chances that this encoding is correct...
        for (Charset triedEncoding : ImmutableList.of(UTF_8, GB_18030, ISO_8859_1)) {
            // Detection of GB-18030 is done using the wrapped buffer having its
            // genuine 0x0, and is attempted only if the sample contains at least
            // a 0x0 because otherwise this encoding is confused with ISO_8859_1.
            if (triedEncoding.equals(GB_18030) && nbOf0x0 == 0) {
                continue;
            }
            ByteBuffer wrapped = triedEncoding.equals(GB_18030) ? wrappedSampleWith0x0 : wrappedSampleWithout0x0;
            if (isEncodingSuccessful(triedEncoding, wrapped, decodingBuffer, sampleMakesTheWholeFile)) {
                return triedEncoding;
            }
        }

        // Encoding may be any single-byte encoding like ISO-8859-N, or a multi-bytes one.
        // A smart detection could consist in trying to encode the sample in every of the
        // multi-bytes encodings that can be enumerated by the Charset class until one
        // succeeds, then in every of the single-byte encodings, but doing so is out of
        // the scope of what this class is currently designed for (i.e. line counting).
        return null;
    }

    /**
     * @return whether the bytes in {@code inputBytes} can be encoded into a certain encoding.<p>
     * Note: if {@code triedEncoding} is a multi-bytes encoding, if the last character of the sample
     * has its sequence of bytes that is cut because of the buffer boundaries, then this method will
     * however return {@code true} because supplied {@code sampleMakesTheWholeFile} is {@code false}.
     */
    private static boolean isEncodingSuccessful(Charset triedEncoding, ByteBuffer inputBytes, CharBuffer reusableSink, boolean sampleMakesTheWholeFile) {
        inputBytes.clear();   // decoding will restart from the 1st byte; clear() does not actually erase the data in the buffer
        reusableSink.clear(); // necessary for being ready to receive some output issued from a new decoding attempt
        CharsetDecoder decoder = triedEncoding.newDecoder().onUnmappableCharacter(CodingErrorAction.REPORT);
        CoderResult cr = decoder.decode(inputBytes, reusableSink, sampleMakesTheWholeFile);
        return !cr.isError(); // means "no malformed or unmappable characters were encountered"
    }
}
