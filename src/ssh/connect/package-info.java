/**
 *  v.0.30
 *  2013.06.27
 * 
 * 
 *  Last added features:
 * 
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
 * Receive parameters from command-line arguments (order important!):
 * 1. make command (make)
 * 2. make options (make_options)
 * 3. makefile (makefiles)
 * 4. source code directory (local_path)
 *  
 * Parameters MUST be set in configuration file:
 * 	host - host address
 * 	port - host SSH port number. Default 22.  
 * 	user - remote user name for connecting by SSH
 * 	password - remote user password OR
 *	{
 * 	key - RSA private key file path 
 *	passphrase - key passphrase   (password, key and passphrase are optional, but need password or key and passphrase to authenticate on the server)
 *	}
 *	remote_path - remote location to used for creating temporary files
 *	file_filter - pattern for filtering out unnecessary files from source code directory. 
 * Default is ".*,*.tar,*.html,*.zip,*.jpg.*.orgin".
 * These files will stay untouched on local machine, but will not be uploaded to server. 
 * 
 * Parameters CAN be set in configuration file (defaults to use if command-line argument is not provided): 
 *	makefiles - makefiles to look into for replacement placeholders
 *	local_path - local path to use in case it is not set through command-line arguments.
 *	make command - command to execute to start remote make.
 *	make options - options for "make" command.
 *    
 *  
 *  Workflow:
 *  
 * Parse makefiles and replace placeholders. 
 * Upload source code to remote location.
 * Execute make command on remote.
 * Download product files (XML files).
 * 
 */
/**
 * @author peterbryzgalov
 *
 */
package ssh.connect;