package ssh.connect;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.LineIterator;

/**
 * Class for detecting and replacing paths in text files.
 * @author peterbryzgalov
 *
 */
public class PathDetector {
	
	private String local_path;
	private static String sp = File.separator;
	String s; // file contents
	
	public PathDetector(String local_path) {
		//System.out.println("\nLooking for local path: " + local_path);
		this.local_path = local_path;
	}
	
	/**
	 * Detect paths in makefiles and source files
	 * @param local_path local path to start search
	 * @throws NullPointerException 
	 * @throws IOException 
	 */
	public void detectPaths(File file) throws NullPointerException, IOException {
		//System.out.println("Checking file "+file.getCanonicalPath()+" for absolute paths.");
		int line_counter = 0;
		LineIterator it = FileUtils.lineIterator(file, "UTF-8");
		try {
			while (it.hasNext()) {
				line_counter++;
				s = it.nextLine();
				//if (local_path.strpos(":"))
				local_path = local_path.replaceAll("\\\\", "/");
				if (s.matches(".*"+local_path+".*")) {
					System.err.println("LOCAL PATH DETECTED in "+ file.getCanonicalPath()+"\nline "+line_counter+": "+s);
				}
			}
		} finally {
			it.close();
		}		
	}
	
	/**
	 * Returns true if path is absolute.
	 * 
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static boolean isAbsolutePath(String path) throws IOException {
		String canonical_path = FilenameUtils.normalizeNoEndSeparator(new File(path).getCanonicalPath());
		//FilenameUtils.normalizeNoEndSeparator(path);
		String prefix = FilenameUtils.getPrefix(path);
		if (prefix.equals(sp)) {
			//System.out.println("Have good prefix: " + prefix);
			return true;
		}
		//System.out.println(norm_path+" vs "+canonical_path);
		return canonical_path.equals(path);
	}

}
