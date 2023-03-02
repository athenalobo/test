//package com.castsoftware.webi.common.utils;
package bns.testcarl;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for counting the number of lines in a collection of {@link File}.
 * In order to provide the best accuracy, the encoding of each file will be attempted
 * to be retrieved by reading its BOM, or if no BOM is found by analyzing a sample of
 * the beginning of the file; when the encoding of a file could neither be retrieved
 * nor guessed, it will be assumed to be the default charset of this JVM.
 */
@Slf4j
public class LineCountUtils {

    @Getter
    private static final int AVAILABLE_NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();

    // Used for encodings that could neither be determined thanks to file BOM nor guessed thanks to sampling
    private static final String NAME_FOR_UNKNOWN_ENCODINGS = "*UNKNOWN*";

    public enum LineCountingMethod {
        // Provides an exact count of lines provided the file encoding could be found
        // thanks to a BOM or guessed in a way that CR/LF can be detected accurately.
        // Also, the code run to count lines is as fast as the genuine and the custom
        // versions of readLine(), but stable in terms of memory (re-) allocations
        // contrary to BufferedReader.readLine() and CustomBufferedReader.readLine()
        // whose use resulted in an OutOfMemoryException after around 2 hours of work
        // for counting lines in dozens of GB of files.
        CHAR_OR_BYTE_LOOKUP,

        // Whatever the method, the count of lines will be approximate inherently
        // to the behavior or lack of accuracy of the methods listed below, even
        // if the encoding of the file would be perfectly guessed. These 3 items
        // remain available for future non-regression tests if needed one day...
        GENUINE_BUFFERED_READER_READLINE,    // BufferedReader.readLine()
        CUSTOM_BUFFERED_READER_READLINE,     // CustomBufferedReader.readLine()
        CUSTOM_BUFFERED_READER_HAS_NEXT_LINE // CustomBufferedReader.hasNextLine()
    }

    @Setter
    private LineCountingMethod lineCountingMethod = LineCountingMethod.CHAR_OR_BYTE_LOOKUP;

    // Q.v. field BufferedReader.defaultCharBufferSize: at most 8K as it is the buffer size used
    // internally by the JVM; if bigger, reading a file will allocate a buffer at each read call!
    @Setter
    private int fileReadBufferSize = 4096; // 4K is faster than 2K and 8K

    // Whether the count of threads used in parallel to count lines has to be remembered
    @Setter
    private boolean countWorkingThreads = false;

    // Whether the count of files per encoding has to be remembered,
    // along with whether each recognition was certain or else guessed
    @Setter
    private boolean buildEncodingStatistics = false;

    // Tells that information about each file that has been processed must be logged
    @Setter
    private boolean logPerFileInfo = false;

    // Line counting progress display frequency > 0 (for e.g. 10000) / null for "mute" mode
    @Setter
    private Integer countingProgressLogFrequency = null;

    private int nbOfFilesToProcess;         // Number of files whose lines have to be counted
    private AtomicLong overallLineCount;    // Sum of lines for all files whose lines could successfully be counted
    private long stopCountThreshold;        // Line counting halts as soon as overallLineCount >= stopCountThreshold
    @Getter
    private AtomicLong nbProcessedBytes;    // Sum of the length of all files whose lines could successfully be counted
    private AtomicInteger nbFilesInSuccess; // Number of files whose lines could successfully be counted
    private AtomicInteger nbFilesInError;   // Number of files for which count of lines has resulted into an exception
    private Set<Long> workingThreadIdsSet;  // null or set holding IDs of all the threads involved in line counting

    // Bind the name of each encoding met to the counts of files having this encoding:
    // -index 0 is for encodings which are certain thanks to the recognition of a BOM,
    //  or by convention said to be certain because the length of the file is 0 bytes,
    // -index 1 is for encoding guessed with much confidence thanks to file sampling,
    // -index 2 is for unknown encodings and those hoped to have been guessed right...
    private Map<String, AtomicInteger>[] encodingNameToFileCountMaps;

    /**
     * @return the overall number of lines that have been counted in all
     * files contained in the provided collection of {@link File}, or that
     * have been counted before the count stopped because the threshold
     * supplied in {@code lineCountThreshold}, when not {@code null}, has
     * been reached.
     * <p>WARNING: The current implementation only counts CR and/or LF,
     * not U+0085 (aka NEXT LINE, aka NEL) and neither U+2028 nor U+2029
     * (respectively aka LINE SEPARATOR and aka PARAGRAPH SEPARATOR).
     */
    public long countCodeLines(Collection<File> files, Long lineCountThreshold) throws IOException {
        nbOfFilesToProcess = files.size();
        overallLineCount = new AtomicLong();
        stopCountThreshold = lineCountThreshold != null ? lineCountThreshold : Long.MAX_VALUE;
        nbProcessedBytes = new AtomicLong();
        nbFilesInSuccess = new AtomicInteger();
        nbFilesInError = new AtomicInteger();
        workingThreadIdsSet = countWorkingThreads ? ConcurrentHashMap.newKeySet(AVAILABLE_NUMBER_OF_CORES) : null;
        if (buildEncodingStatistics) {
            encodingNameToFileCountMaps = (ConcurrentHashMap[]) Array.newInstance(ConcurrentHashMap.class, 3);
            for (int i = 0; i != encodingNameToFileCountMaps.length; ++i) {
                encodingNameToFileCountMaps[i] = new ConcurrentHashMap<>(7);
            }
        }

        files.parallelStream().filter(f -> Files.exists(f.toPath())).forEach(file -> {
            if (overallLineCount.get() < this.stopCountThreshold) {
                // This MUST remain the 1st line of this block so that decreasing this value
                // in case of exception is valid: we get the increased file# now so that no
                // trace, if any, will show the same file# because of multi-threading.
                int fileNumber = nbFilesInSuccess.incrementAndGet();
                try {
                    if (workingThreadIdsSet != null) {
                        workingThreadIdsSet.add(Thread.currentThread().getId());
                    }
                    if (countingProgressLogFrequency != null) {
                        logLineCountingProgress(nbFilesInSuccess.get() + nbFilesInError.get());
                    }

                    boolean[] isEncodingCertain = new boolean[1];
                    int[] bomLength = new int[1];
                    Charset fileEncoding = getFileEncoding(file, isEncodingCertain, bomLength);
                    if (fileEncoding == null) {
                        fileEncoding = Charset.defaultCharset(); // default charset of this JVM
                        isEncodingCertain[0] = false;
                        bomLength[0] = 0;
                    }

                    long lineCount = lineCountingMethod != LineCountingMethod.CHAR_OR_BYTE_LOOKUP
                            // count lines using BufferedReader or CustomBufferedReader
                            ? countLinesWithBufferedReader(file, bomLength[0], fileEncoding)
                            : canSearchLineBreakAsByte(fileEncoding)
                            // single-byte encoding or UTF-8 => CR/LF can unambiguously be searched in a byte[]
                            ? countLinesWithByteLookup(file, fileReadBufferSize, bomLength[0])
                            // multi-bytes encoding => CR/LF must be searched in a char[] rebuilt from the file bytes
                            : countLinesWithCharLookup(file, fileReadBufferSize, bomLength[0], fileEncoding);

                    if (logPerFileInfo) {
                        logPerFileInformation(file, fileNumber, fileEncoding, isEncodingCertain[0], bomLength[0], lineCount);
                    }
                    overallLineCount.addAndGet(lineCount);
                    nbProcessedBytes.addAndGet(file.length());
                } catch (Exception e) {
                    nbFilesInSuccess.decrementAndGet();
                    nbFilesInError.incrementAndGet();
                    log.error(String.format("Line counting failed for file \"%s\" (#failures = %d, #success = %d).", file.getAbsolutePath(), nbFilesInError.get(), nbFilesInSuccess.get()), e);
                }
            }
        });
        return overallLineCount.get();
    }

    /**
     * Can be called for logging how many files have been processed so far, how many
     * files remains to be processed, how many lines have been counted overall, etc.
     */
    private void logLineCountingProgress(int nbOfProcessedFiles) {
        if (nbOfProcessedFiles % countingProgressLogFrequency == 0) {
            String nbOfThreadsInfo = workingThreadIdsSet != null ? String.format(", using %d threads", workingThreadIdsSet.size()) : "";
            log.info("Counted {} kLines in {} / {} files so far (#failures = {}){}...", (overallLineCount.get() / 1000), nbOfProcessedFiles, nbOfFilesToProcess, nbFilesInError.get(), nbOfThreadsInfo);
        }
    }

    /**
     * Can be called for logging which thread has processed which file, what encoding
     * has been found or guessed for it, how many lines have been counted in it, etc.
     */
    private void logPerFileInformation(File file, int fileNumber, Charset fileEncoding, boolean isEncodingCertain, int bomLength, long lineCount) {
        int encodingCountIndex = getEncodingCountIndex(file, isEncodingCertain, bomLength);
        String encodingCertainty = encodingCountIndex == 0 ? " (certain)" : encodingCountIndex == 1 ? " (guessed)" : " (doubtful/unknown)";
        String encodingName = fileEncoding != null ? fileEncoding.name() : NAME_FOR_UNKNOWN_ENCODINGS;
        String encodingUseCount = encodingNameToFileCountMaps != null
                ? String.format(" (#use = %d)", encodingNameToFileCountMaps[encodingCountIndex].get(encodingName).get())
                : "";
        String bomLengthInBytes = bomLength != 0 ? String.format(" - BOM length = %d bytes", bomLength) : "";
        log.info(String.format("thread 0x%02x: counted %d lines in file #%06d \"%s\" with encoding %s%s%s%s",
                (int) (Thread.currentThread().getId() % 0xFF),
                lineCount, fileNumber, file.getName(),
                encodingName, encodingCertainty, encodingUseCount, bomLengthInBytes));
    }

    /**
     * @return the index in the array held by a value of {@link LineCountUtils#encodingNameToFileCountMaps}:
     */
    private static int getEncodingCountIndex(File file, boolean isEncodingCertain, int bomLength) {
        // -index 0 is for encodings which are certain thanks to the recognition of a BOM,
        //  or by convention said to be certain because the length of the file is 0 bytes,
        // -index 1 is for encoding guessed with much confidence thanks to file sampling,
        // -index 2 is for unknown encodings and those hoped to have been guessed right...
        if (bomLength != 0 || (isEncodingCertain && file.length() == 0L)) {
            return 0;
        } else if (isEncodingCertain) {
            return 1;
        } else {
            return 2;
        }
    }

    /**
     * @return whether CR/LF can be searched as byte(s) in
     * a file whose encoding is the given {@code encoding}.
     */
    public static boolean canSearchLineBreakAsByte(Charset charset) {
        return charset.equals(StandardCharsets.UTF_8) || charset.equals(StandardCharsets.ISO_8859_1);
    }

    /**
     * @return the encoding of the provided {@code file}, or {@code null} if encoding is unknown.
     * If {@code isEncodingCertain} is non-null, then this output arg. tells whether the encoding
     * is certain (thanks to the recognition of a file BOM, or because file sampling allowed to
     * guess the encoding with much confidence).
     * If {@code bomLength} is non-null, then this output arg. gives the number ( >= 1 ) of bytes
     * of the BOM that has been found, if any, or will be set to 0 otherwise.
     */
    private Charset getFileEncoding(File file, boolean[] isEncodingCertain, int[] bomLength) throws IOException {
        Charset fileEncoding = FileEncodingUtils.getOrGuessEncoding(file, isEncodingCertain, bomLength);
        if (encodingNameToFileCountMaps != null) {
            String encodingName = fileEncoding != null ? fileEncoding.name() : NAME_FOR_UNKNOWN_ENCODINGS;
            int encodingCountIndex = getEncodingCountIndex(file, isEncodingCertain[0], bomLength[0]);
            AtomicInteger fileEncodingCount = encodingNameToFileCountMaps[encodingCountIndex].get(encodingName);
            if (fileEncodingCount == null) {
                synchronized (encodingNameToFileCountMaps) {
                    fileEncodingCount = encodingNameToFileCountMaps[encodingCountIndex].get(encodingName);
                    if (fileEncodingCount == null) {
                        fileEncodingCount = new AtomicInteger();
                        encodingNameToFileCountMaps[encodingCountIndex].put(encodingName, fileEncodingCount);
                    }
                }
            }
            fileEncodingCount.incrementAndGet();
        }
        return fileEncoding;
    }

    /**
     * @return the approximate count of lines found in the file, that count being
     * approximate inherently to the behavior or lack of accuracy of all methods
     * used, even if the supplied encoding is the right one for the file.
     */
    public long countLinesWithBufferedReader(File file, int bomLength, Charset fileEncoding) throws IOException {
        // A file whose length is 0 or consisting only of a BOM is said to contain no lines
        if (file.length() <= bomLength) {
            return 0;
        }

        // Initialized according to the used class because BufferedReader.readLine() returns null for
        // the last line if the file ends with CR/LF, whereas CustomBufferedReader.readLine() doesn't.
        // So for each method, the delta between the exact count of lines and the value got when this
        // variable is initialized with 0 or 1 has been computed over a sampling of ~ 10 GB of source
        // code, and then the retained initialization value was chosen to have the smallest delta.
        long linesCount;

        try (InputStreamReader isr = new InputStreamReader(new FileInputStream(file), fileEncoding)) {
            switch (lineCountingMethod) {
                case GENUINE_BUFFERED_READER_READLINE:
                    linesCount = 1; // 0 -> 166976237 so delta/exact = 112004 vs. 1 -> 167091710 so delta/exact = -3469
                    try (BufferedReader br = new BufferedReader(isr)) {
                        while (br.readLine() != null) {
                            ++linesCount;
                        }
                    }
                    return linesCount;
                case CUSTOM_BUFFERED_READER_READLINE:
                    linesCount = 1; // 0 -> 166992093 so delta/exact = 96148 vs. 1 -> 167107566 so delta/exact = -19325
                    try (CustomBufferedReader br = new CustomBufferedReader(isr)) {
                        while (br.readLine() != null) {
                            ++linesCount;
                        }
                    }
                    return linesCount;
                case CUSTOM_BUFFERED_READER_HAS_NEXT_LINE:
                    linesCount = 1; // 0 -> 166992093 so delta/exact = 96148 vs. 1 -> 167107566 so delta/exact = -19325
                    try (CustomBufferedReader br = new CustomBufferedReader(isr)) {
                        while (br.hasNextLine()) {
                            ++linesCount;
                        }
                    }
                    return linesCount;
                default:
                    throw new IllegalArgumentException("Invalid line counting method enum value");
            }
        }
    }

    /**
     * @return the exact count of lines found in the file, that count including
     * the last empty line of the file if the file is CR/LF-ended.<p>
     * To be called preferably for multi-bytes encodings like UTF-16, UTF-32,
     * GB-18030, even though it would return a correct count of lines for
     * ISO-8859-1 and UTF-8 though with slightly slower performances.
     */
    public static long countLinesWithCharLookup(File file, int fileReadBufferSize, int bomLength, Charset fileEncoding) throws IOException {
        // A file whose length is 0 or consisting only of a BOM is said to contain no lines
        long fileLength = file.length();
        if (fileLength <= bomLength) {
            return 0;
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), fileEncoding))) {
            boolean previousBufferEndedWithCR = false;
            char[] buffer = new char[Math.min((int) (fileLength & Integer.MAX_VALUE), fileReadBufferSize)];
            for (long linesCount = 1; ; ) {
                // Since here one reads characters, it is impossible to have the sequence
                // of bytes of the last character being cut because of buffer boundaries.
                int nbReadChars = br.read(buffer, 0, buffer.length);
                if (nbReadChars <= 0) {
                    return linesCount;
                }

                int i = 0;
                if (previousBufferEndedWithCR && buffer[0] == '\n') {
                    // The counting done so far already did a "++linesCount", and
                    // is about to do another: we skip the 1st '\n' to avoid it.
                    i = 1;
                } else if (bomLength != 0) {
                    i = 1; // whatever the encoding, the BOM corresponds to 1 'char'
                    bomLength = 0;
                }
                while (i != nbReadChars) {
                    char c = buffer[i++];
                    if (c == '\r' || c == '\n') {
                        ++linesCount;
                        if (c == '\r' && i != nbReadChars && buffer[i] == '\n') {
                            ++i;
                        }
                    }
                }
                previousBufferEndedWithCR = buffer[nbReadChars - 1] == '\r';
            }
        }
    }

    /**
     * @return the exact count of lines found in the file, that count including
     * the last empty line of the file if the file is CR/LF-ended.<p>
     * To be called preferably for multi-bytes encodings like UTF-16, UTF-32,
     * GB-18030, even though it would return a correct count of lines for
     * ISO-8859-1 and UTF-8 though with slightly slower performances.<p>
     * WARNING: This method is not able to cope with files bigger than 2 GB,
     * and loads the whole file in RAM => very risky for big files but worth
     * to be tried for measuring the performances using memory-mapped files,
     * finally found not better than those of countLinesWithCharLookup(...).
     */
    public static long countLinesWithEntireFileLoadedInMemory(File file, int fileReadBufferSize, int bomLength, Charset fileEncoding) throws IOException {
        long fileLength = file.length();
        if (fileLength <= bomLength) {
            // A file whose length is 0 or consisting only of a BOM is said to contain no lines
            return 0;
        } else if ((fileLength - bomLength) > Integer.MAX_VALUE) {
            // FileChannel.map() disallows mapping of a region whose size is > 2 GB
            return countLinesWithCharLookup(file, fileReadBufferSize, bomLength, fileEncoding);
        }

        // Performances are the same with "RandomAccessFile raf = new RandomAccessFile(file, 'r')"
        try (FileChannel channel = new FileInputStream(file).getChannel()) {
            // Math.min() avoids, for some files only, I/O exception "Channel
            // not open for writing - cannot extend file to required size".
            long mappedRegionSize = Math.min((fileLength - bomLength), channel.size());
            MappedByteBuffer mappedByteBuffer = channel.map(FileChannel.MapMode.READ_ONLY, bomLength, mappedRegionSize);
            long linesCount = 1;
            char[] buffer = fileEncoding.decode(mappedByteBuffer).array();
            for (int i = 0; i != buffer.length; /* NOP */) {
                char c = buffer[i++];
                if (c == '\r') {
                    ++linesCount;
                    if (i != buffer.length && buffer[i] == '\n') {
                        ++i;
                    }
                } else if (c == '\n') {
                    ++linesCount;
                }
            }
            return linesCount;
        }
    }

    /**
     * @return the exact count of lines found in the file, that count including
     * the last empty line of the file if the file is CR/LF-ended.<p>
     * To be called *only* for encodings where CR/LF can be found unambiguously
     * when searched as "byte", whatever the encoding is single/multi-bytes,
     * typically ISO-8859-N and UTF-8.
     */
    public static long countLinesWithByteLookup(File file, int fileReadBufferSize, int bomLength) throws IOException {
        // A file whose length is 0 or consisting only of a BOM is said to contain no lines
        long fileLength = file.length();
        if (fileLength <= bomLength) {
            return 0;
        }

        // Performances are the same with "RandomAccessFile raf = new RandomAccessFile(file, 'r')"
        try (FileInputStream fis = new FileInputStream(file)) {
            boolean previousBufferEndedWithCR = false;
            byte[] buffer = new byte[Math.min((int) (fileLength & Integer.MAX_VALUE), fileReadBufferSize)];
            for (long linesCount = 1; ; ) {
                // If the file has a multi-bytes encoding (like UTF-8 for e.g.), having
                // the bytes sequence of a character cut because of buffer boundaries
                // is not a problem since this method deals with bytes, not characters.
                int nbReadBytes = fis.read(buffer, 0, buffer.length);
                if (nbReadBytes <= 0) {
                    return linesCount;
                }

                int i = 0;
                if (previousBufferEndedWithCR && buffer[0] == '\n') {
                    // The counting done so far already did a "++linesCount", and
                    // is about to do another: we skip the 1st '\n' to avoid it.
                    i = 1;
                } else if (bomLength != 0) {
                    i = bomLength;
                    bomLength = 0;
                }
                while (i != nbReadBytes) {
                    byte b = buffer[i++];
                    if (b == '\r' || b == '\n') {
                        ++linesCount;
                        if (b == '\r' && i != nbReadBytes && buffer[i] == '\n') {
                            ++i;
                        }
                    }
                }
                previousBufferEndedWithCR = buffer[nbReadBytes - 1] == '\r';
            }
        }
    }

    /**
     * @return the exact count of lines found in the file, that count including
     * the last empty line of the file if the file is CR/LF-ended.<p>
     * To be called *only* for single/multi-bytes encodings where CR/LF can be found
     * unambiguously when searched as "byte", typically ISO-8859-N and UTF-8.<p>
     * This method is designed to measure the performances using FileChannel,
     * which were found not better than those of countLinesWithByteLookup(...).
     */
    public static long countLinesWithByteLookupUsingFileChannel(File file, int fileReadBufferSize, int bomLength) throws IOException {
        // A file whose length is 0 or consisting only of a BOM is said to contain no lines
        long fileLength = file.length();
        if (fileLength <= bomLength) {
            return 0;
        }

        // Performances are the same with "FileInputStream fis = new FileInputStream(file)"
        try (FileChannel channel = new RandomAccessFile(file, "r").getChannel()) {
            boolean previousBufferEndedWithCR = false;
            byte[] buffer = new byte[Math.min((int) (fileLength & Integer.MAX_VALUE), fileReadBufferSize)];
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            for (long linesCount = 1; /* NOP */ ; byteBuffer.clear()) {
                // If the file has a multi-bytes encoding (like UTF-8 for e.g.), having
                // the bytes sequence of a character cut because of buffer boundaries
                // is not a problem since this method deals with bytes, not characters.
                int nbReadBytes = channel.read(byteBuffer);
                if (nbReadBytes <= 0) {
                    return linesCount;
                }

                int i = 0;
                if (previousBufferEndedWithCR) {
                    if (buffer[0] == '\n') {
                        i = 1;
                    }
                    previousBufferEndedWithCR = false;
                } else if (bomLength != 0) {
                    i = bomLength;
                    bomLength = 0;
                }

                while (i != nbReadBytes) {
                    byte b = buffer[i++];
                    if (b == '\r') {
                        ++linesCount;
                        if (i == nbReadBytes) {
                            previousBufferEndedWithCR = true;
                        } else if (buffer[i] == '\n') {
                            ++i;
                        }
                    } else if (b == '\n') {
                        ++linesCount;
                    }
                }
            }
        }
    }

    public void logReport() {
        String exactness = lineCountingMethod == LineCountingMethod.CHAR_OR_BYTE_LOOKUP ? "exact" : "approx.";
        log.info("Number of lines in the entire File collection  = {} kLines over {} files ({} count = {})", overallLineCount.get() / 1000, nbFilesInSuccess.get(), exactness, overallLineCount.get());
        String nbOfThreadsInfo = workingThreadIdsSet != null ? String.format(" (using %d threads)", workingThreadIdsSet.size()) : "";
        log.info("Number of files whose lines have been counted  = {}{}", nbFilesInSuccess.get(), nbOfThreadsInfo);
        String warningForFilesInError = nbFilesInError.get() != 0 ? " *** WARNING ***" : "";
        log.info("Number of files whose line counting failed     = {}{}", nbFilesInError.get(), warningForFilesInError);
        String nbOfGB = String.format("%.2f", (nbProcessedBytes.get() >> 20) / 1024f);
        log.info("Number of bytes processed during line counting = {} = ~{} KB = ~{} MB = ~{} GB", nbProcessedBytes.get(), (nbProcessedBytes.get() >> 10), (nbProcessedBytes.get() >> 20), nbOfGB);
        if (encodingNameToFileCountMaps != null) {
            // Build the map containing the aggregation of the counts per encoding.
            // Values' type is AtomicInteger for compatibility with the maps held
            // by encodingNameToFileCountMaps[] so that all can be processed just
            // below using a single stream.
            Map<String, AtomicInteger> encodingNameToFileCountAgg = new HashMap<>();
            for (Map<String, AtomicInteger> map : encodingNameToFileCountMaps) {
                map.forEach((encodingName, fileCount) -> {
                    AtomicInteger fileCountAgg = encodingNameToFileCountAgg.getOrDefault(encodingName, new AtomicInteger(0));
                    fileCountAgg.addAndGet(fileCount.get());
                    encodingNameToFileCountAgg.put(encodingName, fileCountAgg);
                });
            }

            // Build array consisting in the elements of encodingNameToFileCountMaps[]
            // to which is appended, as last element, the aggregation built just above.
            Stream<Map<String, AtomicInteger>> concatenatedStreams = Stream.concat(Arrays.stream(encodingNameToFileCountMaps), ImmutableList.of(encodingNameToFileCountAgg).stream());
            String[] encodingStats = concatenatedStreams
                    .map(map -> map.entrySet().stream()
                            .map(e -> String.format("%s=%d", e.getKey(), e.getValue().get()))
                            .sorted().collect(Collectors.joining(", ")))
                    .toArray(String[]::new);
            log.info("Number of files per encoding provided by BOM   = {}", encodingStats[0]);
            log.info("Number of files per encoding that was guessed  = {}", encodingStats[1]);
            log.info("Number of files per encoding that is doubtful  = {}", encodingStats[2]);
            log.info("Number of files per encoding (sum of above)    = {}", encodingStats[3]);
        }
    }

    /**
     * Copy/paste of BufferedReader, except {@link CustomBufferedReader#readLine()}
     * which has been modified to account the last line of a CR/LF-ended file, plus
     * the new method {@link CustomBufferedReader#hasNextLine()} which reads a new
     * line and then returns whether another follows the one that has just been read.
     * <p>Note: "Stream<String> lines()" has been removed because N/A.
     */
    private static class CustomBufferedReader extends BufferedReader {

        private Reader in;
        private char[] cb;
        private int nChars, nextChar;
        private static final int INVALIDATED = -2;
        private static final int UNMARKED = -1;
        private int markedChar = UNMARKED;
        private int readAheadLimit = 0;       // Valid only when markedChar > 0
        private boolean skipLF = false;       // If the next character is a line feed, skip it
        private boolean markedSkipLF = false; // The skipLF flag when the mark was set

        // Here we keep the BufferedReader's genuine values and neither replaced
        // 8192 by PREFERRED_IO_BUFFER_SIZE nor replaced 80 by 120 for instance,
        // so that performances of CustomBufferedReader are compared with equity:
        // 1-against the performances of the genuine BufferedReader class
        // 2-against the performances of countLinesWithCharLookup() & al.
        private static final int defaultCharBufferSize = 8192;
        private static final int defaultExpectedLineLength = 80;

        public CustomBufferedReader(Reader in, int sz) {
            super(in);
            if (sz <= 0) {
                throw new IllegalArgumentException("Buffer size <= 0");
            }
            this.in = in;
            cb = new char[sz];
            nextChar = nChars = 0;
        }

        public CustomBufferedReader(Reader in) {
            this(in, defaultCharBufferSize);
        }

        private void ensureOpen() throws IOException {
            if (in == null) {
                throw new IOException("Stream closed");
            }
        }

        private void fill() throws IOException {
            int dst;
            if (markedChar <= UNMARKED) {
                /* No mark */
                dst = 0;
            } else {
                /* Marked */
                int delta = nextChar - markedChar;
                if (delta >= readAheadLimit) {
                    /* Gone past read-ahead limit: Invalidate mark */
                    markedChar = INVALIDATED;
                    readAheadLimit = 0;
                    dst = 0;
                } else {
                    if (readAheadLimit <= cb.length) {
                        /* Shuffle in the current buffer */
                        System.arraycopy(cb, markedChar, cb, 0, delta);
                        markedChar = 0;
                        dst = delta;
                    } else {
                        /* Reallocate buffer to accommodate read-ahead limit */
                        char[] ncb = new char[readAheadLimit];
                        System.arraycopy(cb, markedChar, ncb, 0, delta);
                        cb = ncb;
                        markedChar = 0;
                        dst = delta;
                    }
                    nextChar = nChars = delta;
                }
            }

            int n;
            do {
                n = in.read(cb, dst, cb.length - dst);
            } while (n == 0);
            if (n > 0) {
                nChars = dst + n;
                nextChar = dst;
            }
        }

        @Override
        public int read() throws IOException {
            synchronized (lock) {
                ensureOpen();
                for (; ; ) {
                    if (nextChar >= nChars) {
                        fill();
                        if (nextChar >= nChars) {
                            return -1;
                        }
                    }
                    if (skipLF) {
                        skipLF = false;
                        if (cb[nextChar] == '\n') {
                            nextChar++;
                            continue;
                        }
                    }
                    return cb[nextChar++];
                }
            }
        }

        private int read1(char[] cbuf, int off, int len) throws IOException {
            if (nextChar >= nChars) {
            /* If the requested length is at least as large as the buffer, and
               if there is no mark/reset activity, and if line feeds are not
               being skipped, do not bother to copy the characters into the
               local buffer.  In this way buffered streams will cascade
               harmlessly. */
                if (len >= cb.length && markedChar <= UNMARKED && !skipLF) {
                    return in.read(cbuf, off, len);
                }
                fill();
            }
            if (nextChar >= nChars) {
                return -1;
            }
            if (skipLF) {
                skipLF = false;
                if (cb[nextChar] == '\n') {
                    nextChar++;
                    if (nextChar >= nChars) {
                        fill();
                    }
                    if (nextChar >= nChars) {
                        return -1;
                    }
                }
            }
            int n = Math.min(len, nChars - nextChar);
            System.arraycopy(cb, nextChar, cbuf, off, n);
            nextChar += n;
            return n;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            synchronized (lock) {
                ensureOpen();
                if ((off < 0) || (off > cbuf.length) || (len < 0) ||
                        ((off + len) > cbuf.length) || ((off + len) < 0)) {
                    throw new IndexOutOfBoundsException();
                } else if (len == 0) {
                    return 0;
                }

                int n = read1(cbuf, off, len);
                if (n <= 0) {
                    return n;
                }
                while ((n < len) && in.ready()) {
                    int n1 = read1(cbuf, off + n, len - n);
                    if (n1 <= 0) {
                        break;
                    }
                    n += n1;
                }
                return n;
            }
        }

        String readLine(boolean ignoreLF) throws IOException {
            StringBuffer s = null;
            int startChar;

            synchronized (lock) {
                ensureOpen();
                boolean omitLF = ignoreLF || skipLF;

                for (; ; ) {

                    if (nextChar >= nChars) {
                        fill();
                    }
                    if (nextChar >= nChars) { /* EOF */
                        // The ONLY difference with BufferedReader is here:
                        // we use ">=" instead of ">" to get the last line.
                        if (s != null && s.length() >= 0) {
                            return s.toString();
                        } else {
                            return null;
                        }
                    }
                    boolean eol = false;
                    char c = 0;
                    int i;

                    /* Skip a leftover '\n', if necessary */
                    if (omitLF && (cb[nextChar] == '\n')) {
                        nextChar++;
                    }
                    skipLF = false;
                    omitLF = false;

                    charLoop:
                    for (i = nextChar; i < nChars; i++) {
                        c = cb[i];
                        if ((c == '\n') || (c == '\r')) {
                            eol = true;
                            break;
                        }
                    }

                    startChar = nextChar;
                    nextChar = i;

                    if (eol) {
                        String str;
                        if (s == null) {
                            str = new String(cb, startChar, i - startChar);
                        } else {
                            s.append(cb, startChar, i - startChar);
                            str = s.toString();
                        }
                        nextChar++;
                        if (c == '\r') {
                            skipLF = true;
                        }
                        return str;
                    }

                    if (s == null) {
                        s = new StringBuffer(defaultExpectedLineLength);
                    }
                    s.append(cb, startChar, i - startChar);
                }
            }
        }

        @Override
        public String readLine() throws IOException {
            return readLine(false);
        }

        /**
         * Reads a line of text. A line is considered to be terminated by any one
         * of a line feed ('\n'), a carriage return ('\r'), or a carriage return
         * followed immediately by a linefeed.
         *
         * @return whether another line follows the one that has just been read.
         */
        public boolean hasNextLine() throws IOException {
            // Conclusion of perf. measure over ~ 10 GB of files entirely fitting in the cache of
            // an HDD: not faster than readLine() even though here no memory allocations happen!
            // Performances are also the same with/without "synchronized(lock)" and "ensureOpen()"
            // so these have been left in place as they both exist in BufferReader.readLine().
            boolean foundNonLineBreakChar = false;
            synchronized (lock) {
                ensureOpen();
                boolean omitLF = skipLF;
                for (; ; ) {
                    if (nextChar >= nChars) {
                        fill();
                    }
                    if (nextChar >= nChars) { /* EOF */
                        return foundNonLineBreakChar;
                    }

                    /* Skip a leftover '\n', if necessary */
                    if (omitLF && (cb[nextChar] == '\n')) {
                        nextChar++;
                    }
                    skipLF = false;
                    omitLF = false;

                    boolean eol = false;
                    char c = 0;
                    int i;
                    for (i = nextChar; i < nChars; i++) {
                        c = cb[i];
                        if ((c == '\n') || (c == '\r')) {
                            eol = true;
                            break;
                        }
                    }

                    nextChar = i;
                    if (eol) {
                        nextChar++;
                        if (c == '\r') {
                            skipLF = true;
                        }
                        return true;
                    }
                    foundNonLineBreakChar = true;
                }
            }
        }

        @Override
        public long skip(long n) throws IOException {
            if (n < 0L) {
                throw new IllegalArgumentException("skip value is negative");
            }
            synchronized (lock) {
                ensureOpen();
                long r = n;
                while (r > 0) {
                    if (nextChar >= nChars) {
                        fill();
                    }
                    if (nextChar >= nChars) /* EOF */ {
                        break;
                    }
                    if (skipLF) {
                        skipLF = false;
                        if (cb[nextChar] == '\n') {
                            nextChar++;
                        }
                    }
                    long d = nChars - nextChar;
                    if (r <= d) {
                        nextChar += r;
                        r = 0;
                        break;
                    } else {
                        r -= d;
                        nextChar = nChars;
                    }
                }
                return n - r;
            }
        }

        @Override
        public boolean ready() throws IOException {
            synchronized (lock) {
                ensureOpen();

                /*
                 * If newline needs to be skipped and the next char to be read
                 * is a newline character, then just skip it right away.
                 */
                if (skipLF) {
                    /* Note that in.ready() will return true if and only if the next
                     * read on the stream will not block.
                     */
                    if (nextChar >= nChars && in.ready()) {
                        fill();
                    }
                    if (nextChar < nChars) {
                        if (cb[nextChar] == '\n') {
                            nextChar++;
                        }
                        skipLF = false;
                    }
                }
                return (nextChar < nChars) || in.ready();
            }
        }

        @Override
        public boolean markSupported() {
            return true;
        }

        @Override
        public void mark(int readAheadLimit) throws IOException {
            if (readAheadLimit < 0) {
                throw new IllegalArgumentException("Read-ahead limit < 0");
            }
            synchronized (lock) {
                ensureOpen();
                this.readAheadLimit = readAheadLimit;
                markedChar = nextChar;
                markedSkipLF = skipLF;
            }
        }

        @Override
        public void reset() throws IOException {
            synchronized (lock) {
                ensureOpen();
                if (markedChar < 0) {
                    throw new IOException((markedChar == INVALIDATED)
                            ? "Mark invalid"
                            : "Stream not marked");
                }
                nextChar = markedChar;
                skipLF = markedSkipLF;
            }
        }

        @Override
        public void close() throws IOException {
            synchronized (lock) {
                if (in == null) {
                    return;
                }
                try {
                    in.close();
                } finally {
                    in = null;
                    cb = null;
                }
            }
        }
    }
}
