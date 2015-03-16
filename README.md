## SSHconnect v1.19
 
# Utility for remote command execution with automatic data transfer. 
 
 
## Requirements
 
Requires processing program (compiler etc.) installed on a server.
SSH connection to the server, authentication by password or by key.
 
 
## Usage
 
java -jar SSHconnect.jar [options] 
  
   
### Options
 
 All options can be set in configuration file sshconnect_conf.txt or passed through command line options.
 Command line options have higher priority. 
 
 Command line options (and parameter names in configuration file):
 	-ap	Path added to PATH environment variable on the server. Use it to add processing program to the server PATH environment variable. (Configuration file parameter name is add_path).		
	-h	Server host address (Configuration file parameter name is host)		
	-p	Server port number for SSH connection (Configuration file parameter name is port)
	-u	User name for SSH connection authentication (Configuration file parameter name is user)		
	-pw	Password for SSH user (Configuration file parameter name is password)		-k	Path to RSA key for authentication on the server (Configuration file parameter name is key) 		
	-ph	Passphrase for RSA key (Configuration file parameter name is passphrase)	
	Password, key and passphrase are optional, but either password or key is necessary for authentication on the server.
 
	-rp	Path on the server to be used for temporary files (Configuration file parameter name is remote_path) 		
	-m	Command to be executed on the server for processing code (Configuration file parameter name is build_command)
	
 _Important notes on setting build_command:_

	1. If you set an executable file name, don't forget to add "./" before it if it's not on the server PATH
	2. If build_command path has spaces, put single quotes around the path like this: './makeproject 1.sh'
 
	-lp	Path to source files, also the place to download code processing results from the server (Configuration file parameter name is local_path)	
	-ff	Comma-separated list of common filename patters to exclude files from uploading to the server (Configuration file parameter name is file_filter)
 	Default is ".*, *.tar, *.html, *.zip, *.jpg".
 
	-pf	Files with placeholders that must be replaced with server-side absolute path before the code is processed on the server (Configuration file parameter name is preprocess_files)
  	Absolute path replacement placeholder: "#[remote_path]" (without quotes).
  	-dp	Comma-separated list of filename patterns to download product files after building source code. Example: "*.xml, *.h"
  	-cp	Command pattern. Default value is "echo '#' | $SHELL -l" (without double quotes). 
  		Command pattern is used to run build commands on "K" front-end with the purpose of initializing environment variables, and it also works with other servers. 
  		"#" in command pattern is replaced with commands, and resulting command stored in temporary shell script file, which is executed on the server before build command.
    
   
## Algorithm
 
 Establish SSH connection to the server.
 Parse files with absolute path replacement placeholders and replace placeholders with server-side absolute path. 
 Upload source code to the server.
 Execute build command on the server.
 Download product files (XML) files. 
 
 
# Used Libraries and Licenses
 
| Library | License|
|---------|--------|
| Commons Lang Package Version 3.1 | Apache License v2.0 |
| Apache Commons IO Version 2.4	| Apache License v2.0 |
| JSch 0.1.50 | BSD-like |
| JTar 2.1 | Apache License v2.0 |
| Orion SSH2 | BSD-like |


 