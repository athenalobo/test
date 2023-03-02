package bns.testcarl.command;
import java.util.List;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import picocli.CommandLine.*;


@Command(name = "RunDetectorFramework",
        mixinStandardHelpOptions = true,
        description = "Used to run the detector framework on CLI.")
public class DetectorLogic implements Runnable
{
    @Override
    public void run(){}
    public static void runDetectorLogic(List<String> list)
    {
        ProcessBuilder processBuilder = new ProcessBuilder(list);
        processBuilder.redirectErrorStream(true);

        BufferedReader r;
        try {
            r = new BufferedReader(new InputStreamReader(processBuilder.start().getInputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String line;
        while(true)
        {
            try {
                line = r.readLine();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if(line == null) break;
            System.out.println(line);
        }
    }
}
