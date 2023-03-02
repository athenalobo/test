package bns.testcarl.command;

import bns.testcarl.jsonExtractors.*;
import org.springframework.stereotype.Component;
import picocli.CommandLine.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
@Command(
        name = "CarlTestCLI",
        mixinStandardHelpOptions = true,    // adds --help and --version options to your application
        description = "This cli application integrates the 5 CLIs"
//        ,subcommands = {
//                DetectorLogic.class,
//                ExtractFromDetectorOutput.class
//        }
)
public class MainCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Executing the main command...\n");

        List<String> list = new ArrayList<>();
        list.add(Constants.framework_detector);
        list.add(Constants.inputDIR);
        list.add("--detail");
        list.add("-o");
        list.add(Constants.framework_detector_output);

        // Running the detector_framework.exe
        DetectorLogic.runDetectorLogic(list);

        // Parsing the json output to give to the curl (Extracting the frameworks for determinator service)
        ExtractFromDetectorOutput.extractor(Constants.framework_detector_output);

        //SoftwareComposition.extractor(Constants.framework_detector_output);

        Task2FromDetectorOutput.extractor(Constants.framework_detector_output);

        JsonPrettifier.prettify();



        //ArchitecturePreviewFromDetectorOutput.extractor(Constants.framework_detector_output);


        /*input for the CurlFromDetectorOutput to extractor the curl */
        String curlInput = Constants.curlInput;

        // creating the curl to run
        CurlFromDetectorOutput.createCurl(curlInput);

        // output of the curl
        String curlOutput = Constants.curlOutput;

        // running the curl, it will save the output too!
        CurlLogic.curlLogic(curlOutput);

        String extendCliInput = Constants.extendCliInput;

        // takes in the output of the curl statement and executes the extendCli on the transformed output
        ExtensionIdFromCurlOutput.getExtensionUid(extendCliInput);

        String outputDIR = Constants.outputDIR;

        // this is the final step THE CARL CLI which will run the analysis on the source code
        CARLcliLogic.CarlCliLogic(Constants.inputDIR, outputDIR, Constants.app_name);

        /* final output is shown in the outputDIR of the TESTBNS folder */

    }

}
