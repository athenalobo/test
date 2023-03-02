package bns.testcarl.jsonExtractors;
import bns.testcarl.command.Constants;
import ch.qos.logback.core.net.ObjectWriter;
import org.json.JSONArray;
import org.json.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import picocli.CommandLine.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

import static java.lang.Object.*;

public class Task2FromDetectorOutput
{
    public static void extractor(String detectorOutputFile)
    {
        //System.out.println("\nExtracting the frameworks for determinator service...\n ");
        Object obj;
        try {
            obj = new JSONParser().parse(new FileReader(detectorOutputFile));
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }

        // typecasting obj to JSONObject
        JSONObject jo = (JSONObject) obj;
        Map map = new JSONObject((Map) jo.get("frameworks per classification"));
        JSONArray jsonArray=new JSONArray();

        for (Map.Entry pair : (Iterable<Map.Entry>) map.entrySet())
        {
            JSONObject frameworks=new JSONObject();
            org.json.simple.JSONArray v = (org.json.simple.JSONArray)(pair.getValue());
            for(int i=0;i<v.size();i++)
            {
                //System.out.println(v.get(i));
                frameworks.put("type",pair.getKey());
                frameworks.put("name",v.get(i));
                jsonArray.put(frameworks);
            }
        }

        JSONArray composition=new JSONArray();

        //System.out.println(jsonArray);
        JSONObject outputJSON0 = new JSONObject();
        JSONObject outputJSON = new JSONObject();
        JSONObject outputJSON1=new JSONObject();
        JSONObject outputJSON2=new JSONObject();

        String graph = (String) jo.get("graphviz");

        outputJSON0.put("name", Constants.app_name);
        outputJSON.put("frameworks", jsonArray);
        outputJSON1.put("graphviz", graph);
        outputJSON2.put("composition",composition);


        outputJSON.putAll(outputJSON);
        outputJSON.putAll(outputJSON0);
        outputJSON.putAll(outputJSON1);
        outputJSON.putAll(outputJSON2);

        String op = Constants.fastScanOutput;

        PrintWriter pw;
        try {
            pw = new PrintWriter(op);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        pw.write(outputJSON.toJSONString());
        pw.flush();
        pw.close();

    }


//    public static void main(String[] args) throws Exception
//    {
//        extractor(Constants.framework_detector_output);
//
//    }


}
