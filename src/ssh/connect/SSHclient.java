package ssh.connect;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;  
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;
import com.jcraft.jsch.UserInfo;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;


/**
 * Ver.0.11 
 * Orion SSH + JSch
 * 
 * 
 * @author peterbryzgalov
 *
 */

public class SSHclient {

	/**
	 * @param args
	 * @throws JSchException 
	 * @throws SftpException 
	 */
	public static void main(String[] args) throws IOException, JSchException, SftpException {
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
	    
	    
	    public SSHbasic()  throws IOException  {	
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
    	
	    public void makeConnect()  throws IOException, JSchException, SftpException {
	    	Connection conn = new Connection(host);
	    	conn.connect();

	    	boolean isAuthenticated = conn.authenticateWithPassword(user, password);

	    	if (isAuthenticated == false)
	    		throw new IOException("Authentication failed.");
	    	Session sess = conn.openSession();
	    	sess.execCommand("uname -a && date && uptime && who && export PATH=$PATH:/home/peter/kscope && echo $PATH && cd /home/peter/kscope/NICAM-K && pwd && make && find . -name \"*.xml\"");
	    	System.out.println("Here is some information about the remote host:");
	    	InputStream stdout = new StreamGobbler(sess.getStdout());
	    	InputStream stderr = new StreamGobbler(sess.getStderr());
	    	BufferedReader br = new BufferedReader(new InputStreamReader(stdout));
	    	BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));
	    	
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
	    	sess.execCommand("cd /home/peter/kscope/NICAM-K/ && find -name \"*.xml\"");
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
	    			System.out.println(line);
	    		}	    		    		
	    	} finally {
	    		br.close();
	    		sess.close();	    		
	    	}	    	
	    	
	    	String[] filenames = response.toString().replaceAll("(\\s\\./)|(^\\./)", "").split("\n");
	    	System.out.println("\n\n--------");
	    	
	    	// JSch
	    	int mode=ChannelSftp.OVERWRITE;
	    	SftpProgressMonitor monitor = new MyProgressMonitor();
	    	 // get a new session    
	        com.jcraft.jsch.Session session = new_session();  
	  
	        // Upload file block
	        com.jcraft.jsch.Channel channel=session.openChannel("sftp");
	        channel.connect();
	        ChannelSftp sftp_channel=(ChannelSftp)channel;

	    	
	    	
	    	for (String filename:filenames) {
	    		if (filename.length() < 2) continue;
	    		String remote_filename ="/home/peter/kscope/NICAM-K/"+filename;
	    		String local_filename = local_path+"/" + filename;
	        	System.out.println("Downloading "+remote_filename+" to " + local_filename);
	        	
	        	File local_file = new File(local_filename);
	        	if (!local_file.exists()) {
	        		local_file.getParentFile().mkdirs();
	        		local_file.createNewFile();
	        	}
	        	sftp_channel.get(remote_filename, local_filename, monitor, mode);	  
	        	
	    	}
	    	conn.close();
	    	System.out.println("finished.");
	    } 	
	    
	    
	    private com.jcraft.jsch.Session new_session()  throws JSchException {
    		JSch shell = new JSch();
    		com.jcraft.jsch.Session session = shell.getSession(user, host, port);  
    		  
	        // set user password and connect to a channel  
	        session.setUserInfo(new SSHUserInfo(password));  
	        session.connect();  
	        return session;
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

