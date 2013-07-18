/**
 *  v.0.33
 *  2013.07.18
 * 
 * 
 *  Last added features:
 * 
 * Read all parameters from command line. 
 * Correct processing of single quotes around paths in command line - if path contain spaces, it must be single-quoted.
 * New format of command-line parameters. Build command is one String. Preprocess files is a new parameter.
 * Check if remote temporary directory exists before creating it. If exists, add counter to the end of the name, increase counter value and check again. 
 * Added atool_path parameter. If set in configuration file, atool_path will be added to PATH environment variable on server before running make command. 
 * Use tar archive to keep file privileges
 * Custom make command support
 * Client time before server bug corrected
 * Key authentication (work with password too)
 * Simultaneous stdout and stderr output. 
 * Error handling.
 * Works with K-scope.
 * Orion SSH + JSch
 * Parsing Makefiles for replacement placeholders
 * Unique temporary directory names
 *  
 *  
 *  Parameters:
 * 
 * All parameters can be set in configuration file or pass through command line options.
 * Command line options have higher priority. 
 * 
 * Command line options and parameter names in configuration file
 * 	-ap		add_path			Path added to PATH environment variable on the server. Use it to add atool path to server PATH.		
 *	-h		host				Server host address		
 *	-p		port				Server port number for SSH connection
 *	-u		user				User name for SSH connection authentication		
 *	-pw		password			Password for SSH user		
 *	-k		key					Path to RSA key for authentication on the server 		
 *	-ph		passphrase			Passphrase for RSA key		
 * Password, key and passphrase are optional, but need password or key and passphrase to authenticate on the server
 *
 *	-rp		remote_path			Path on the server to be used for temporary files 		
 *	-m		build_command		Command to be executed on the server for building the project
 * 1. 実行ファイルを指定する場合、ファイル名の前に"./"を指定しなければならない。
 * 2. ファイル名（パス）にスペースが使われている場合はアポストロフィを使用しなければならない。例えば：　'./makeproject 1.sh'
 *	
 *	-lp		local_path			Path to source files, also the place to download intermediate code from the server after the project has been built	
 *	-ff		file_filter			Comma-separated list of common filename patters to exclude files from uploading to the server
 * Default is ".*,*.tar,*.html,*.zip,*.jpg.*.orgin".
 * 
 *	-pf		preprocess_files	Files with placeholders that must be replaced with server-side absolute path before the project is built on the server
 * 
 * 
 *  Workflow:
 *  
 * Parse preprocess_files and replace placeholders. 
 * Upload source code to remote location.
 * Execute build command on the server.
 * Download product files (XML files).
 * 
 */
/**
 * @author peterbryzgalov
 *
 */
package ssh.connect;