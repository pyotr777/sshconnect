package ssh.connect;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Filter out files that match filter,
 * or accept only files, that match accept.
 * If both set to not null, first check accept, then filter.
 * @author peterbryzgalov
 */
public class SearchByNameFilter implements FileFilter {

	Pattern filter_pattern = null;
	Pattern accept_pattern = null;
	boolean verbose = true;
	
	
	/**
	 * @param filter pattern for filtering out
	 * @param accept pattern for accepting
	 */
	public SearchByNameFilter(String accept, String filter) {
		this(accept,filter,true);
	}
	
	
	/**
	 * @param filter pattern for filtering out
	 * @param accept pattern for accepting
	 * @param verbose Reports on accepting/filtering individual files
	 */
	public SearchByNameFilter(String accept, String filter, boolean verbose) {
		if (filter!=null) filter_pattern = Pattern.compile(convertFilterPattern(filter));
		if (accept!=null) accept_pattern = Pattern.compile(convertFilterPattern(accept));
		this.verbose = verbose;
	}	
	
	/**
	 * Check if this file should be filtered out or included into archive
	 * If accept is set, and file accepted, check filter
	 * If accept is set, and file rejected, return true (filter file out)
	 * If accept is not set, check filter
	 *  
	 * @param node	File to be checked
	 * @return true if file should be filtered out
	 */
	public boolean filter(File node) {
		boolean result = false;
		String filename = node.getName();
		Matcher m = null;
		if (accept_pattern!=null) {
			m = accept_pattern.matcher(filename);
			if (m.matches()) {
				try {
					if (verbose) System.out.println("Accepted "+node.getCanonicalPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
				result = false;				
			} else {
				return true;
			}
		}
		if (filter_pattern != null) {
			m = filter_pattern.matcher(filename);
			if (m.matches()) {
				try {
					if (verbose) System.out.println("Filtered "+node.getCanonicalPath());
				} catch (IOException e) {
					e.printStackTrace();
				}
				result = true;
			}
		}
		return result;
	}
	
	/**
	 * Transform common file patterns like "*.html,.*.*" into regular expression: "(.*\\.html)|(\\..*\\..*)".
	 * @param filter String containing comma-separated common patterns
	 * @return String, containing regular expression for matching such files.
	 */
	private String convertFilterPattern(String filter) {
		String regexp = "(" +filter.replaceAll(",",")|(") + ")";
		regexp = regexp.replaceAll("\\.", "\\\\.");
		regexp = regexp.replaceAll("\\*", "[^\\.]*");
		return regexp;
	}
	
}