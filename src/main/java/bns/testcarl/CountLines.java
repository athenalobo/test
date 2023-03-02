//package bns.testcarl;
//
//import java.io.File;
//import java.util.HashSet;
//import java.util.*;
//public class CountLines
//{
//    String dirPath = "C:\\Users\\AML\\Desktop\\java\\CRMModernization_source_code";
//    File directory = new File(dirPath);
//
//
//    Set<File> fileSet = new HashSet<File>();
//
//
//    boolean ValidPath = new File(dirPath).isDirectory();
//
//    if(ValidPath)
//    {    File[] directoryFiles = directory.listFiles();
//        if (directoryFiles != null) {
//            fileSet.addAll(Arrays.asList(directoryFiles));    }}
//
//    long nbLocs = 0L;
//    long nbBytes = 0L;
//    LineCountUtils countUtils = new LineCountUtils();
//try {
//    // no threshold for stopping counting LoC after a certain number of
//    // lines has been reached as we need the complete counts per techno.
//    nbLocs = countUtils.countCodeLines(fileSet, null);
//    nbBytes = countUtils.getNbProcessedBytes().get();
//} catch (IOException e) {
//    log.warn("Unable to get number of lines of code for technology ");
//}
//
//}
