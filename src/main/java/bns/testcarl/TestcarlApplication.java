package bns.testcarl;

import bns.testcarl.command.MainCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import picocli.CommandLine;

@SpringBootApplication
public class TestcarlApplication implements CommandLineRunner {

/*
   @Autowired
   private SequentialFlow mainCommand;
*/
@Autowired
	private MainCommand mainCommand;

	public static void main(String[] args) {
		SpringApplication.run(TestcarlApplication.class, args);
	}

	@Override
	public void run(String... args) {
		CommandLine cmd = new CommandLine(mainCommand);
		cmd.execute(args);
		System.exit(0);
	}

}
