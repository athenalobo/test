package bns.testcarl.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

public class CurlLogic {

    public static void curlLogic(String curlOutput) {
        String obj;
        try {
            obj = new String(Files.readAllBytes(Path.of(curlOutput)));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        ProcessBuilder processBuilder2 = new ProcessBuilder("cmd.exe", "/c", obj);
        processBuilder2.redirectErrorStream(true);

        BufferedReader r2;
        try {
            r2 = new BufferedReader(new InputStreamReader(processBuilder2.start().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line2;
        while (true) {
            try {
                line2 = r2.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (line2 == null) break;
            System.out.println(line2);
        }
    }
}
