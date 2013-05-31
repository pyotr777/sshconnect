package ssh.connect;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Class for running in new thread stderr output.
 * @author peterbryzgalov
 *
 */
public class stderrThread extends Thread {
	private BufferedReader stderrReader;
	
	public stderrThread(InputStream stderr) {
		stderrReader = new BufferedReader(new InputStreamReader(stderr));
	}

	public void run() {
		String line = "";
		try {
			while (true)
			{
				try {
					line = stderrReader.readLine();
				} catch (IOException e) {
					System.err.println("...");
					break;
				}
				if (line == null)
					break;
				System.err.println(line);
			}	
		}
		finally 
		{
			try {
				stderrReader.close();
			} catch (IOException e) {
				System.err.println("Error closing remote stderr stream reader.");
			}
		}
	}
}
