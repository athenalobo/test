package bns.testcarl.command;

import bns.testcarl.jsonExtractors.CurlFromDetectorOutput;
import bns.testcarl.jsonExtractors.ExtractFromDetectorOutput;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.stereotype.Component;
import picocli.CommandLine.*;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
@Command(
        name = "DetectorFramework",
        mixinStandardHelpOptions = true,
        description = "Extract the technologies from source code"
)
public class SequentialFlow implements Runnable{
    @Parameters(paramLabel = "[detect_frameworkDIR]",
            defaultValue = "C:\\Users\\BNS\\Desktop\\TESTBNS\\detect_framework\\detect_framework.exe",
            description = "Directory for detect_framework.exe" )
    private String detect_frameworkDIR;
    @Parameters(
            paramLabel = "[inputDIR]",
            defaultValue = "C:\\Users\\BNS\\Desktop\\TESTBNS\\CRMModernization",
            description = "Directory for source code folder which contains the application" )
    private String inputDIR;

    @Parameters(
            paramLabel = "[detect_frameworkJSONoutputDIR]",
            defaultValue = "C:\\Users\\BNS\\Desktop\\TESTBNS\\scans\\detectorOUTPUT.json",
            description = "Directory for output of detect_framework.exe" )
    private String detect_frameworkJSONoutputDIR;


    @Override
    public void run() {
        List<String> list = new ArrayList<String>();
        list.add(detect_frameworkDIR);
        list.add(inputDIR);
        list.add("-o");
        list.add(detect_frameworkJSONoutputDIR);

//        ProcessBuilder processBuilder = new ProcessBuilder(detect_frameworkDIR, inputDIR, "-o", detect_frameworkJSONoutputDIR);
//        ProcessBuilder processBuilder = new ProcessBuilder(detect_frameworkDIR, "-h");
        ProcessBuilder processBuilder = new ProcessBuilder(list);
        processBuilder.redirectErrorStream(true);

        BufferedReader r = null;
        try {
            r = new BufferedReader(new InputStreamReader(processBuilder.start().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line;
        while(true){
            try {
                line = r.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if(line == null) break;
            System.out.println(line);
        }
        System.out.println("command: " + processBuilder.command());

        ExtractFromDetectorOutput.extractor(detect_frameworkJSONoutputDIR);

        String curlInput = "C:\\Users\\BNS\\Desktop\\TESTBNS\\scans\\CurlInput.json";

        CurlFromDetectorOutput.createCurl(curlInput);


        String curlOutput = "C:\\Users\\BNS\\Desktop\\TESTBNS\\scans\\Curl.txt";


        String obj = "";
        try {
            obj = new String(Files.readAllBytes(Path.of(curlOutput)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

//        System.out.println(obj);

        ProcessBuilder processBuilder2 = new ProcessBuilder("cmd.exe", "/c", obj);
        processBuilder2.redirectErrorStream(true);

        BufferedReader r2 = null;
        try {
            r2 = new BufferedReader(new InputStreamReader(processBuilder2.start().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line2;
        while(true){
            try {
                line2 = r2.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if(line2 == null) break;
            System.out.println(line2);
        }

        String extendCliInput = "C:\\Users\\BNS\\Desktop\\TESTBNS\\scans\\CURLoutput.json";
        Object obj2 = null;
        try {
            obj2 = new JSONParser().parse(new FileReader(String.valueOf(extendCliInput)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        JSONObject jo = (JSONObject) obj2;
        String extensionuid = "";
        Iterator<JSONArray> itr = jo.values().iterator();

        while (itr.hasNext()) {
            JSONArray jsonChildArray = itr.next();
            Iterator<JSONObject> innerItr = jsonChildArray.iterator();
            while(innerItr.hasNext()){
                JSONObject innerChild = innerItr.next();
                extensionuid = (String) innerChild.get("extensionuid");
                System.out.println(extensionuid);
                ExtendCliLogic.extendCliLogic(extensionuid);
            }
        }


    }
}
