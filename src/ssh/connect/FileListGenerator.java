package ssh.connect;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.nio.file.*;

public class FileListGenerator {
	
	private ArrayList<File> file_list;
	FileFilter f_filter;
	//PathDetector p_detector;
	private ArrayList<String> seen_path_list; // List of seen folders (paths)
	
	public FileListGenerator(String start_path, FileFilter f_filter) throws NullPointerException, IOException {
		file_list = new ArrayList<File>();
		seen_path_list = new ArrayList<String>(100);  
		this.f_filter = f_filter;
		if (start_path == null || start_path.length() < 1) throw new NullPointerException ("start_path is null or empty."); 
		File start = new File(start_path);
		if (start==null || !start.isDirectory()) {
			throw new FileNotFoundException("Start path "+start_path+" is not found or not a directory");
		}
		
		//p_detector = new PathDetector(start_path);
		generateFileList(start, f_filter);
	}
	
	/**
	 * Getter method for file list.
	 * @return file_list
	 */
	public ArrayList<File> getList() {
		return this.file_list;
	}
	
	/**
	 * Traverse subdirectories and 
	 * add files into file_list  
	 * @param node file or directory
	 * @throws IOException 
	 * @throws NullPointerException 
	 */
	private void generateFileList(File node, FileFilter f_filter) throws NullPointerException, IOException {
		if (Files.isSymbolicLink(node.toPath())) {
			Path target_path=Files.readSymbolicLink(node.toPath());
			System.out.println("Have symlink: "+node.toString() +" -> "+target_path.toString());
			if (f_filter.filter(node)) return;
			File real = node.toPath().toRealPath(LinkOption.NOFOLLOW_LINKS).toFile();
			this.file_list.add(real);			
    	}
		
		//add file only
		else if (node.isFile()){
			if (f_filter.filter(node)) return;
			File file = node.getAbsoluteFile();
			this.file_list.add(file);
			//p_detector.detectPaths(file);
		}

		// Recursive call for directory
		else if (node.isDirectory()) {
			// Check for cross-directory links and loops
			String canon_p = node.getCanonicalPath();
			if (seen_path_list.contains(canon_p)) {
				System.err.println("Already seen this path: " + canon_p);				
			} 
			else {
				seen_path_list.add(canon_p);
				
				// Get list of files in folder
				File[] subNodes = node.listFiles();
				for(File file : subNodes){
					generateFileList(file, f_filter);
				}
			}
		}
	}
}
