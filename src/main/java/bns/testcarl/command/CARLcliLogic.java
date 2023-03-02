package bns.testcarl.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class CARLcliLogic {

    public static void CarlCliLogic(String inputDIR, String outputDIR, String appName){

        String carlCliEXE = Constants.carlcli;


        ProcessBuilder processBuilder4 = new ProcessBuilder(carlCliEXE, "-a", appName, "-s", inputDIR, "-o", outputDIR);
        processBuilder4.redirectErrorStream(true);

        BufferedReader r4;
        try {
            r4 = new BufferedReader(new InputStreamReader(processBuilder4.start().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line4;
        while (true) {
            try {
                line4 = r4.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (line4 == null) break;
            System.out.println(line4);
        }
     }

/*
     public static void main(String[] args){
         String inputDIR = "C:\\Users\\BNS\\Desktop\\TESTBNS\\CRMModernization";
         String appName = "Bhawansh Narain Saxena";
         String outputDIR = "C:\\Users\\BNS\\Desktop\\TESTBNS\\output";

         CarlCliLogic(inputDIR, outputDIR, appName);
     }
*/
}
