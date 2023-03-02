package bns.testcarl.jsonExtractors;

import bns.testcarl.command.Constants;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import picocli.CommandLine.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
@Command(name = "ExtractFromDetectorOutput",
        version = "ExtractFromDetectorOutput 1.0",
        mixinStandardHelpOptions = true,
        description = "This extracts the output of the" +
                " detector framework and outputs a json file with only the detected frameworks.")
public class ExtractFromDetectorOutput implements Runnable {
    @Override
    public void run(){}
    public static void extractor(String detectorOutputFile)
    {
        System.out.println("\nExtracting the frameworks for determinator service...\n ");

        Object obj;
        try {
            obj = new org.json.simple.parser.JSONParser().parse(new FileReader(detectorOutputFile));
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }

        // typecasting obj to JSONObject
        JSONObject jo = (JSONObject) obj;
        JSONObject outputJSON = new JSONObject();

        Map frameworks = (Map) jo.get("frameworks");

        JSONArray ja = new JSONArray();

        for (Map.Entry pair : (Iterable<Map.Entry>) frameworks.entrySet())
        {
            ja.add(pair.getKey());
        }

        outputJSON.put("technologies", ja);
        outputJSON.put("protocol" , 4);


        String curlInput = Constants.curlInput;

        PrintWriter pw;
        try {
            pw = new PrintWriter(curlInput);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        pw.write(outputJSON.toJSONString());
        pw.flush();
        pw.close();
    }


/*
    public static void main(String[] args) throws Exception
    {
        String curlInput = "C:\\Users\\BNS\\Desktop\\TESTBNS\\scans\\detectorOUTPUT.json";
        ExtractFromDetectorOutput.extractor(curlInput);

    }
*/

}