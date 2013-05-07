package ssh.connect;

import com.jcraft.jsch.*;

import java.io.BufferedReader;
import java.io.DataOutputStream;  
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;  
import java.io.InputStreamReader;
import java.util.regex.PatternSyntaxException;

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
	    
	    String local_path = "/Users/peterbryzgalov/work/HimenoBMT_Dprof"; // local project folder. Must contain Makefile and all file necessary for building.  
	    String remote_path = "/home/peter/kscope/tmp"; 
	    
	    
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
	    	//makeConnect();
	    }
	
    	public SSHbasic()  throws JSchException, SftpException, IOException  {	
    		//makeConnect();
    	}
    	
    	public void makeConnect()  throws JSchException, SftpException, IOException {
	        // get a new session    
	        Session session = new_session();  
	  
	        // Upload file block
	        Channel channel=session.openChannel("sftp");
	        channel.connect();
	        ChannelSftp sftp_channel=(ChannelSftp)channel;
	        
	        // Check if remote directory exists
	        // Method lstat throws SftpException if path does not exist
	        SftpATTRS attrs = null;
	        try {
	        	attrs = sftp_channel.lstat(remote_path);
	        } catch (SftpException e) {
	        	// Path does not exist
	        	// Create new directory
	        	sftp_channel.mkdir(remote_path);
	        	attrs = sftp_channel.lstat(remote_path);
	        }
	        
	        if (!attrs.isDir()) throw new SftpException(550, "Remote path is not a directory ("+remote_path+").");
	        
	        SftpProgressMonitor monitor=new MyProgressMonitor();
		    int mode=ChannelSftp.OVERWRITE;
		    sftp_channel.cd(remote_path);
		    System.out.println("Remote directory: " + sftp_channel.pwd());
		    
		    // Check if local path is valid
		    File local = new File(local_path);
		    if (!local.exists()) throw new IOException("Source path is not valid (not exists): "+local_path);
		    
		    sftp_channel.lcd(local_path);
		    System.out.println("Local directory: " + sftp_channel.lpwd());
		    
		    
		    // Create ZIP archive
		    AppZip appZip = new AppZip(local_path);
		    //appZip.setSource(local_path);
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
		    sftp_channel.exit();
		    System.out.println("Archive uploaded.");
		    	        
		    // Execute make
	        channel = session.openChannel("shell"); 
	        channel.connect();  
	        InputStreamReader isr = new InputStreamReader(channel.getInputStream());
	        BufferedReader dataIn = new BufferedReader(isr);  
	        DataOutputStream dataOut = new DataOutputStream(channel.getOutputStream());  
	        
	        // send ls command to the server  
	        execRemoteCommand("cd "+remote_path, dataIn, dataOut); 
	        
	        execRemoteCommand("unzip -o "+archive, dataIn, dataOut);
	        
	        execRemoteCommand("cd "+noExtension(archive), dataIn, dataOut);
	        
	        execRemoteCommand("make", dataIn, dataOut);
	        
	        execRemoteCommand("ls -la", dataIn, dataOut);
	    	        
	        dataOut.close();
	  
	        dataIn.close();
	        channel.disconnect();  
	        session.disconnect();
	        System.exit(0);
	    }

		/**
		 * Execute a remote command, waits 1 sec and prints output to dataIn stream
		 * @param command to execute
		 * @param dataIn	output of the command (stream towards local machine)
		 * @param dataOut	command stream (stream outwards local machine) 
		 * @throws IOException
		 */
		private void execRemoteCommand(String command, BufferedReader dataIn, DataOutputStream dataOut) throws IOException {
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

