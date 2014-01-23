package ssh.connect;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;  
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.prefs.InvalidPreferencesFormatException;
import org.apache.commons.io.FileUtils;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jcraft.jsch.StreamGobbler;
import com.jcraft.jsch.UserInfo;
import com.jcraft.jsch.agentproxy.*;


/**
 * 
 * Utility for remote parsing Fortran programs with atool.
 *  
 * @author Peter Bryzgalov
 *
 */

public class SSHclient {
	
	private static final String VERSION ="1.14docker";
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
	    boolean remove_remote_path = false; // Set to true if path didn't exist and we created it
	    String add_path = ""; // path to atool and F_Front
	    String archive = "";
	    String archive_path = "";
	    
	    // command to execute
	    String build_command = ""; // priority value - from command line 3rd argument (args[2])
	    
	    private String default_archive_filename = "archive.zip"; 
	    private AppTar archiver;  // Used for operations with archive
	    
	    // Remote temporary folder name
	    String tmp_dir = "";
	    
	    // Temporary directory: remote_tmp
	    //String remote_tmp = ""; 
	    	    
	    // Source file filter.
	    // Exclude selected file types.
	    String file_filter = ".*,*.tar,*.html,*.zip,*.jpg.*.orgin";
	    	    	    
	    
	    // JSch parameters initialization
	    com.jcraft.jsch.Session session = null;
	    int mode=ChannelSftp.OVERWRITE;
    	SftpProgressMonitor monitor = new MyProgressMonitor();
    	Channel channel = null;
    	ChannelSftp sftp_channel = null;
    	String[] processfiles_list = null;
    	
    	Connector con = null;
	    
	    public SSHconnect(String args[])  throws IOException, IllegalArgumentException, NoSuchAlgorithmException, InvalidPreferencesFormatException   {	
	    	
	    	// Read parameters from configuration file
	    	Properties prop = new Properties();
	    	
	    	try {
	    		String properties_string = FileUtils.readFileToString(new File(CONFIG_FILE),"UTF-8");
	    		prop.load(new StringReader(properties_string.replace("\\","\\\\")));  // For Windows OS paths
	    	} catch (FileNotFoundException e) {
	    		try {	    			
	    			String path = SSHclient.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
	    			RESOURCE_PATH = URLDecoder.decode(path, "UTF-8");
	    			RESOURCE_PATH = RESOURCE_PATH.substring(0,RESOURCE_PATH.lastIndexOf(File.separator, RESOURCE_PATH.length()-2)+1);
	    			System.out.println("Looking for config file in " +RESOURCE_PATH +CONFIG_FILE );
	    			String properties_string = FileUtils.readFileToString(new File(RESOURCE_PATH +CONFIG_FILE),"UTF-8");
		    		prop.load(new StringReader(properties_string.replace("\\","\\\\")));
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
	    	build_command = updateProperty(prop, "build_command");	    	
	    	local_path = updateProperty(prop, "local_path");
	    	remote_path = updateProperty(prop, "remote_path");
	    	
	    	String ff = updateProperty(prop, "file_filter");
	    	// *.origin - reserved for original copies of edited make files.
	    	if (ff != null && ff.length() > 1) file_filter = ff +",*.origin";
	    	
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
			
			// Use same folder structure on server
	    	if (remote_path==null || remote_path.length() < 1) {
	    		if (local_path.length()<1) {
	    			System.err.println("No local path specified.");
	    			throw new IOException("No local path specified");
	    		}
	    		System.out.println("No remote path set ("+remote_path+"). Using same path as local:"+local_path);
	    		remote_path = local_path.substring(0, local_path.lastIndexOf(File.separator));
	    	}
			
	    	archiver = new AppTar(local_path, file_filter);
	    	archive_path = this.archiver.archiveName(local_path);  
		    archive = fileName(archive_path);
		    remote_full_path = remote_path+"/" +noExtension(archive);
	            		
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
		public void makeConnect() throws IOException, JSchException, SftpException, NullPointerException, Exception {

			// 1.

			// JSch connect
			// get a new session
			System.out.print("Opening SFTP channel...");
			session = new_session();
			System.out.println(" Authenticated.");

			
			try {
				
				channel = session.openChannel("shell");

				// Enable agent-forwarding.
				((ChannelShell)channel).setAgentForwarding(true);

				//channel.setInputStream(System.in);			
				//channel.setOutputStream(System.out);
				channel.connect(3 * 1000);
				
				
				sftp_channel=(ChannelSftp)session.openChannel("sftp");;
				sftp_channel.setAgentForwarding(true);
				sftp_channel.connect(3000);
				System.out.println("Channels open.");
				try { Thread.sleep(500); } catch (Exception ee) { }
				//if (true) throw new Exception("Stop");
				
				//PathDetector pd = new PathDetector(local_path,remote_path,makefiles,null);
				//pd.detectPaths();
				//if (true) return;
				
				// 2. Create archive with source files,
				// 3. Upload archive to temporary directory
				createArchiveAndUpload();
				

				// 4. Extract source files from archive on remote machine
				// 5. Execute Make command
				String path_command = "";
				if (add_path.length() > 0) path_command = "whoami; PATH=$PATH:'"+add_path+"' && ";
				executeCommands(session,"ip addr show && cd '"+remote_path+"'  && pwd && tar -xvf '"+archive+"'", true,true,true); 
				executeCommands(session, path_command+"echo path=$PATH && cd '"+remote_path+"'  && pwd && tar -xvf '"+archive+"'", true,true,true); 
				executeCommands(session, path_command+ "cd '"+remote_full_path+ "' && echo $PATH && which atool && " + build_command,true,true,true);
				
				// 6. Pick up xml files
				String str_response = executeCommands(session, "cd '"+remote_full_path+"' && find -name \"*.xml\"",true,false,true);
				
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
				System.out.println("Cleaning remote location: " + remote_path);
				executeCommands(session, "cd "+remote_path+" && rm -r " + remote_full_path,false,false,false);	
				if (this.remove_remote_path) {
					System.out.println("rm -rf " + remote_path);
					executeCommands(session, "rm -rf " + remote_path,false,false,false);
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				System.out.println("Closing connections.");
				
				//JSch
				if (sftp_channel != null) sftp_channel.exit();
				if (channel != null) channel.disconnect();  
				if (session != null) session.disconnect();

				System.out.println("All tasks complete.");
			}
	    }

		
		/**
		 * Execute command over Jsch connection
		 * @param session	Jsch session instance
		 * @param commands Commands to execute
		 * @param display_stdout Set to true to display remote stdout
		 * @param display_stderr Set to true to display remote stderr
		 * @param verbose Set to true to display comments in stdout.
		 * @throws IOException
		 * @throws JSchException 
		 */
		private String executeCommands(Session session, String commands, boolean display_stdout, boolean display_stderr,boolean verbose) throws IOException, JSchException {
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(commands);
			((ChannelExec) channel).setAgentForwarding(true);
			channel.setInputStream(null);
			((ChannelExec) channel).setErrStream(System.err);
			InputStream in = channel.getInputStream();
			channel.connect();
			StringBuilder response = new StringBuilder();
			if (verbose && (display_stdout || display_stderr)) System.out.println("Command session report:\n----------------------------------");
			/*if (display_stderr) {
				(new stderrThread(stderr)).start(); // Display stderr in new thread
			}*/
			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(new StreamGobbler(in)));
				try {
					while (true) {
						String line = br.readLine();
						if (line == null) {
							break;
						}
						if (display_stdout) System.out.println(line);
						response.append(line+"\n\r");
					}
				} finally {
					br.close();
				}
			} finally {
				channel.disconnect();
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
        		attrs = sftp_channel.lstat(remote_path);
        	} catch (SftpException e) {
        		// Path does not exist
        		// Create new directory
        		String inmaking="";
        		try {
        			System.out.println("Creating remote folder "+ remote_path);
        			ArrayList<String> remote_paths = new ArrayList<String>();
        			remote_paths = breakPath(remote_path,remote_paths);
        			for (String next_path : remote_paths) {
        				inmaking = next_path;
        				try {
        					attrs = sftp_channel.lstat(inmaking);
        				} catch (SftpException es) {
        					System.out.println("making "+ next_path);
        					sftp_channel.mkdir(next_path);
        				}
        			}
        			this.remove_remote_path = true;
        			//  For now we delete only lowest folder.
        			//  Better solution: record all folders we create, and remove them all after all tasks complete.        			
        			inmaking = remote_path;
        			attrs = sftp_channel.lstat(inmaking);        			
        		}
        		catch (SftpException es) {
        			System.err.println("Failed creating temporary folder "+ inmaking);
        			throw es;
        		}
        		attrs = sftp_channel.lstat(remote_path);
        	}	        
	        
	        if (!attrs.isDir()) throw new SftpException(550, "Remote path is not a directory ("+remote_path+").");
	        
	        SftpProgressMonitor monitor = new MyProgressMonitor();
		    int mode=ChannelSftp.OVERWRITE;
		    sftp_channel.cd(remote_path);
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
		    
		    System.out.println(" ");
		}

	    /**
	     * Breaks path into subpaths from top down:
	     * /folder1/folder2/folder3 ->
	     * /folder1
	     * /folder1/folder2
	     * /folder1/folder2/folder3
	     * @param remote_path_to_split: path to split
	     * @param remote_paths: empty ArrayList of type String
	     * @return ArrayList of paths as Strings
	     */
	    private ArrayList<String> breakPath(String remote_path_to_split, ArrayList<String> remote_paths) {
			String[] pieces = remote_path_to_split.split(File.separator);
			String constructed_path = pieces[0];
			if (constructed_path.length()>0) remote_paths.add(constructed_path);
			for (int i=1; i < pieces.length; i++) {
				constructed_path = constructed_path + File.separator + pieces[i];
				if (pieces[i].length()>0) remote_paths.add(constructed_path);
			}
			return remote_paths;
		}

			    
	    private com.jcraft.jsch.Session new_session()  throws JSchException {
			JSch shell = new JSch();
			JSch.setConfig("PreferredAuthentication", "publickey");
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
				// Add remote identity repository
				
				try {
					ConnectorFactory cf = ConnectorFactory.getDefault();
					this.con = cf.createConnector();
				} catch (AgentProxyException e) {
					System.out.println(e);
				}

				if (this.con != null) {
					RemoteIdentityRepository irepo =  new RemoteIdentityRepository(this.con);
					shell.setIdentityRepository((IdentityRepository) irepo);
				}
				
				// end repository
				
				session = shell.getSession(user, host, port);
				session.setUserInfo(new SSHUserInfo());  
				session.setConfig("StrictHostKeyChecking", "no");

				if (key.length() > 0) {	     
					System.out.print(" Trying key authentication with "+key+" ... ");
					
										
					File key_file = new File(key);
					if (key_file.exists() == false) {
						System.err.println("Key file "+key+" not found.");
						throw new JSchException("Key authentication failed.");
					}
					shell.addIdentity(key_file.getAbsolutePath(), passphrase);
					
					
					try {
						session.connect(30000);
					} catch (JSchException je) {						
						System.err.println("\nException on connection attempt to "+session.getHost()+":"+session.getPort()+". Check your user name ("+session.getUserName()+"), key ("+key_file.getAbsolutePath()+") and passphrase ("+passphrase+") settings.");						
						throw je;
					}
					use_key_authentication = true;
				} else {
					try {
						session.connect(3000);
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

