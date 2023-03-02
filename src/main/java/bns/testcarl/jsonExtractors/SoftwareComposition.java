package bns.testcarl.jsonExtractors;
import bns.testcarl.command.Constants;
import ch.qos.logback.core.net.ObjectWriter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import picocli.CommandLine.*;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import static java.lang.Object.*;

@Command(name = "ExtractFromDetectorOutput",
        version = "ExtractFromDetectorOutput 1.0",
        mixinStandardHelpOptions = true,
        description = "This extracts the output of the" +
                " detector framework and outputs a json file with identified frameworks"+
                "and their categories.")
public class SoftwareComposition implements Runnable
{
    @Override
    public void run(){}
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
        JSONObject outputJSON2=new JSONObject();
        JSONObject outputJSON3=new JSONObject();
        JSONObject outputJSON4=new JSONObject();



        JSONObject innerObj = (JSONObject) jo.get("languages per type");
        JSONObject prog = (JSONObject) innerObj.get("programming");
        JSONObject file_per_language=(JSONObject) jo.get("file per language");
        JSONObject prog1=(JSONObject) file_per_language.get("programming");

        int s= prog.size();

        //File Count

        HashMap<String,Integer> map=new HashMap<>();

        for (Map.Entry pair : (Iterable<Map.Entry>) prog.entrySet())
        {

            int c=0;

            JSONObject count = (JSONObject) prog.get(pair.getKey());
            for(Map.Entry pair1 : (Iterable<Map.Entry>) count.entrySet())
            {
                c+=(long)(pair1.getValue());
            }
            map.put((String)pair.getKey(),c);
        }

        //File ids

        HashMap<String, ArrayList<Integer>> hm1=new HashMap<>();

        for (Map.Entry pair : (Iterable<Map.Entry>) prog1.entrySet())
        {
            String a= (String) pair.getKey();
            ArrayList<Integer> al= (ArrayList<Integer>) pair.getValue();
            hm1.put(a,al);

        }

        //Path values
        JSONArray filesVal = (JSONArray) jo.get("files");
        HashMap<Long, String> Id_Path = new HashMap<>();
        Iterator filesValObj = filesVal.iterator();
        while(filesValObj.hasNext())
        {
            JSONObject IdPathObj = (JSONObject) filesValObj.next();
            Id_Path.put((Long) IdPathObj.get("id"), (String) IdPathObj.get("path"));
        }


        JSONObject jo2=new JSONObject(map);
        outputJSON2.put("Languages with File Count", jo2);

        JSONObject jo3=new JSONObject(hm1);
        outputJSON3.put("Languages with File ids", jo3);

        JSONObject jo4=new JSONObject(Id_Path);
        outputJSON4.put("File id with path",jo4);


        outputJSON2.putAll(outputJSON3);
        outputJSON2.putAll(outputJSON4);


        String op = Constants.software_composition;

        PrintWriter pw;
        try {
            pw = new PrintWriter(op);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        pw.write(outputJSON2.toJSONString());
        pw.flush();
        pw.close();

    }


//    public static void main(String[] args) throws Exception
//    {
//        extractor(Constants.framework_detector_output);
//
//    }


}
