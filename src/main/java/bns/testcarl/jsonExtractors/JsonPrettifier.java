package bns.testcarl.jsonExtractors;

import bns.testcarl.command.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.io.IOException;

public class JsonPrettifier {
    public static void prettify() {
        String inputFilePath = Constants.fastScanOutput;
        String outputFilePath = Constants.fastScanOutputPretty;
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        try {
            Object jsonObject = mapper.readValue(new File(inputFilePath), Object.class);
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(new File(outputFilePath), jsonObject);
            System.out.println("JSON file prettified successfully!");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
