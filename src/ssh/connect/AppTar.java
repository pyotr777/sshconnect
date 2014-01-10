package ssh.connect;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Pattern;

import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarOutputStream;
 

/**
 * Ver.1.0
 * 
 * Add files to tar archive.
 * Use library JTar 2.1
 * 
 * @author peterbryzgalov
 *
 */
public class AppTar
{
	ArrayList<File> file_list;
	
	private String SOURCE_FOLDER = null;
	
	// Length of the source path of the files to be archived, that should be skipped.
	// Need to store files with short paths relative to the topmost folder in archive.
	// Set inside setSource method.
	private int SOURCE_PATH_SKIP_LENGTH = 0;
	
	private String source_folder_name = "";
	Pattern filter_pattern = null;

	AppTar(String source)  throws IOException {
		this(source,null);
	 }
	
	/**
	 * Prepare file list for packaging.
	 * @param source Directory where files are located. 
	 * @param filter Filter for excluding files
	 * @throws IOException
	 */
	AppTar(String source, String filter)  throws IOException {
		file_list = new ArrayList<File>();
		// filter for unneeded files
		//filter_pattern =  Pattern.compile(convertFilterPattern(filter));
	    
		if (source.length() > 0) setSource(source);
		else throw new IOException("Source path for creating archive is empty.");
		// Check if source path is valid
	    File local = new File(source);
	    if (!local.exists()) throw new IOException("Source path for creating archive is not valid (not exists): "+source);
	    SearchByNameFilter sbn_filter = new SearchByNameFilter(null, filter,false);
	    System.out.print(" Generating file list... ");
	    FileListGenerator fl_generator = new FileListGenerator(this.SOURCE_FOLDER,sbn_filter);
	    file_list = fl_generator.getList();
	}

	
	/**
	 * Zip files from file_list into archive zipFile 
	 * @param zipFile output ZIP file location
	 */
	public void tarIt(File tar_file) throws IOException, FileNotFoundException,SecurityException {
		//byte[] buffer = new byte[2048];
		TarOutputStream tos=null;
		try{
			FileOutputStream fos = new FileOutputStream(tar_file);
			tos = new TarOutputStream( new BufferedOutputStream(fos));
			System.out.println("Creating tar " + tar_file + " with files:");
			for(File file : this.file_list) {
				System.out.print(file);
				TarEntry te= new TarEntry(file, generateTarEntry(file.getAbsolutePath()));
				te.setModTime(file.lastModified());
				Date modificationTime = te.getModTime();
				System.out.print("\t\t"+modificationTime);
				// copy file owner permissions
				// default permission: 644				
				TarHeader tar_header = te.getHeader();
				if (file.canExecute()) tar_header.mode = tar_header.mode | 0100; // add execute permission
				if (file.canWrite()) tar_header.mode = tar_header.mode | 0400; // add write permission
				else tar_header.mode = tar_header.mode & 0177577; // remove write permission
				System.out.println("\t\t "+ Integer.toOctalString(tar_header.mode - 0100000)+"\t"+tar_header.size);
				if (Files.isSymbolicLink(file.toPath())) {
					tar_header.linkFlag=TarHeader.LF_SYMLINK;
					String target_path = Files.readSymbolicLink(file.toPath()).toString();
					tar_header.linkName=new StringBuffer(target_path);		
					tar_header.size = 0;
					tos.putNextEntry(new TarEntry(tar_header));					
				} else {	
					tos.putNextEntry(new TarEntry(tar_header));
				    BufferedInputStream origin = new BufferedInputStream(new FileInputStream( file ));
				    int count;
				    byte data[] = new byte[2048];
				  
				    while((count = origin.read(data)) != -1) {
				    	tos.write(data, 0, count);
				    }
				    origin.close();
				}			    
				tos.flush();				
			}
		} finally {   
			tos.close();
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
	private String generateTarEntry(String file){
		String path=file.substring(SOURCE_PATH_SKIP_LENGTH, file.length());
		return path;
	}
	
	/**
	 * Create filename for archive from path, same as the lowest level directory name in the path.
	 * @param path Project folder full path 
	 * @return archive name
	 */
	public String archiveName(String path) {
		String tar_name;
		// Get bottom level folder name
		int last_separator = path.lastIndexOf(File.separator);
		while (last_separator + 1 >= path.length() && last_separator > 0) {
			path = path.substring(0,last_separator);
		}
		String last_folder = path.substring(last_separator+1);
		this.source_folder_name = last_folder;		
		String path_to_last_folder = path.substring(0, last_separator);
		
		tar_name = path_to_last_folder + File.separator + last_folder + ".tar";		
		return tar_name;
	}


	/**
	 * Return source folder name before replacing spaces in it.
	 * @return
	 */
	public String getOriginalFolder() {
		return this.source_folder_name;
	}
	
}


