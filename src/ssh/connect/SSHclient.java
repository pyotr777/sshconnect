package ssh.connect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;  
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
import com.trilead.ssh2.ConnectionInfo;
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
	
	private static final String VERSION ="1.02";
	public static final String CONFIG_FILE = "sshconnect_conf.txt";
	public static String RESOURCE_PATH;  // used to find configuration file 
	
	/**
	 * @param args
	 * @throws JSchException 
	 * @throws SftpException 
	 * @throws NoSuchAlgorithmException 
	 */
	public static void main(String[] args) {
		System.out.println("SSHconnect Ver."+VERSION+"\n\nWelcome to SSH transportation center!\nWe shall make your code at remote location and download the product files.\n");
		if (args.length>0) {
			System.out.print("Command line arguments: ");

			for (String arg: args) {
				System.out.print(" "+arg);
			}
			System.out.println(" ");
		}		
		System.out.print("Initialization start...");
		
		SSHconnect ssh_connection = null;
		try {
			ssh_connection = new SSHconnect(args); 
		} catch (IOException e) {
    		e.printStackTrace();
    		System.err.println("Please chack file path.");
    		System.exit(1);
    	} catch (Exception e) {
    		e.printStackTrace();
    		System.exit(1);
    	}	
		
		System.out.println(" finished.");
		
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
	 * Set ssh_connection properties based on args.
	 * 
	 * @param args make command, make options, make file, local path
	 * @param ssh_connection
	 * @throws IOException 
	 */
	static void setSSHconnectParameters(String[] args, SSHconnect ssh_connection) throws IOException, IllegalArgumentException  {
		// Set parameters from command line

		for (int i = 0; i < args.length; i++ ) {
			if (args[i].indexOf("-")==0) {
				if (args[i].equals("-ap")) {
					ssh_connection.add_path = trimApostrophe(args[i+1]);
					i++;
				} 
				else if (args[i].equals("-h")) {
					ssh_connection.host = args[i+1];
					i++;
				} 
				else if (args[i].equals("-p")) {
					ssh_connection.port = Integer.parseInt(args[i+1]);
					i++;
				} 
				else if (args[i].equals("-u")) {
					ssh_connection.user = args[i+1];
					i++;
				} 
				else if (args[i].equals("-pw")) {
					ssh_connection.password = args[i+1];
					i++;
				} 
				else if (args[i].equals("-k")) {
					ssh_connection.key = trimApostrophe(args[i+1]);
					i++;
				} 
				else if (args[i].equals("-ph")) {
					ssh_connection.passphrase = args[i+1];
					i++;
				} 
				else if (args[i].equals("-rp")) {
					ssh_connection.remote_path = trimApostrophe(args[i+1]);
					i++;
				} 
				else if (args[i].equals("-m")) {
					ssh_connection.build_command = trimApostrophe(args[i+1]); // remove single quotes around argument
					i++;
				} 
				else if (args[i].equals("-lp")) {
					ssh_connection.local_path = trimApostrophe(args[i+1]);
					File lp_file = new File(ssh_connection.local_path);
					ssh_connection.local_path = lp_file.getCanonicalPath();
					i++;
					if (!checkPath(ssh_connection.local_path, true)) {
						System.err.println("Input parameter "+args[i]+" ("+ssh_connection.local_path+") does not exist or is not a directory. Parameter after -lp option must be local directory path where source files are located.");
						throw new IllegalArgumentException();
					}
				} 
				else if (args[i].equals("-ff")) {
					ssh_connection.file_filter = args[i+1] +",*.origin";
					i++;
				} 
				else if (args[i].equals("-pf")) {
					ssh_connection.preprocess_files = trimApostrophe(args[i+1]);
					i++;
				} 
			}
		}
	}
	
	
	/**
	 * Remove single quotes around argument
	 * @param arg
	 * @return
	 */
	private static String trimApostrophe(String arg) {
		arg = arg.trim();
		int f_pos = arg.indexOf("'");
		int l_pos = arg.lastIndexOf("'");
		if (f_pos == 0 && l_pos == arg.length()-1) arg = arg.substring(1, arg.length()-1);
		return arg;
	}

	/**
	 * Convert absolute path abs_path to relative to base_path.
	 * abs_path must be subdirectory of base_path. 
	 * 
	 * @param base_path
	 * @param abs_path
	 * @return relative path
	 */
	 static String getRelativePath(String base_path, String abs_path) throws IOException {
		 // Normalize the paths
        String normalized_abs_path = new File(abs_path).getCanonicalPath();
        String normalized_base_path = new File(base_path).getCanonicalPath();
        if (normalized_base_path.charAt(normalized_base_path.length()-1)!= File.separatorChar) normalized_base_path = normalized_base_path + File.separator;
        
        //System.out.println(normalized_abs_path + " vs " + normalized_base_path);
        
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
	 * True if path exists, and is of correct type (folder/file) 
	 * @param path
	 * @param folder - true if we need path to be a directory, false - otherwise
	 */
	private static boolean checkPath(String path, boolean folder) {
		File f=null;
		try {
			f = new File(path.replaceAll("'", ""));
		} catch (NullPointerException e) {
			return false;
		}
		boolean is_folder = f.isDirectory();
		if (folder)	return is_folder;
		else return (f.exists() && !is_folder);
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
	    String add_path = ""; // path to atool and F_Front
	    String archive = "";
	    String archive_path = "";
	    
	    // files to look for replacement placeholders
	    String preprocess_files = "";  // priority value - from configuration file 
	    // command to execute
	    String build_command = ""; // priority value - from command line 3rd argument (args[2])
	    
	    static private final Pattern placeholder_pattern = Pattern.compile("#\\[([\\w\\d\\-_]*)\\]");
	    private String default_archive_filename = "archive.zip"; 
	    private AppTar archiver;  // Used for operations with archive
	    
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
    	String[] processfiles_list = null;
	    
	    public SSHconnect(String args[])  throws IOException, IllegalArgumentException, NoSuchAlgorithmException, InvalidPreferencesFormatException   {	
	    	
	    	// Read parameters from configuration file
	    	Properties prop = new Properties();
	    	
	    	try {
	    		String properties_string = FileUtils.readFileToString(new File(CONFIG_FILE),"UTF-8");
	    		prop.load(new StringReader(properties_string.replace("\\","\\\\")));  // For Windows OS paths
	    		//prop.load(new FileInputStream(CONFIG_FILE));
	    	} catch (FileNotFoundException e) {
	    		try {	    			
	    			String path = SSHclient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
	    			RESOURCE_PATH = URLDecoder.decode(path, "UTF-8");
	    			RESOURCE_PATH = RESOURCE_PATH.substring(0,RESOURCE_PATH.lastIndexOf(File.separator, RESOURCE_PATH.length()-2)+1);
	    			System.out.println("Looking for config file in " +RESOURCE_PATH +CONFIG_FILE );
	    			String properties_string = FileUtils.readFileToString(new File(RESOURCE_PATH +CONFIG_FILE),"UTF-8");
		    		prop.load(new StringReader(properties_string.replace("\\","\\\\")));
	    			//prop.load(new FileInputStream(RESOURCE_PATH + CONFIG_FILE));
	    		} catch (FileNotFoundException e2) {
	    			System.out.println("Configuration file "+CONFIG_FILE+" not found. ");
	    			setDefaults(prop); // initialize with sensible default values
	    		} catch (URISyntaxException e1) {
	    			System.out.println("Configuration file "+CONFIG_FILE+" not found. ");
	    			setDefaults(prop);
				}
	    	}
	    	
	    	add_path = updateProperty(prop,"add_path");
	    	host = updateProperty(prop, "host");
	    	try {
	    		port = Integer.parseInt(updateProperty(prop, "port"));
	    	} catch (NumberFormatException e) {
	    		System.err.println("'port' propery in "+CONFIG_FILE+" not recognized: "+port+". Default port 22 is used.");
	    		port = 22;	    		
	    	}	    	
	    	user = updateProperty(prop, "user");
	    	password = updateProperty(prop, "password"); // If password == "" authenticate with key.
	    	key = updateProperty(prop,"key");
	    	passphrase = updateProperty(prop,"passphrase");
	    	remote_path = updateProperty(prop, "remote_path");	    	
	    	build_command = updateProperty(prop, "build_command");	    	
	    	local_path = updateProperty(prop, "local_path");  	    	
	    	String ff = updateProperty(prop, "file_filter");
	    	// *.origin - reserved for original copies of edited make files.
	    	if (ff != null && ff.length() > 1) file_filter = ff +",*.origin";
	    	
	    	// Files to look into for replacement pattern 
	    	preprocess_files = updateProperty(prop, "preprocess_files");
	    	
	    	// set SSHconnect parameters from command-line arguments
			try { 
				setSSHconnectParameters(args, this);
			}
			catch (Exception e) {
				e.printStackTrace();
				System.exit(1);
			}
	    	// Check important parameters
			if (user.length() < 1) throw new InvalidPreferencesFormatException("'user' property not set. Set it in "+CONFIG_FILE+" or with command-line option -u. This is a required propery. Set ssh user name for connecting to remote server.");
			if (remote_path.length() < 1) throw new InvalidPreferencesFormatException("'remote_path' property not set. Set it in in "+CONFIG_FILE+" or with command-line option -rp. This is a required propery. Set remote path on server to create temporary directories.");
	    	
			// Remote tmp directory name generation
			try {
				this.tmp_dir = String.format("tmp%d_%s", System.currentTimeMillis()/1000, this.getTmpDirName(System.getProperty("user.name")));
			} catch (UnsupportedEncodingException e1) {
				e1.printStackTrace();
			} catch (NoSuchAlgorithmException e1) {
				e1.printStackTrace();
			}
			this.remote_tmp = this.remote_path + "/" + this.tmp_dir;
			this.remote_tmp = this.remote_tmp.replaceAll("//", "/");	
			
	    	archiver = new AppTar(local_path, file_filter);
	    	// Append remote path with tmp directory and archive name directory	        
	        archive_path = this.archiver.archiveName(local_path);  
		    archive = fileName(archive_path);
		    remote_full_path = remote_tmp+"/" +noExtension(archive);
	            		
    	}
    	
	    /**
	     * Initializa properties with sensible default values
	     * if configuration file not found.
	     * @param prop
	     */
	    private void setDefaults(Properties prop) {
			prop.setProperty("port", "22");
			prop.setProperty("build_command", "make");
			prop.setProperty("file_filter", "*.zip,*.tar,.DS*,*.ksx");			
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
			if (property.indexOf(",")>=0) property = property.replaceAll("\\s","");
			else property = property.trim();
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

			// Orion connect
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
				String path_command = "";
				if (add_path.length() > 0) path_command = "PATH=$PATH:'"+add_path+"' && ";
				
				executeOrionCommands(orion_conn, path_command+"echo path=$PATH && cd '"+remote_tmp+"'  && pwd && tar -xvf '"+archive+"'", true,true,true); 
				// rename new Folder to match archive name (with replaced spaces)
				if (archiver.replacedSpaces()) executeOrionCommands(orion_conn, "cd '"+remote_tmp+"'  && pwd && mv '"+archiver.getOriginalFolder()+ "' '"+archiver.getNewFolder() +"'",true,true,true);
				executeOrionCommands(orion_conn, path_command+ "cd '"+remote_full_path+ "' && echo $PATH && which atool && " + build_command,true,true,true);
				
				// 6. Pick up xml files
				String str_response = executeOrionCommands(orion_conn, "cd '"+remote_full_path+"' && find -name \"*.xml\"",true,false,true);
				
				// 7. Download XML files with JSch	        
				String[] filenames = str_response.replaceAll("(\\s\\./)|(^\\./)", "").split("\n");		
				System.out.print("Downloading "+(filenames.length-1) +" products to "+local_path+". ");
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
			} catch (Exception e) {
				e.printStackTrace();
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
				}
			}
			
			try {
				if (isAuthenticated == false) {
					orion_conn.connect();
					System.out.print(" Authenticating with key... ");
					File key_file = new File(key);
					Boolean exists = key_file.exists();
					if (exists) isAuthenticated = orion_conn.authenticateWithPublicKey(user, key_file, "");
					if (!isAuthenticated) {
						ConnectionInfo cinfo = orion_conn.getConnectionInfo();
						System.out.println(cinfo.keyExchangeAlgorithm);
					}
					use_key_authentication = true;
				}
			}
			catch (IOException e) {
				e.printStackTrace();
				orion_conn.close();
				isAuthenticated = false;
			}

			if (isAuthenticated == false) {
				System.out.println(" Failed.");				
				throw new IOException("Authentication with key ("+key+") and passphrase ("+passphrase+") on server "+host+":" +port+" failed.");
			}

			return orion_conn;
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
			if (verbose && (display_stdout || display_stderr)) System.out.println("Command session report:\n----------------------------------");
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
	        boolean exists = true;  // false - remote tmp directory doesn't exist (ordinary situation)
	        int tmp_counter = 1;  // used to append to temporary directory name in case remote directory already exists before we created it.
	        while (exists) {
	        	try {
	        		attrs = sftp_channel.lstat(remote_tmp);
	        	} catch (SftpException e) {
	        		// Path does not exist
	        		// Create new directory
	        		exists = false;
	        		sftp_channel.mkdir(remote_tmp);
	        		attrs = sftp_channel.lstat(remote_tmp);
	        	}
	        	if (exists) {
	        		remote_tmp = remote_tmp +"_"+ tmp_counter;
	        		tmp_counter++;
	        	}
	        }
	        
	        if (!attrs.isDir()) throw new SftpException(550, "Remote path is not a directory ("+remote_tmp+").");
	        
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
		    if (preprocess_files.length() > 0) {
		    	System.out.println("Preparing makefiles for upload to server.");
		    	processfiles_list = getFilesList("", local_path, preprocess_files).split(";");
		    	for (String makefile_path : processfiles_list) {
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
		    		File parsed_makefile = saveString2File(s,makefile_path);
		    		// Copy modification time
		    		long mdate = makefile_backup.lastModified();
		    		parsed_makefile.setLastModified(mdate);
		    		
		    		System.out.println(" finished."); 
		    	}
		    	System.out.println("Makefiles are ready.");
		    }
		    
		    // Create  archive
		    System.out.println("Packing files for transportation.");
		    
	    	File archive_file = new File(archive_path);
	    	archiver.tarIt(archive_file);
		    System.out.println("Uploading " + archive_path);		    
		    if (archive_file.exists()) {
			    FileInputStream file_stream = new FileInputStream(archive_file);
			    sftp_channel.put(file_stream, archive, monitor, mode); 
		    }
		    System.out.println("Archive uploaded. Cleaning up.");
		    
		    // Delete local archive file
		    archive_file.delete();
		    
		    // Restore original makefiles
		    if (preprocess_files.length() > 0) {
		    	for (String procfile_path : processfiles_list) {
		    		File file_org = new File(procfile_path);
		    		File file_backup = new File(procfile_path+".origin");
		    		System.out.println("Restore original "+file_org);
		    		FileUtils.copyFile(file_backup, file_org);
		    		file_backup.delete();
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
	    			//System.out.print(" inserted " +remote_full_path);
	    		}
	    	}
	    	return new_s;
		}
	    
	    /**
	     * Saves string to a file
	     * @param s
	     * @return new File instance
	     * @throws IOException 
	     */
	    private File saveString2File(String s, String full_path) throws IOException {
	    	File file = new File(full_path);
	    	file.createNewFile();
	    	FileUtils.writeStringToFile(file, s, "UTF-8");
	    	return file;
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
					if (password != null && password.length() > 0) {
						System.out.print(" Password authentication failed. ");
						System.err.println("Failed password authintication for "+user+":"+password+"@"+host+":"+port);
					}
				}
			}
			
			if (need_key_authentication) {		        	
				if (key.length() > 0) {	     
					System.out.print(" Trying key authentication with "+key+" ... ");
					File key_file = new File(key);
					if (key_file.exists() == false) {
						System.err.println("Key file "+key+" not found.");
						throw new JSchException("Key authentication failed.");
					}
					shell.addIdentity(key_file.getAbsolutePath(), passphrase);
					
					session = shell.getSession(user, host, port);
					session.setUserInfo(new SSHUserInfo());  
					session.setConfig("StrictHostKeyChecking", "no");
					try {
						session.connect();
					} catch (JSchException je) {						
						System.err.println("\nException on connection attempt to "+session.getHost()+":"+session.getPort()+". Check your user name ("+session.getUserName()+"), key ("+key_file.getAbsolutePath()+") and passphrase ("+passphrase+") settings.");						
						throw je;
					}
					use_key_authentication = true;
				} else {
					try {
						session = shell.getSession(user, host, port);
						session.connect();
					} catch(Exception e) {
						throw new JSchException("\nConnection failed. Check your user name ("+user+"), key ("+key+") and passphrase ("+passphrase+") settings.");
					}
				}
			}
			return session;
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

