package ssh.connect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
 

/**
 * Ver.0.14
 * 
 * Add files to zip archive
 *  * 
 * @author peterbryzgalov
 *
 */
public class AppZip
{
	ArrayList<File> file_list;
	
	private String SOURCE_FOLDER = null;
	
	// Length of the source path of the files to be archived, that should be skipped.
	// Need to store files with short paths relative to the topmost folder in archive.
	// Set inside setSource method.
	private int SOURCE_PATH_SKIP_LENGTH = 0;
	
	Pattern filter_pattern = null;

	AppZip(String source)  throws IOException {
		this(source,null);
	 }
	
	/**
	 * Prepare file list for packaging.
	 * @param source Directory where files are located. 
	 * @param filter Filter for excluding files
	 * @throws IOException
	 */
	AppZip(String source, String filter)  throws IOException {
		file_list = new ArrayList<File>();
		// filter for unneeded files
		//filter_pattern =  Pattern.compile(convertFilterPattern(filter));
	    
		if (source.length() > 0) setSource(source);
		else throw new IOException("Source path for creating archive is empty.");
		// Check if source path is valid
	    File local = new File(source);
	    if (!local.exists()) throw new IOException("Source path for creating archive is not valid (not exists): "+source);
	    SearchByNameFilter sbn_filter = new SearchByNameFilter(null, filter);
	    FileListGenerator fl_generator = new FileListGenerator(this.SOURCE_FOLDER,sbn_filter);
	    file_list = fl_generator.getList();
	}

	
	/**
	 * Zip files from file_list into archive zipFile 
	 * @param zipFile output ZIP file location
	 */
	public void zipIt(File zip_file) throws IOException, FileNotFoundException,SecurityException {
		byte[] buffer = new byte[1024];
		ZipOutputStream zos=null;
		try{
			FileOutputStream fos = new FileOutputStream(zip_file);
			zos = new ZipOutputStream(fos);
			System.out.println("Creating Zip : " + zip_file.getCanonicalPath());
			for(File file : this.file_list) {
				//System.out.print(" file added : " + file);
				ZipEntry ze= new ZipEntry(generateZipEntry(file.getAbsolutePath()));
				ze.setTime(file.lastModified());
				//Date modificationTime = new Date(ze.getTime());
				//System.out.println("\t\t\t\t\t"+modificationTime);
				zos.putNextEntry(ze);
				FileInputStream in =  new FileInputStream(file);
				try {
					int len;
					while ((len = in.read(buffer)) > 0) {
						zos.write(buffer, 0, len);
					}
				} finally {
					in.close();
				}
			}

			
		} finally {   
			zos.closeEntry();
			zos.close();
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
		//generateFileList(new File(dirname));
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


