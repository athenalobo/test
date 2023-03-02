package bns.testcarl.jsonExtractors;

import bns.testcarl.command.ExtendCliLogic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;


public class ExtensionIdFromCurlOutput {
    public static void getExtensionUid(String extendCliInput){
        Object obj;
        try {
            obj = new JSONParser().parse(new FileReader(String.valueOf(extendCliInput)));
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }

        JSONObject jo = (JSONObject) obj;
        String extensionuid;
        Iterator<JSONArray> itr = jo.values().iterator();
        while (itr.hasNext()) {
            JSONArray jsonChildArray = itr.next();
            Iterator<JSONObject> innerItr = jsonChildArray.iterator();
            while(innerItr.hasNext()){
                JSONObject innerChild = innerItr.next();
                extensionuid = (String) innerChild.get("extensionuid");
                System.out.println("\nExtension id to be downloaded -> " + extensionuid );
                ExtendCliLogic.extendCliLogic(extensionuid);
            }
        }

//          The following code is for the case when the curl request is sent with any protocol other than 4, as the
        // response format of the determinator service changes.

//        Iterator<JSONObject> itr = jo.values().iterator();
//
//        while (itr.hasNext()) {
//            JSONObject jsonChildObject = itr.next();
//            extensionuid = (String) jsonChildObject.get("extensionuid");
//            System.out.println(extensionuid);
//            ExtendCliLogic.runExtendCli(extensionuid);
//        }
    }

/*
    public static void main(String[] args){
        String extendCliInput = "C:\\Users\\BNS\\Desktop\\TESTBNS\\scans\\CURLoutput.json";

        runExtendCli(extendCliInput);
    }
*/

}
