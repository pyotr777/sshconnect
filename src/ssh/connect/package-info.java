/**
 *  v.1.16
 *  2013.12.17
 * 
 * 
 *  Last added features:
 *  
 * Add comand replacement pattern for use with "K" frontend.
 * Remove all comments from Absolute path files (preprocess files). Comments on the same path with placeholders caused extra white space in path error in atool.
 * Added parameter product_pattern for defining product files to download. Default: *.xml. Format: comma-separated list of simple file patterns, like: *.xml, *.java
 * Switched to jtar2 library, symlinks copied as symlinks to server.
 * Switched to JDK 1.7 system library. Added check for folder link loops before creating tar archive.
 * Changes in diagnostic messages and key authentication for Orion.
 * Catching exceptions if no X11 present ("No X11 DISPLAY variable was set, but this program performed an operation which requires it.")
 * Corrected error with Windows file paths. 
 * File filter arg parameter correction to exclude original Absolute path files. 
 * Removed function: "Replace spaces in source folder name on the server before building project."
 * Local path detection in files: gives warning.
 * Configuration file made optional.
 * Replace spaces in source folder name on the server before building project.
 * Read all parameters from command line. 
 * Correct processing of single quotes around paths in command line - if path contain spaces, it must be single-quoted.
 * New format of command-line parameters. Build command is one String. preprocess_files is a new parameter.
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
 * 	-ap		add_path			Path added to PATH environment variable on the server. Use it to add atool path to server PATH environment variable.		
 *	-h		host				Server host address		
 *	-p		port				Server port number for SSH connection
 *	-u		user				User name for SSH connection authentication		
 *	-pw		password			Password for SSH user		
 *	-k		key					Path to RSA key for authentication on the server 		
 *	-ph		passphrase			Passphrase for RSA key		
 * Password, key and passphrase are optional, but either password or key is necessary for authentication on the server.
 *
 *	-rp		remote_path			Path on the server to be used for temporary files 		
 *	-m		build_command		Command to be executed on the server for building the project
 *　build_commandの注意事項：
 * 1. 実行ファイルを指定する場合、PATHに載ってないファイル名の前に"./"を指定しなければならない。
 * 2. ファイル名（パス）にスペースが使われている場合はアポストロフィを使用しなければならない。例えば：　'./makeproject 1.sh'
 *  Important notes on setting build_command:
 *  1. If you set an executable file name, don't forget to add "./" before it if it's not on PATH
 *	2. If build_command path has spaces, put single quotes around the path like this: './makeproject 1.sh'
 *
 *	-lp		local_path			Path to source files, also the place to download intermediate code from the server after the project has been built	
 *	-ff		file_filter			Comma-separated list of common filename patters to exclude files from uploading to the server
 * Default is ".*,*.tar,*.html,*.zip,*.jpg.*.orgin".
 * 
 *	-pf		preprocess_files	Files with placeholders that must be replaced with server-side absolute path before the project is built on the server
 * 
 * 
 * Algorithm
 * 
 * Establish SSH connection to the server.
 * Parse files with absolute path replacement placeholders and replace placeholders with server-side absolute path. 
 * Upload source code to the server.
 * Execute build command on the server.
 * Download product files (XML) files. 
 * 
 */
/**
 * @author peterbryzgalov
 *
 */
package ssh.connect;