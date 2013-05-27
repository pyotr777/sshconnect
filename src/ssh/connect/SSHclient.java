package ssh.connect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;  
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jcraft.jsch.UserInfo;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;


/**
 * Ver.0.16
 * 
 *  Connecting to K-scope
 *  
 * Orion SSH + JSch
 * Parsing Makefiles for replacement patterns
 * Unique temporary directory names
 * 
 * @author Peter Bryzgalov
 *
 */

public class SSHclient {

	static String searchable_files_extensions = "f90,";  // extensions of files to search for absolute paths
	
	/**
	 * @param args
	 * @throws JSchException 
	 * @throws SftpException 
	 * @throws NoSuchAlgorithmException 
	 */
	public static void main(String[] args) throws IOException, JSchException, SftpException, NoSuchAlgorithmException {
		SSHbasic basic_connection = new SSHbasic();
		basic_connection.local_path = args[args.length-1];
		for (String arg: args) {
			System.out.println(arg);
		}
		detectPaths(basic_connection.local_path);
		//basic_connection.makeConnect();
	}
	
	/**
	 * Detect paths in makfiles and source files
	 * @param local_path local path to start search
	 */
	private static List<File> detectPaths(String local_path) {
		List<File> files_to_check = new ArrayList<File>();
		files_to_check = getFilesList(files_to_check, local_path,searchable_files_extensions);
		for (File file:files_to_check) {
			String s = file.toString();
			/// look for absolute paths
		}
		return files_to_check;
	}

	/**
     * Get list of files with extensions in comma-separated list.
     * Search in path including subdirectories.
     * 
     * @param files	List of files found before
     * @param start_path Path to start search
     * @param extensions Comma-separated list of extensions
     * @return
     */
	private static List<File> getFilesList(List<File> files, String start_path, String extensions) {
		
	

		return null;
	}

	static class SSHbasic {
		
		// Remote path to F_Front and atool  
		String Ffront_path = ""; 
		
		// Initializing parameters with default values
	    String host = ""; // host IP  
	    String user = ""; // username for SSH connection  
	    String password = ""; // password for SSH connection  
	    int group_id; // user group ("kscope") ID	   
	    int port; // default SSH port  
	    
		// local project folder. Must contain Makefile and all files necessary for building.  
	    String local_path = ""; 
	    String remote_path = ""; 
	    String remote_full_path = ""; // path including temporary directory and archive name without extension
	    String archive = "";
	    
	    String makefiles = "";
	    String make = "";
	    String make_options = "";
	    static private final Pattern placeholder_pattern = Pattern.compile("<([\\w\\d\\-_]*)>");
	    private String default_archive_filename = "archive.zip"; 
	    
	    // Remote temporary folder name
	    String tmp_dir = "";
	    
	    // Temporary directory: remote_tmp
	    String remote_tmp = ""; 
	    	    
	    // Source file filter.
	    // Exclude selected file types.
	    String file_filter = ".*,*.tar,*.html,*.zip,*.jpg.*.orgin";
	    	    
	    
	    // JSch parameters initialization
	    com.jcraft.jsch.Session session = null;
	    int mode=ChannelSftp.OVERWRITE;
    	SftpProgressMonitor monitor = new MyProgressMonitor();
    	com.jcraft.jsch.Channel channel = null;
    	ChannelSftp sftp_channel = null;
    	String[] makefile_filenames = null;
    	String[] makefiles_list = null;
	    
	    
	    public SSHbasic()  throws IOException, NoSuchAlgorithmException  {	
	    	
	    	// Read parameters from configuration file
    		Properties prop = new Properties();
        	try {
                //load properties from config file
        		prop.load(new FileInputStream("config.txt"));
        		user = updateProperty(prop, "user");
        		password = updateProperty(prop, "password");
        		try {
        			group_id = Integer.parseInt(updateProperty(prop, "group_id"));
        		} catch (NumberFormatException e) {
        			group_id = 1022;
        			System.out.println("Default groupID 1022 used.");
        		}
        		host = updateProperty(prop, "host");
        		try {
        			port = Integer.parseInt(updateProperty(prop, "port"));
        		} catch (NumberFormatException e) {
        			port = 22;
        			System.out.println("Default port 22 used.");
        		}
        		// local_path = updateProperty(prop, "local_path");
        		remote_path = updateProperty(prop, "remote_path");     
        		
        		// *.origin - reserved for original copies of edited make files.
        		file_filter = updateProperty(prop, "file_filter")+",*.origin";
        		makefiles = updateProperty(prop, "makefiles");
        		make = updateProperty(prop,"make");
        		make_options = prop.getProperty("make_options"); // no need to remove spaces
        		if (make_options == null) make_options = "";
        		Ffront_path = updateProperty(prop,"Ffront_path");
        		if (makefiles != null && makefiles.length() > 0) makefile_filenames = makefiles.split(",");
        	} catch (IOException e) {
        		e.printStackTrace();
        		System.out.println("Default parameters used.");
        	}

        	// Remote tmp directory name generation
        	tmp_dir = String.format("tmp%d_%s", System.currentTimeMillis()/1000, getTmpDirName(System.getProperty("user.name")));
        	remote_tmp = remote_path + "/" + tmp_dir;
        	remote_tmp = remote_tmp.replaceAll("//", "/");	
        	//System.out.println(remote_tmp);
    	}
    	
	    /**
	     * Return hashcode of a string, adopted to use as a filename part
	     * @param name - some string
	     * @return hashcode
	     * @throws UnsupportedEncodingException 
	     * @throws NoSuchAlgorithmException 
	     */
		private Object getTmpDirName(String name) throws UnsupportedEncodingException, NoSuchAlgorithmException {
			byte[] tmp_dir_name_bytes = name.getBytes("UTF-8");
        	MessageDigest md = MessageDigest.getInstance("SHA-1");
        	tmp_dir_name_bytes = md.digest(tmp_dir_name_bytes);
        	String hash = URLEncoder.encode(tmp_dir_name_bytes.toString(),"UTF-8");
        	hash = hash.replaceAll("[^\\w\\d-_]","");
        	return hash;
		}


		/**
		 * Return property value without space characters.
		 * @param prop Property set.
		 * @param property_name Name of a property.
		 * @return property value without space characters
		 */
		private String updateProperty(Properties prop, String property_name) {
			String property = prop.getProperty(property_name);
			if (property == null) return "";
			property = property.replaceAll("\\s","");
			return property;
		}


		/**
		 * Method for compiling source code on remote machine.
		 * 
		 * 1.Connect to remote machine
		 * 2. Create archive with source files,
		 * 3. Upload archive to temporary directory
		 * 4. Extract source files from archive on remote machine
		 * 5. Execute Make command
		 * 6. Pick up xml files
		 * 7. Download xml files to local source files location
		 * 8. Remove remote temporary directory 
		 * 
		 * @throws IOException
		 * @throws JSchException
		 * @throws SftpException
		 */
		public void makeConnect()  throws IOException, JSchException, SftpException {
			
			// Orion SSH
			Connection conn = new Connection(host,port);

			try {

				// 1.
				// JSch connect and upload
				// get a new session    
				session = new_session();  
				channel=session.openChannel("sftp");
				channel.connect();
				sftp_channel=(ChannelSftp)channel;

				// 2. 3.  
				createArchiveAndUpload();

				// Execute remote commands

				conn.connect();

				boolean isAuthenticated = conn.authenticateWithPassword(user, password);

				if (isAuthenticated == false)
					throw new IOException("Authentication failed.");
				Session sess = conn.openSession();
				// 4. 5.
				sess.execCommand("export PATH=$PATH:"+Ffront_path+" && echo $PATH && cd "+remote_tmp+" && pwd && unzip -o "+archive+" && cd "+noExtension(archive)+" && " + make + " " + make_options);
				InputStream stdout = new StreamGobbler(sess.getStdout());
				InputStream stderr = new StreamGobbler(sess.getStderr());
				BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
				BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));
				// Read and display output
				try {
					while (true)
					{
						String line = br.readLine();
						if (line == null)
							break;
						System.out.println(line);
					}
					while (true)
					{
						String line = stderrReader.readLine();
						if (line == null)
							break;
						System.err.println(line);
					}	    		
				} finally {
					System.out.println("ExitCode: " + sess.getExitStatus());
					System.out.println("Connction:" +conn.getConnectionInfo());
					br.close();
					stderrReader.close();
					sess.close();

				}

				sess = conn.openSession();
				// 6. Pick up xml files
				sess.execCommand("cd "+remote_full_path+" && find -name \"*.xml\"");
				stdout = new StreamGobbler(sess.getStdout());
				br = new BufferedReader(new InputStreamReader(stdout));
				StringBuilder response = new StringBuilder();
				try {
					while (true)
					{
						String line = br.readLine();
						if (line == null)
							break;
						response.append(line+"\n\r");
						//System.out.println(line);
					}	    		    		
				} finally {
					br.close();
					sess.close();	    		
				}	    	

				String[] filenames = response.toString().replaceAll("(\\s\\./)|(^\\./)", "").split("\n");
				System.out.println("\n--------");

				// 7. Download XML files with JSch	        
				for (String filename:filenames) {
					if (filename.length() < 2) continue;
					String remote_filename =remote_full_path+"/"+filename;
					String local_filename = local_path+"/" + filename;
					//System.out.println("Downloading "+remote_filename+" to " + local_filename);
					try {
						File local_file = new File(local_filename);
						if (!local_file.exists()) {
							local_file.getParentFile().mkdirs();
							//local_file.createNewFile();
						}
						sftp_channel.get(remote_filename, local_filename, monitor, mode);	  
					} catch (Exception e) {
						e.printStackTrace(System.err);
					}
				}

				// 8.
				//TODO remove remote temporary directory and archive

			} catch (Exception ex) {
				ex.printStackTrace();
			} finally {
				//orionSSH
				conn.close();

				//JSch
				sftp_channel.exit();
				channel.disconnect();  
				session.disconnect();

				System.out.println("finished.");
				System.exit(0);
			}
	    } 	
	    
	    /**
	     * Archive source files and upload archive to remote directory.
	     * Uses JSch library.
	     * @throws SftpException 
	     * @throws IOException 
	     */
	    private void createArchiveAndUpload() throws SftpException, IOException {
	    	// Check if remote directory exists
	        // Method lstat throws SftpException if path does not exist
	        SftpATTRS attrs = null;
	        try {
	        	attrs = sftp_channel.lstat(remote_tmp);
	        } catch (SftpException e) {
	        	// Path does not exist
	        	// Create new directory
	        	sftp_channel.mkdir(remote_tmp);
	        	attrs = sftp_channel.lstat(remote_tmp);
	        }
	        
	        if (!attrs.isDir()) throw new SftpException(550, "Remote path is not a directory ("+remote_tmp+").");
	        
	        
	        // Append remote path with tmp directory and archive name directory
	        String archive_path = archiveName(local_path);
		    archive = fileName(archive_path);
		    remote_full_path = remote_tmp+"/" +noExtension(archive);
	        
	        SftpProgressMonitor monitor = new MyProgressMonitor();
		    int mode=ChannelSftp.OVERWRITE;
		    sftp_channel.cd(remote_tmp);
		    System.out.println("Remote directory: " + sftp_channel.pwd());
		    
		    // Check if local path is valid
		    File local = new File(local_path);
		    if (!local.exists()) throw new IOException("Source path is not valid (not exists): "+local_path);
		    
		    sftp_channel.lcd(local_path);
		    System.out.println("Local directory: " + sftp_channel.lpwd());
		     
		    // Parse make files
		    if (makefiles.length() > 0) {
		    	makefiles_list = getFilesList("", local_path, makefiles).split(",");
		    	for (String makefile_path : makefiles_list) {
		    		// Save original files
		    		File makefile_org = new File(makefile_path);
		    		File makefile_backup = new File(makefile_path+".origin");
		    		FileUtils.copyFile(makefile_org, makefile_backup);
		    		String s = FileUtils.readFileToString(makefile_org,"UTF-8");

		    		s = replacePlaceholdersInMakefile(s);
		    		saveString2File(s,makefile_path);
		    	}
		    }
		    
		    // Create ZIP archive
		    AppZip appZip = new AppZip(local_path, file_filter);
	    	appZip.zipIt(archive_path);
	    	File file = new File(archive_path);
		    System.out.println("Created zip: " + archive_path);		    
		    if (file.exists()) {
			    FileInputStream file_stream = new FileInputStream(file);
			    sftp_channel.put(file_stream, archive, monitor, mode); 
			    sftp_channel.chgrp(group_id, archive); 
		    }
		    System.out.println("Archive uploaded.");
		    
		    // Delete local archive file
		    file.delete();
		    
		    // Restore original makefiles
		    if (makefiles.length() > 0) {
		    	for (String makefile_path : makefiles_list) {
		    		File makefile_org = new File(makefile_path);
		    		File makefile_backup = new File(makefile_path+".origin");
		    		FileUtils.copyFile(makefile_backup, makefile_org);
		    		makefile_backup.delete();
		    	}
		    }
		}

	    /**
	     * Replace placeholders matching pattern placeholder_pattern
	     * @param s
	     * @return
	     */
	    private String replacePlaceholdersInMakefile(String s) {
	    	Matcher m = placeholder_pattern.matcher(s);
	    	while (m.find()) {
	    		String placeholder_name = m.group(1);
	    		if (placeholder_name.equals("remote_path")) {
	    			s = s.replaceFirst(m.group(0),remote_full_path);
	    		}
	    	}
	    	return s;
		}
	    
	    /**
	     * Saves string to a file
	     * @param s
	     * @throws IOException 
	     */
	    private void saveString2File(String s, String full_path) throws IOException {
	    	File file = new File(full_path);
	    	file.createNewFile();
	    	FileUtils.writeStringToFile(file, s, "UTF-8");
	    }

		/**
	     * Get comma-separated list of full file paths, where file names are in comma-separated list filenames.
	     * Search in path including subdirectories.
	     * @param list	List of files found before
	     * @param path
	     * @param filenames
	     * @return
	     */
		private String getFilesList(String list, String path, String filenames) {
			File directory = new File(path);
			//get all the files from a directory
			File[] fList = directory.listFiles();
			for (File file : fList){
				if (file.isFile()) { 
					String filename = file.getName();
					int index = ArrayUtils.indexOf(makefile_filenames, filename);
					if (index >= 0) {
						if (list.length() > 0) list = list +"," +file.getAbsolutePath();
						else list = file.getAbsolutePath();
					}
				} else if (file.isDirectory()){
					getFilesList(list, file.getAbsolutePath(), filenames);
				}
			}
			return list;
		}

		private com.jcraft.jsch.Session new_session()  throws JSchException {
    		JSch shell = new JSch();
    		com.jcraft.jsch.Session session = shell.getSession(user, host, port);  
    		  
	        // set user password and connect to a channel  
	        session.setUserInfo(new SSHUserInfo(password));  
	        session.connect();  
	        return session;
    	}	
		
		/**
    	 * Create filename for zip archive from path, same as the lowest level directory name in the path.
    	 * @param path	path name
    	 * @return zip archive name
    	 */
    	private String archiveName(String path) {
    		String zip_name;
			zip_name = path + ".zip";		
			return zip_name;
		}
    	
    	/**
    	 * Return only file name given full path to a file
    	 * @param full_path	Full path with filename
    	 * @return	Filename without path
    	 */
    	private String fileName(String full_path) {
			int slash = full_path.lastIndexOf(File.separator);
			String filename = full_path;
			if (slash > 0 && slash < full_path.length()-1) filename = full_path.substring(slash+1);
			else filename = this.default_archive_filename;
    		return filename;
		}
    		
    	
    	/**
    	 * Remove extension from file name
    	 * @param filename	file name (with extension)
    	 * @return file name without extension 
    	 */
    	private String noExtension(String filename) {
			int dot = filename.lastIndexOf(".");
			if (dot > 0) {
				filename = filename.substring(0, dot);
			}
			return filename;
		}
	}	
	
	// this class implements jsch UserInfo interface for passing password to the session  
    static class SSHUserInfo implements UserInfo {  
        private String password;  
  
        SSHUserInfo(String password) {  
            this.password = password;  
        }  
  
        public String getPassphrase() {  
            return null;  
        }  
  
        public String getPassword() {  
            return password;  
        }  
  
        public boolean promptPassword(String arg0) {  
            return true;  
        }  
  
        public boolean promptPassphrase(String arg0) {  
            return true;  
        }  
  
        public boolean promptYesNo(String arg0) {  
            return true;  
        }  
  
        public void showMessage(String arg0) {  
            System.out.println(arg0);  
        }  
    }    

    		   
}

