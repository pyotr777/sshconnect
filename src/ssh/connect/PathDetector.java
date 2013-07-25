package ssh.connect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

/**
 * Class for detecting and replacing paths in text files.
 * @author peterbryzgalov
 *
 */
public class PathDetector {
	
	private String local_path;
	private String accept_pattern;
	private String reject_pattern;
	
	private Pattern path_pattern1 = Pattern.compile("[\\S]*?[\\\\/][\\S]*"); // at least one slash or backslash in a word (char sequence between spaces)
	
	
	public PathDetector(String local_path, String remote_path, String pattern, String reject_pattern) {
		System.out.println("Local path: " + local_path);
		this.local_path = local_path;
		this.accept_pattern = pattern;
		this.reject_pattern = reject_pattern;
	}
	
	/**
	 * Detect paths in makfiles and source files
	 * @param local_path local path to start search
	 * @throws NullPointerException 
	 * @throws IOException 
	 */
	public void detectPaths() throws NullPointerException, IOException {
		List<File> files_to_check = new ArrayList<File>();
		//files_to_check = getFilesList(files_to_check, local_path,searchable_files_extensions);
		SearchByNameFilter sbn_filter = new SearchByNameFilter(accept_pattern, reject_pattern);
	    FileListGenerator fl_generator = new FileListGenerator(local_path,sbn_filter);
	    files_to_check = fl_generator.getList();
		for (File file:files_to_check) {
			String s = FileUtils.readFileToString(file, "UTF-8");
			Matcher m = this.path_pattern1.matcher(s);
			while (m.find()) {
				System.out.println("Have " + m.group(0));
				String subs = m.group(0);
				if (subs.length() > 1) {					
					if (subs.matches(".*"+local_path+".*")) {
						System.out.println("Have " + m.group(0));
						System.out.println("!!! LOCAL PATH DETECTED");
					}
				}
			}
			
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
		FilenameUtils.normalizeNoEndSeparator(path);
		String prefix = FilenameUtils.getPrefix(path);
		if (prefix.equals(File.separator)) {
			//System.out.println("Have good prefix: " + prefix);
			return true;
		}
		//System.out.println(norm_path+" vs "+canonical_path);
		return canonical_path.equals(path);
	}

}
