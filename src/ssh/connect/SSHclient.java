package ssh.connect;

import com.jcraft.jsch.*;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;  
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.PatternSyntaxException;


/**
 * Ver.0.13 
 * Channel EXEC
 * 
 * Successfully tested on Himeno project. 15.05.2013 10:45
 * 
 * @author peterbryzgalov
 *
 */

public class SSHclient {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws JSchException, SftpException, IOException {
		SSHbasic basic_coonection = new SSHbasic();
		basic_coonection.makeConnect();
	}

	static class SSHbasic {
		String endLineStr = " # "; // it is dependent to the server  
	    String host = "192.168.56.101"; // host IP  
	    String user = "peter"; // username for SSH connection  
	    String password = "simsim"; // password for SSH connection  
	    //String archive = "HimenoBMT_Dprof.zip";
	    // default name for archive file
		private String default_archive_filename = "archive.zip"; 
	    
		// local project folder. Must contain Makefile and all files necessary for building.  
	    String local_path = "/Users/peterbryzgalov/work/HimenoBMT_Dprof"; 
	    String remote_path = "/home/peter/kscope/"; 
	    
	    // Remote temporary folder name
	    java.util.Random rndm = new java.util.Random();
	    String tmp_dir = "tmp" + rndm.nextInt();
	    
	    // Source file filter.
	    // Exclude selected file types.
	    String file_filter = ".*,*.tar,*.html,*.zip,*.jpg";
	    	    
	    int group_id = 1002; // kscope group ID	   
	    int port = 22; // default SSH port  
	    
	    
	    /**
	     * Create SSH_basic connection class with address.
	     * @param address	Address with format: "user:password@host_address:port"
	     */
	    public SSHbasic(String address)  throws JSchException, SftpException, IOException  {
	    	// user_and_address[0] = user name and password
	    	// user_and_address[1] = host address and port
	    	String[] user_and_address;
	    	
	    	// user_and_password[0] = user name
	    	// user_and_password[1] = password
	    	String[] user_and_password;
	    	
	    	// Host address and port number
	    	String[] address_and_port;
	    
	    	try {
	    		user_and_address = address.split("@"); 
		    	if (user_and_address[0].length() > 0) {
		    		if (user_and_address[0].indexOf(":") > 0) {
		    			// user_and_address[0] = "user:password"
		    			user_and_password = user_and_address[0].split(":");
		    			if (user_and_password[0].length() > 0) user = user_and_password[0];
		    			if (user_and_password[1].length() > 0) password = user_and_password[1];
		    		} else {
		    			// user_and_address[0] = "user"
		    			user = user_and_address[0];
		    		}
		    	}
		    	if (user_and_address[1].length() > 0) {
		    		if (user_and_address[1].indexOf(":") > 0) {
		    			// user_and_address[1] = "host address:port"
		    			address_and_port = user_and_address[1].split(":");
		    			if (address_and_port[0].length() > 0) host = address_and_port[0];
		    			if (address_and_port[1].length() > 0) port = Integer.parseInt(address_and_port[1]);
		    		} else {
		    			// user_and_address[1] = "host address"
		    			host = user_and_address[1];
		    		}
		    	}
	    	} catch (PatternSyntaxException e) {
	    		e.printStackTrace();
	    	}
	    }
	
    	public SSHbasic()  throws JSchException, SftpException, IOException  {	
    		// Read parameters from configuration file
    		
    		Properties prop = new Properties();
        	try {
                //load properties from config file
        		prop.load(new FileInputStream("config.txt"));
        		user = prop.getProperty("user").replaceAll("\\s","");
        		password = prop.getProperty("password").replaceAll("\\s","");
        		host = prop.getProperty("host").replaceAll("\\s","");
        		port = Integer.parseInt(prop.getProperty("port").replaceAll("\\s",""));
        		local_path = prop.getProperty("local_path").replaceAll("\\s","");
        		remote_path = prop.getProperty("remote_path").replaceAll("\\s","");     
        		file_filter = prop.getProperty("file_filter").replaceAll("\\s","");
        	} catch (IOException e) {
        		e.printStackTrace();
        		System.out.println("Default parameters used.");
        	}
    	}
    	
    	public void makeConnect()  throws JSchException, SftpException, IOException {
	        // get a new session    
	        Session session = new_session();  
	  
	        // Upload file block
	        Channel channel=session.openChannel("sftp");
	        channel.connect();
	        ChannelSftp sftp_channel=(ChannelSftp)channel;
	        
	        // Temporary directory: remote_tmp
	        //String remote_tmp = remote_path + "/" + tmp_dir;
	        String remote_tmp = "/home/peter/kscope";
	        remote_tmp = remote_tmp.replaceAll("//", "/");
	        
	        // Check if remote directory exists
	        // Method lstat throws SftpException if path does not exist
	       /*
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
	        */
	        SftpProgressMonitor monitor = new MyProgressMonitor();
		    int mode=ChannelSftp.OVERWRITE;
		    sftp_channel.cd(remote_tmp);
		    System.out.println("Remote directory: " + sftp_channel.pwd());
		    
		    // Check if local path is valid
		    File local = new File(local_path);
		    if (!local.exists()) throw new IOException("Source path is not valid (not exists): "+local_path);
		    
		    sftp_channel.lcd(local_path);
		    System.out.println("Local directory: " + sftp_channel.lpwd());
	    
		    /*
		    
		    // Create ZIP archive
		    AppZip appZip = new AppZip(local_path, file_filter);
	    	String archive_path = archiveName(local_path);
	    	appZip.zipIt(archive_path);
	    	File file = new File(archive_path);
		    String archive = fileName(archive_path);
		    System.out.println("Created zip: " + archive_path + archive);		    
		    if (file.exists()) {
			    FileInputStream file_stream = new FileInputStream(file);
			    sftp_channel.put(file_stream, archive, monitor, mode); 
			    sftp_channel.chgrp(group_id, archive); 
		    }
		    System.out.println("Archive uploaded.");
		    
		    // Delete local archive file
		    file.delete();
		    	        
		    	        */
		    /* SHELL CHANNEL
		    channel = session.openChannel("shell"); 
	        channel.connect();  
	        InputStreamReader isr = new InputStreamReader(channel.getInputStream());
	        BufferedReader dataIn = new BufferedReader(isr);  
	        DataOutputStream dataOut = new DataOutputStream(channel.getOutputStream());  
	        
	        // send ls command to the server  
	        execRemoteCommand("echo $PATH", dataIn, dataOut); 
 */
		    
		    
		    // Execute make
		    execRemoteCommands(session,"cd "+remote_tmp+"/NICAM-K\nmake\n");
		    String filelist = execRemoteCommands(session,"cd "+remote_tmp+"/NICAM-K\nls\n");
		    String[] filenames = findXMLfilenameFromList(filelist);
		    // Download XML
	        for (String filename:filenames) {
	        	String remote_filename = sftp_channel.pwd()+"/NICAM-K/"+filename;
	        	System.out.println("Downloading "+remote_filename+" to " + sftp_channel.lpwd());
	        	sftp_channel.get(remote_filename, ".", monitor, mode);
	        }
	        
	        // Clean remote tmp files
	        //execRemoteCommands(session,"cd "+remote_path+"\nrm -r " + tmp_dir);
		    
		    /*
	        channel = session.openChannel("shell"); 
	        channel.connect();  
	        InputStreamReader isr = new InputStreamReader(channel.getInputStream());
	        BufferedReader dataIn = new BufferedReader(isr);  
	        DataOutputStream dataOut = new DataOutputStream(channel.getOutputStream());  
	        
	        // send ls command to the server  
	        execRemoteCommand("cd "+remote_tmp, dataIn, dataOut); 
	        execRemoteCommand("unzip -o "+archive, dataIn, dataOut);
	        execRemoteCommand("cd "+noExtension(archive), dataIn, dataOut);
	        execRemoteCommand("make", dataIn, dataOut);
	        String filelist = execRemoteCommand("ls", dataIn, dataOut);
	    	        
	        String[] filenames = findXMLfilenameFromList(filelist);
	        // Download XML
	        for (String filename:filenames) {
	        	String remote_filename = sftp_channel.pwd()+"/"+ noExtension(archive)+"/"+filename;
	        	System.out.println("Downloading "+remote_filename+" to " + sftp_channel.lpwd());
	        	sftp_channel.get(remote_filename, ".", monitor, mode);
	        }
	        // Clean remote tmp files
	        execRemoteCommand("cd "+remote_path, dataIn, dataOut);
	        execRemoteCommand("rm -r " + tmp_dir, dataIn, dataOut);
	         dataOut.close();
	        dataIn.close();
	       */
	        
	        
	       
	        sftp_channel.exit();		    
	        channel.disconnect();  
	        session.disconnect();
	        System.exit(0);
	    }

    	/**
    	 * Execute number of command on remote machine 
    	 * using channel Exec.
    	 * 
    	 * @param session	connection session,
    	 * @param commands	commands to be executed.
    	 * @throws JSchException 
    	 * @throws IOException 
    	 */
    	private String execRemoteCommands(Session session, String commands) throws JSchException, IOException {
    		System.out.print("Exec "+ commands + "\n");
    		StringBuilder response = new StringBuilder();
    		Channel ch = session.openChannel("exec");
    		//((ChannelExec)ch).setEnv("PATH", ".:/bin:/usr/bin:/home/peter/kscope:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");
    		((ChannelExec)ch).setCommand("sh /bin/path.sh\n"+commands);
    		ch.setInputStream(null);
    		((ChannelExec)ch).setErrStream(System.err);
    		InputStream in=ch.getInputStream();
    		ch.connect();
    		byte[] tmp=new byte[1024];
    		while(true){
    			System.out.print("Available " + in.available() + ". Closed " + ch.isClosed()+ "\n");
    			while(in.available() > 0){
    				int i=in.read(tmp, 0, 1024);
    				if(i < 0) break;
    				String s = new String(tmp, 0, i);
    				System.out.print(s);
    				response.append(s);
    				System.out.print("\n-- Available " + in.available() + ". Closed " + ch.isClosed()+ "\n");
    				try{Thread.sleep(1000);}catch(Exception ee){ ee.printStackTrace(); }    				
    			}
    			if(ch.isClosed()){
    				System.out.println("exit-status: "+ch.getExitStatus());
    				break;
    			}
    			try{Thread.sleep(1000);}catch(Exception ee){ ee.printStackTrace(); }
    		}
    		ch.disconnect();	
    		return response.toString();
		}
    	
    	/**
		 * Execute a remote command, waits 1 sec and prints output to dataIn stream
		 * @param command to execute
		 * @param dataIn	output of the command (stream towards local machine)
		 * @param dataOut	command stream (stream outwards local machine) 
		 * @throws IOException
		 */
		private String execRemoteCommand(String command, BufferedReader dataIn, DataOutputStream dataOut) throws IOException {
			StringBuilder response;
			dataOut.writeBytes(command+"\r\n");
	        // print the response   	
	        dataOut.flush();
	        try {Thread.sleep(1000); } catch (Exception ee) {ee.printStackTrace();}
	        response= new StringBuilder();
	        while(dataIn.ready()) {
	        	char c = (char)dataIn.read();
	        	response.append(c);       
	        } 
	        if (response.length() < 1) {
	        	System.out.println("No response");
	        } 
	        System.out.println(correctSymbols(response.toString()));
	        return response.toString();
		}  

		/**
    	 * Find generated XML file with intermediate code from file list.
    	 * The file has following characteristics:
    	 *  - created after other files in the same folder,
    	 *  - has extension .xml 
    	 * @param filelist	list of files from command ls -la 
    	 * @return file name of the XML file
    	 */
		private String[] findXMLfilenameFromList(String filelist) {
			ArrayList<String> xml_filename_list = new ArrayList<String>();
			String[] filenames = filelist.split("\n"); 
			for (String filename : filenames) {
				if (filename.length() > 1) {
					if (checkName(filename)) xml_filename_list.add(filename);
				}
			}
			String[] xml_filenames = new String[xml_filename_list.size()];
			xml_filenames = xml_filename_list.toArray(xml_filenames);
			return xml_filenames;
		}

		/**
		 * Check if the file is XML
		 * @param filename	File name
		 * @return true if it's XML file
		 */
		private boolean checkName(String filename) {
			String[] xml_patterns = {".xml",".XML"};
			for (int i = 0; i < xml_patterns.length; i++) {
				int p = filename.indexOf(xml_patterns[i]);
				// Check if file name ends with one of the patterns
				if (p > 0 && p + xml_patterns[i].length() == filename.length()) return true; 
			}
			return false;
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

		private Session new_session()  throws JSchException {
    		JSch shell = new JSch();
    		Session session = shell.getSession(user, host, port);  
    		  
	        // set user password and connect to a channel  
	        session.setUserInfo(new SSHUserInfo(password));  
	        session.connect();  
	        return session;
    	}  
		
		/**
    	 * Correct distorted symbols
    	 * @param str Input string to be corrected
    	 * @return corrected string
    	 */
    	private String correctSymbols(String str) {
    		if (str == null) return null;
    		String[][] correct_pairs = {{"[34;42m","/"},{"[01;34m",""},{"[0m",""},{"[01;32m","*"},{"[01;31m",""}};
    		for (int i = 0; i < correct_pairs.length; i++) {
    			str = str.replace(correct_pairs[i][0], correct_pairs[i][1]);
    		}
    		return str;
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

