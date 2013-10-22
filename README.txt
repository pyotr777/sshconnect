 SSHconnect v1.03
 
 Utility for remote source code processing. 
 
 
 Requirements
 
 Requires processing program (compiler etc.) installed on a server
 SSH connection to the server, authentication by password or by key.
 
 
 Usage
 
 java -jar SSHconnect.jar [options] 
  
   
 Parameters
 
 All parameters can be set in configuration file sshconnect_conf.txt or passed through command line options.
 Command line options have higher priority. 
 
 Command line options and parameter names in configuration file
 	-ap		add_path			Path added to PATH environment variable on the server. Use it to add processing program to the server PATH environment variable.		
	-h		host				Server host address		
	-p		port				Server port number for SSH connection
	-u		user				User name for SSH connection authentication		
	-pw		password			Password for SSH user		
	-k		key					Path to RSA key for authentication on the server 		
	-ph		passphrase			Passphrase for RSA key		
 	Password, key and passphrase are optional, but either password or key is necessary for authentication on the server.
 
	-rp		remote_path			Path on the server to be used for temporary files 		
	-m		build_command		Command to be executed on the server for processing code
	Important notes on setting build_command:
		1. If you set an executable file name, don't forget to add "./" before it if it's not on the server PATH
		2. If build_command path has spaces, put single quotes around the path like this: './makeproject 1.sh'
 
	-lp		local_path			Path to source files, also the place to download code processing results from the server	
	-ff		file_filter			Comma-separated list of common filename patters to exclude files from uploading to the server
 	Default is ".*, *.tar, *.html, *.zip, *.jpg".
 
	-pf		preprocess_files	Files with placeholders that must be replaced with server-side absolute path before the code is processed on the server
  	Absolute path replacement placeholder: "#[remote_path]" (without quotes).
    
   
 Algorithm
 
 Establish SSH connection to the server.
 Parse files with absolute path replacement placeholders and replace placeholders with server-side absolute path. 
 Upload source code to the server.
 Execute build command on the server.
 Download product files (XML) files. 
 
 
 Limitations etc.
 
 1. Product files must have .xml extension.
 2. If there is a symlink loop in the source code directory structure, directories included in the loop will not be uploaded to the server.
 
 
 Used Libraries and Licenses
 
 Library								License
 . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .
 Commons Lang Package Version 3.1		Apache License v2.0
 Apache Commons IO Version 2.4			Apache License v2.0
 JSch 0.1.50							BSD-like
 JTar 1.1	 							Apache License v2.0
 Orion SSH2								BSD-like

 