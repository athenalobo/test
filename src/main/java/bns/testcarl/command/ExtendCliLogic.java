package bns.testcarl.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ExtendCliLogic {
    public static void extendCliLogic(String extensionuid){

        String extendCliEXE = Constants.extendcli;

        ProcessBuilder processBuilder3 = new ProcessBuilder(extendCliEXE, "download", extensionuid);
        processBuilder3.redirectErrorStream(true);

        BufferedReader r3;
        try {
            r3 = new BufferedReader(new InputStreamReader(processBuilder3.start().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line3;
        while(true){
            try {
                line3 = r3.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if(line3 == null) break;
            System.out.println(line3);
        }
    }


//        public static void main(String[] args) throws Exception
//    {
//        String extensionuid = "com.castsoftware.angularjs";
//        extendCliLogic(extensionuid);
//    }


}
