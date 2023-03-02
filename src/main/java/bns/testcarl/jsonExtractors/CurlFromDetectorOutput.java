package bns.testcarl.jsonExtractors;

import bns.testcarl.command.Constants;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;


@Command(name = "CurlFromDetectorOutput", version = "CurlFromDetectorOutput.mf 1.0", mixinStandardHelpOptions = true)
public class CurlFromDetectorOutput implements Runnable {

    @Override
    public void run() {}
    public static void createCurl(String curlInput){

        System.out.println("\nCreating the curl for the determinator service...\n");

        String obj;
        try {
            obj = new String(Files.readAllBytes(Path.of(curlInput)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        System.out.println(obj);
        String ans = obj.replace("\"", "\\\"");

        ans = "curl --location --request POST \"https://extend.castsoftware.com/api/determinator\" --header \"X-CAST-AIP: 8.3.36\" " +
                "--header \"X-CAST-AIPC: 2.6.0\" --header " +
                "\"X-CXPROXY-APIKEY: XCGN1-C19E669D2848AEEBD46DCC2D923AC6D472E6158321A3DC2B607BE7E2493B8E61\" --header \"Content-Type: application/json\"" +
                " --header \"Cookie: Path=/\"" +
                " --data-raw " + "\""+  ans + "\"" +
                " -o "+ Constants.extendCliInput;
        System.out.println(ans);

        String curlOutput = Constants.curlOutput;

        PrintWriter pw;
        try {
            pw = new PrintWriter(curlOutput);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        pw.write(ans);
        pw.flush();
        pw.close();

    }


    public static void main(String[] args) throws Exception
    {
        String curlInput = Constants.curlInput;
        CurlFromDetectorOutput.createCurl(curlInput);

    }



}
