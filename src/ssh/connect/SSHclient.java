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
import java.util.Properties;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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
 * 
 * Utility for remote parsing Fortran programs with atool.
 *  
 * @author Peter Bryzgalov
 *
 */

public class SSHclient {
	
	private static String version="0.25";
	public static String conf_filename = "sshconnect_conf.txt";
	
	/**
	 * @param args
	 * @throws JSchException 
	 * @throws SftpException 
	 * @throws NoSuchAlgorithmException 
	 */
	public static void main(String[] args) {
		System.out.println("SSHconnect Ver."+version+"\n\nWelcome to SSH transportation center!\nWe shall make your code at remote location and download the product files.\n");
		if (args.length>0) {
			System.out.print("Command line arguments: ");

			for (String arg: args) {
				System.out.print(" "+arg);
			}
			System.out.println(" ");
		}		
		SSHconnect ssh_connection = null;
		try {
			ssh_connection = new SSHconnect(); // read parameters from conf_file
		} catch (IOException e) {
    		e.printStackTrace();
    		System.err.println("Couldn't read from configuration file: config.txt must be in the same directory with SSHconnect.jar.\rRequired parameters must be defined in configuration file "+conf_filename+":\nhost\nuser\npassword\nremote_path.");
    		return;
    	} catch (Exception e) {
    		e.printStackTrace();
    		return;
    	}
		// Set parameters from command line
		// setting local_path value from command line 4th arguments 
		if (args.length > 3) {
			ssh_connection.local_path = args[3]; 
			if (!checkPath(ssh_connection.local_path, true)) {
				System.err.println("Input parameter "+args[3]+" is not a directory. 4th command line parameter must be local path where source files are located.");
				return;
			}
		}
		
		// setting makefile_execute value from command line 3rd argument 
		if (args.length  > 2) {
			ssh_connection.makefile_execute = args[2];
			if (!checkPath(ssh_connection.makefile_execute, false)) {
				System.err.println("Input parameter "+args[2]+" is not a file. 3rd command line parameter must be local path to makefile.");
				return;
			}
		}
		// If makefiles not defined in config.txt, use value from command line argument
		if (ssh_connection.makefiles_process.length() ==0 && ssh_connection.makefile_execute.length() > 0) {
			ssh_connection.makefiles_process = ssh_connection.makefile_execute;
			System.err.println("'makefiles' property not found in config.txt. Using command line parameter for list of makfiles to look into for replacement placeholders: "+ ssh_connection.makefiles_process);
		}
		
		// setting make_options from command line 2nd parameter
		if (args.length > 1) {
			ssh_connection.make_options = args[1];
		}
		
		// setting make command from command line 1st parameter
		if (args.length > 0) {
			ssh_connection.make = args[0];
			if (!ssh_connection.make.equals("make")) {
				// If make command is not "make", but some file
				// Convert absolute path (produced if used K-scope "refer" button in New Project dialog) to relative to local_path.
				// make path must be subdirectory of local_path.
				try {
					if (PathDetector.isAbsolutePath(ssh_connection.make)) ssh_connection.make = "./" +getRelativePath(ssh_connection.local_path, ssh_connection.make);
					else ssh_connection.make = "./"+FilenameUtils.normalize(ssh_connection.make); // remove extra dots
					System.out.println("Make command: "+ ssh_connection.make);
				} catch (IOException e) {
					System.err.println("Make file path is not a subdirectory of local_path.\nmake:"+args[0]+ "\nlocal_path: "+ssh_connection.local_path);
					e.printStackTrace();
					System.exit(1);
				}
			}
		}
		System.out.println(" finished.");
		
		//detectPaths(basic_connection.local_path); //  on hold.
		
		try {
			ssh_connection.makeConnect();
		} catch (JSchException e) {
			e.printStackTrace();
			System.err.println("Could not connect (JSch) to "+ssh_connection.user+"@" + ssh_connection.host);
			System.exit(1);
		} catch (SftpException e) {
			e.printStackTrace();
			System.err.println("Could not connect (sftp) to "+ssh_connection.user+"@" + ssh_connection.host);
			System.exit(1);
		} catch (Exception e) {
			System.err.println("Could not connect to "+ssh_connection.user+"@" + ssh_connection.host);
			e.printStackTrace();
			System.exit(1);
		}
		System.exit(0);
	}
	
	/**
	 * Convert absolute path abs_path to relative to base_path.
	 * abs_path must be subdirectory of base_path. 
	 * 
	 * @param base_path
	 * @param abs_path
	 * @return relative path
	 */
	private static String getRelativePath(String base_path, String abs_path) throws IOException {
		 // Normalize the paths
        String normalized_abs_path = new File(abs_path).getCanonicalPath();
        String normalized_base_path = new File(base_path).getCanonicalPath();
        if (normalized_base_path.charAt(normalized_base_path.length()-1)!= File.separatorChar) normalized_base_path = normalized_base_path + File.separator;
        
        String relative_path = "";
        
        if (normalized_abs_path.indexOf(normalized_base_path) == 0) {
        	relative_path = normalized_abs_path.substring(normalized_base_path.length());
        } else if (normalized_abs_path.indexOf(normalized_base_path) > 0) {
        	System.err.println("Something wrong with these paths: \nbase: " + base_path + "\nabs:  "+abs_path);
        	throw new IOException("Couldn't process these paths:\nnorm_base: "+normalized_base_path+"\nnorm_abs:  "+normalized_abs_path);
        }
        
		return relative_path;
	}


	/**
	 * Check if path exists.
	 * True if path exists, otherwise - false 
	 * @param path
	 * @param folder - true if we need path to be a directory, false - otherwise
	 */
	private static boolean checkPath(String path, boolean folder) {
		File f=null;
		try {
			f = new File(path);
		} catch (NullPointerException e) {
			return false;
		}
		if (folder)	return f.isDirectory();
		else return !f.isDirectory();
	}

	

	
	static class SSHconnect {
	
		
		// Initializing parameters with default values
	    String host = ""; // host IP  
	    String user = ""; // username for SSH connection  
	    String password = ""; // password for SSH connection 
	    int port; // default SSH port
	    String key="",passphrase="";
	    boolean use_key_authentication = false; // If password authentication for JSch fails, do not try it again with Orion. Force key authentication.
	    
		// local project folder. Must contain Makefile and all files necessary for building.  
	    String local_path = ""; 
	    String remote_path = ""; 
	    String remote_full_path = ""; // path including temporary directory and archive name without extension
	    String archive = "";
	    
	    // Two kinds of makefiles parameters:
	    // makefiles to look for replacement placeholders
	    String makefiles_process = "";  // priority value - from config.txt 
	    // makefile to execute
	    String makefile_execute = ""; // priority value - from command line 3rd argument (args[2])
	    
	    String make = "";
	    String make_options = "";
	    static private final Pattern placeholder_pattern = Pattern.compile("#\\[([\\w\\d\\-_]*)\\]");
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
    	String[] makefiles_list = null;
	    
	    
	    public SSHconnect()  throws IOException, IllegalArgumentException, NoSuchAlgorithmException, InvalidPreferencesFormatException   {	
	    	
	    	// Read parameters from configuration file
    		Properties prop = new Properties();
    		System.out.print("Initialization start...");
    		prop.load(new FileInputStream(conf_filename));
    		
    		make = updateProperty(prop,"make");
    		make_options = prop.getProperty("make_options"); // no need to remove spaces
    		if (make_options == null) make_options = "";
    		local_path = updateProperty(prop, "local_path");  
    		
    		host = updateProperty(prop, "host");
    		
    		user = updateProperty(prop, "user");
    		if (user.length() < 1) throw new InvalidPreferencesFormatException("'user' property not found in config.txt. This is a required propery. Set ssh user name for connecting to remote server.");
    		
    		password = updateProperty(prop, "password"); // If password == "" authenticate with key.
    		
    		key = updateProperty(prop,"key");
    		passphrase = updateProperty(prop,"passphrase");
    		
    		try {
    			port = Integer.parseInt(updateProperty(prop, "port"));
    		} catch (NumberFormatException e) {
    			port = 22;
    			System.err.println("'port' propery not found or not a number in config.txt. Default port 22 is used.");
    		}
    		  			
    		remote_path = updateProperty(prop, "remote_path");     
    		if (remote_path.length() < 1) throw new InvalidPreferencesFormatException("'remote_path' property not found in config.txt. This is a required propery. Set remote path on server to create temporary directories.");
    		
    		String ff = updateProperty(prop, "file_filter");
    		// *.origin - reserved for original copies of edited make files.
    		if (ff != null && ff.length() > 1) file_filter = ff +",*.origin";
    		else System.err.println("'file_filter' property not found in config.txt. Default is used: "+ file_filter);
    		
    		// Makefiles to look into for replacement pattern 
    		makefiles_process = updateProperty(prop, "makefiles");
    		
    		// Remote tmp directory name generation
    		tmp_dir = String.format("tmp%d_%s", System.currentTimeMillis()/1000, getTmpDirName(System.getProperty("user.name")));
    		remote_tmp = remote_path + "/" + tmp_dir;
    		remote_tmp = remote_tmp.replaceAll("//", "/");	    		
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
		 * 1. Connect to remote machine
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
		 * @throws NullPointerException
		 */
		public void makeConnect()  throws IOException, JSchException, SftpException, NullPointerException, Exception {
			// 1.
			// JSch connect
			// get a new session    
			System.out.print("Opening SFTP channel...");
			session = new_session();
			System.out.println(" Authenticated.");

			System.out.print("Creating connection to "+host+":"+port+"...");
			Connection orion_conn = newOrionConnection();
			System.out.println(" Authenticated.");
			
			try {
				channel=session.openChannel("sftp");
				channel.connect();
				sftp_channel=(ChannelSftp)channel;
				System.out.println(" success.");
				try { Thread.sleep(500); } catch (Exception ee) { }
				
				//PathDetector pd = new PathDetector(local_path,remote_path,makefiles,null);
				//pd.detectPaths();
				//if (true) return;
				
				// 2. Create archive with source files,
				// 3. Upload archive to temporary directory
				createArchiveAndUpload();

				// 4. Extract source files from archive on remote machine
				// 5. Execute Make command
				executeOrionCommands(orion_conn, "echo $PATH && cd "+remote_tmp+"  && unzip -o "+archive, true,true,true);
				if (!make.equals("make"))  executeOrionCommands(orion_conn, "cd "+remote_full_path+ " && chmod u+x "+make, true,true,false);  // Add executable permission if make command is not "make".
				executeOrionCommands(orion_conn,  "cd "+remote_full_path+ " && " + make + " " + make_options+ " "+getRelativePathFromTop(makefile_execute,local_path),true,true,true);
				
				// 6. Pick up xml files
				String str_response = executeOrionCommands(orion_conn, "cd "+remote_full_path+" && find -name \"*.xml\"",true,false,true);
				
				// 7. Download XML files with JSch	        
				String[] filenames = str_response.replaceAll("(\\s\\./)|(^\\./)", "").split("\n");		
				System.out.print("Downloading "+filenames.length+" products. ");
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
				System.out.println("Download finished.");
				
				// 8.
				//Remove remote temporary directory and archive				
				System.out.println("Cleaning remote location: " + tmp_dir);
				executeOrionCommands(orion_conn, "cd "+remote_path+" && rm -r " + tmp_dir,false,false,false);				
			} finally {
				System.out.println("Closing connections.");
				//orionSSH
				orion_conn.close();

				//JSch
				sftp_channel.exit();
				channel.disconnect();  
				session.disconnect();

				System.out.println("All tasks complete.");
			}
	    }

		/**
		 * @return
		 * @throws IOException
		 */
		private Connection newOrionConnection() throws IOException {
			// Orion SSH connection
			Connection orion_conn = new Connection(host,port);
			
			boolean isAuthenticated = false;
			if (!use_key_authentication) {
				orion_conn.connect();
				try {
					isAuthenticated = orion_conn.authenticateWithPassword(user, password);
				}
				catch (IOException e) {				
					isAuthenticated = false;										
				}

				if (isAuthenticated == false) {
					System.out.print(" Password authentication ("+ user+":" + password+ ") failed.");
					orion_conn.close();
					if (key.length() < 1) {
						// Cannot use key authentication. Password authentication failed. 
						throw new IOException("Could not connect to "+host);
					}
				}
			}
			
			try {
				if (isAuthenticated == false) {
					orion_conn.connect();
					System.out.print(" Authenticating with key... ");
					isAuthenticated = orion_conn.authenticateWithPublicKey(user, new File(key), passphrase);
					use_key_authentication = true;
				}
			}
			catch (IOException e) {
				orion_conn.close();
				isAuthenticated = false;
			}

			if (isAuthenticated == false) {
				System.out.println(" Failed.");
				throw new IOException("Authentication with key "+key+"("+passphrase+") on server "+host+":" +port+" failed.");
			}

			return orion_conn;
		}

		/**
		 * Return relative path reachable from toppath
		 *  
		 * @param absolute_path
		 * @param toppath
		 * @return
		 */
		private String getRelativePathFromTop(String absolute,String toppath) {
			if (absolute.length() == 0) return "";
			File top = new File(toppath);
			File abs = new File(absolute);
			String relative = abs.getAbsolutePath().replaceFirst(top.getAbsolutePath(), "");
			if (relative.substring(0, 1).equalsIgnoreCase(File.separator)) relative = relative.substring(1);
			return relative;
		}

		/**
		 * Execute remote commands over orionSSH connection
		 * @param orion_conn Connection substance
		 * @param commands Commands to execute
		 * @param display_stdout Set to true to display remote stdout
		 * @param display_stderr Set to true to display remote stderr
		 * @param verbose Set to true to display comments in stdout.
		 * @throws IOException
		 */
		private String executeOrionCommands(Connection orion_conn, String commands, boolean display_stdout, boolean display_stderr,boolean verbose) throws IOException {
			// Execute remote commands

			if (verbose) System.out.println("Opening command session.");
			Session sess = orion_conn.openSession();
			if (verbose) System.out.println("Command session start.\nExecuting: "+ commands.replaceAll("&&", ", "));
			sess.execCommand(commands);
			InputStream stdout = new StreamGobbler(sess.getStdout());
			InputStream stderr = new StreamGobbler(sess.getStderr());
			BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
			StringBuilder response = new StringBuilder();
			// Read and display output
			if (verbose && (display_stdout || display_stderr)) System.out.println("Displaying command session report:\n----------------------------------");
			if (display_stderr) {
				(new stderrThread(stderr)).start(); // Display stderr in new thread
			}
			try {
				while (true)
				{
					String line = br.readLine();
					if (line == null)
						break;
					if (display_stdout) System.out.println(line);
					response.append(line+"\n\r");
				}    		
			} finally 
			{
				//System.out.println("ExitCode: " + sess.getExitStatus());
				//System.out.println("Connction:" +orion_conn.getConnectionInfo());
				br.close();
				sess.close();
				if (verbose) {
					if (display_stdout || display_stderr) System.out.println("----------------------------------");
					System.out.println("Session closed.");
				}
			}
			return response.toString();
		} 	
	    
	    /**
	     * Archive source files and upload archive to remote directory.
	     * Uses JSch library.
	     * @throws SftpException 
	     * @throws IOException 
	     */
	    private void createArchiveAndUpload() throws SftpException, IOException, NullPointerException {
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
		    System.out.println("SFTP channel exit set at: " + sftp_channel.pwd());
		    
		    // Check if local path is valid
		    try {
		    	File local = new File(local_path);
		    	if (!local.exists()) throw new IOException("Source path is does not exist: "+local_path);
		    } catch (NullPointerException e) {
		    	throw new IOException("Source path is not valid: "+local_path);
		    }
		    sftp_channel.lcd(local_path);
		    System.out.println("SFTP channel entrance set at: " + sftp_channel.lpwd());
		     
		    // Parse make files
		    if (makefiles_process.length() > 0) {
		    	System.out.println("Preparing makefiles for upload to server.");
		    	makefiles_list = getFilesList("", local_path, makefiles_process).split(",");
		    	for (String makefile_path : makefiles_list) {
		    		// Save original files
		    		File makefile_org = new File(makefile_path);
		    		File makefile_backup = new File(makefile_path+".origin");
		    		System.out.println("Backing up "+makefile_org);
		    		FileUtils.copyFile(makefile_org, makefile_backup);
		    		System.out.print("Replacing placeholders: ");  
		    		String s = FileUtils.readFileToString(makefile_org,"UTF-8");
		    		try {
		    			String s2 = replacePlaceholdersInMakefile(s);
		    			if (s2.length() > 0) s = s2;
		    		} catch (Exception e) {
		    			e.printStackTrace();
		    			throw new NullPointerException();
		    		}
		    		saveString2File(s,makefile_path);
		    		System.out.println(" finished."); 
		    	}
		    	System.out.println("Makefiles are ready.");
		    }
		    
		    // Create ZIP archive
		    System.out.println("Packing files for transportation.");
		    AppZip appZip = new AppZip(local_path, file_filter);
	    	File zip_file = new File(archive_path);
	    	appZip.zipIt(zip_file);
		    System.out.println("Uploading " + archive_path);		    
		    if (zip_file.exists()) {
			    FileInputStream file_stream = new FileInputStream(zip_file);
			    sftp_channel.put(file_stream, archive, monitor, mode); 
		    }
		    System.out.println("Archive uploaded. Cleaning up.");
		    
		    // Delete local archive file
		    zip_file.delete();
		    
		    // Restore original makefiles
		    if (makefiles_process.length() > 0) {
		    	for (String makefile_path : makefiles_list) {
		    		File makefile_org = new File(makefile_path);
		    		File makefile_backup = new File(makefile_path+".origin");
		    		System.out.println("Restore original "+makefile_org);
		    		FileUtils.copyFile(makefile_backup, makefile_org);
		    		makefile_backup.delete();
		    	}
		    }
		    System.out.println(" ");
		}

	    /**
	     * Replace placeholders matching pattern placeholder_pattern.
	     * If pattern not found, returns empty string.
	     * @param s
	     * @return
	     */
	    private String replacePlaceholdersInMakefile(String s) throws PatternSyntaxException, IllegalStateException, IndexOutOfBoundsException {
	    	Matcher m = placeholder_pattern.matcher(s);
	    	String new_s = "";
	    	while (m.find()) {
	    		String placeholder_name = m.group(1);
	    		if (placeholder_name.equals("remote_path")) {
	    			new_s = m.replaceAll(remote_full_path);
	    			System.out.print(" inserted " +remote_full_path);
	    		}
	    	}
	    	return new_s;
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
	     * @param path	directory to search
	     * @param filenames File names to include in the list
	     * @return
	     */
		private String getFilesList(String list, String path, String filenames) throws NullPointerException {
			File directory = new File(path);
			//get all the files from a directory
			File[] fList = directory.listFiles();
			for (File file : fList){
				if (file.isFile()) { 
					String filename = file.getName();
					int index = ArrayUtils.indexOf(filenames.split(","), filename);
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
			boolean need_key_authentication = false;
			
			if (password.length() < 1) { 
				System.out.print(" no password provided. ");
				need_key_authentication = true;
			} 
			else 
			{
				// set user password and connect to a channel  
				session.setUserInfo(new SSHUserInfo(password));  
				try {
					session.connect();
				} catch (JSchException e) {
					need_key_authentication = true;
					System.out.print(" Password authentication failed. ");
				}
			}
			
			if (need_key_authentication) {		        	
				if (key.length() > 0) {	     
					System.out.print(" Trying key authentication... ");
					shell.addIdentity(new File(key).getAbsolutePath(), passphrase);
					session = shell.getSession(user, host, port);
					session.setUserInfo(new SSHUserInfo());  
					try {
						session.connect();
					} catch (JSchException je) {
						System.err.println("Cannot pass key authentication. Check your passphrase and key settings in configuration file.");
						throw je;
					}
					use_key_authentication = true;
				} else {
					throw new JSchException("Password authentication failed. No key provided.");
				}
			}
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
			else {
				System.err.println("Couldn't extract archive name from " + full_path+". Use default name:" + this.default_archive_filename);
				filename = this.default_archive_filename;
			}
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
        
        SSHUserInfo() {  
              
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

