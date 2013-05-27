package ssh.connect;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;

public class FileListGenerator {
	
	private ArrayList<File> file_list;
	
	public FileListGenerator(String start_path, FileFilter f_filter) throws FileNotFoundException, NullPointerException {
		file_list = new ArrayList<File>();
		if (start_path == null || start_path.length() < 1) throw new NullPointerException ("start_path is null or empty."); 
		File start = new File(start_path);
		if (start==null || !start.isDirectory()) {
			throw new FileNotFoundException("Start path "+start_path+" is not found or not a directory");
		}
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
	 */
	private void generateFileList(File node, FileFilter f_filter) {
		
		//add file only
		if (node.isFile()){
			if (f_filter.filter(node)) return;
			File file = node.getAbsoluteFile();
			this.file_list.add(file);
		}

		// Recursive call for directory
		if (node.isDirectory()){
			File[] subNodes = node.listFiles();
			for(File file : subNodes){
				generateFileList(file, f_filter);
			}
		}
	}
}
