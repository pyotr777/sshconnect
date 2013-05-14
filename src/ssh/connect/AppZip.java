package ssh.connect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
 
public class AppZip
{
	List<String> fileList;
	
	
	private String SOURCE_FOLDER = null;
	
	// Length of the source path of the files to be archived, that should be skipped.
	// Need to store files with short paths relative to the topmost folder in archive.
	// Set inside setSource method.
	private int SOURCE_PATH_SKIP_LENGTH = 0;
	
	Pattern filter_pattern = null;

	AppZip(String source)  throws IOException {
		fileList = new ArrayList<String>();
		if (source.length() > 0) setSource(source);
		else throw new IOException("Source path for creating archive is empty.");
		// Check if source path is valid
	    File local = new File(source);
	    if (!local.exists()) throw new IOException("Source path is not valid (not exists): "+source);
	 }
	
	AppZip(String source, String filter)  throws IOException {
		fileList = new ArrayList<String>();
		// filter for unneeded files
		filter_pattern =  Pattern.compile(convertFilterPattern(filter));
	    
		if (source.length() > 0) setSource(source);
		else throw new IOException("Source path for creating archive is empty.");
		// Check if source path is valid
	    File local = new File(source);
	    if (!local.exists()) throw new IOException("Source path is not valid (not exists): "+source);    
	}

	/**
	 * Transform common file patterns like "*.html,.*.*" into regular expression: "(.*\\.html)|(\\..*\\..*)".
	 * @param filter String containing comma-separated common patterns
	 * @return String, containing regular expression for matching such files.
	 */
	private String convertFilterPattern(String filter) {
		String regexp = "(" +filter.replaceAll(",",")|(") + ")";
		regexp = regexp.replaceAll("\\.", "\\\\.");
		regexp = regexp.replaceAll("\\*", ".*");
		return regexp;
	}

	/**
	 * Zip it
	 * @param zipFile output ZIP file location
	 */
	public void zipIt(String zipFile){

		byte[] buffer = new byte[1024];

		try{

			FileOutputStream fos = new FileOutputStream(zipFile);
			ZipOutputStream zos = new ZipOutputStream(fos);

			System.out.println("Output to Zip : " + zipFile);

			for(String file : this.fileList){

				System.out.println("File Added : " + file);
				ZipEntry ze= new ZipEntry(file);
				zos.putNextEntry(ze);
				String filename = SOURCE_FOLDER.substring(0,SOURCE_PATH_SKIP_LENGTH) + file;
				FileInputStream in =  new FileInputStream(filename);

				int len;
				while ((len = in.read(buffer)) > 0) {
					zos.write(buffer, 0, len);
				}

				in.close();
			}

			zos.closeEntry();
			//remember close it
			zos.close();
		} catch(IOException ex) {
			ex.printStackTrace();   
		}
	}
	
	/**
	 * Setter function for SOURCE_FOLDER
	 * @param dirname	Path to files to be zipped.
	 */
	public void setSource(String dirname) {
		this.SOURCE_FOLDER = dirname;
		this.SOURCE_PATH_SKIP_LENGTH = dirname.lastIndexOf(File.separator) + 1;
		// If slash (path separator "/") is the last symbol, skip one more level from the end of path
		if (this.SOURCE_PATH_SKIP_LENGTH >= dirname.length()-1) this.SOURCE_PATH_SKIP_LENGTH = dirname.substring(0, dirname.length()-1).lastIndexOf(File.separator) + 1;
		// If no slash found, skip 0 chars.
		if (this.SOURCE_PATH_SKIP_LENGTH < 0) this.SOURCE_PATH_SKIP_LENGTH = 0;
		generateFileList(new File(dirname));
	}

	/**
	 * Traverse a directory and get all files,
	 * and add the file into fileList  
	 * @param node file or directory
	 */
	private void generateFileList(File node){
		
		if (filterThisFile(node)) return;
			
		//add file only
		if (node.isFile()){
			File file = node.getAbsoluteFile();
			String path = file.toString();
			String entry = generateZipEntry(path);
			fileList.add(entry);
		}

		if (node.isDirectory()){
			String[] subNote = node.list();
			for(String filename : subNote){
				generateFileList(new File(node, filename));
			}
		}
	}
		
	/**
	 * Check if this file should be filtered out or oncluded into archive
	 * @param node	File to be checked
	 * @return true if file should be filtered out
	 */
	private boolean filterThisFile(File node) {
		String filename = node.getName();
		Matcher m = filter_pattern.matcher(filename);
		if (m.matches()) {
			try {
				System.out.println("Filtered "+node.getCanonicalPath());
			} catch (IOException e) {
				e.printStackTrace();
			}
			return true;
		}
		return false;
	}

	/**
	 * Format the file path for zip
	 * @param file file path
	 * @return Formatted file path
	 */
	private String generateZipEntry(String file){
		return file.substring(SOURCE_PATH_SKIP_LENGTH, file.length());
	}
}
