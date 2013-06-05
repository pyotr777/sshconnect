/**
 *  v.0.23
 *  2013.06.05
 * 
 * 
 *  Last added features:
 * 
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
 * Receive parameters from command line arguments (order important!):
 * 1. make command (make)
 * 2. make options (make_options)
 * 3. make file (makefiles)
 * 4. source code directory 
 *  
 * Parameters must be set in config.txt:
 * 	host - host address  
 * 	user - remote user name for connecting by SSH
 * 	password - remote user password
 * 	key - RSA private key file path
 *	passphrase - key passphrase   (password, key and passphrase are optional, but need password or key and passphrase to authenticate on the server)
 *	group_id - remote "kscope" group ID
 *	port - host SSH port number. Default 22.
 *	remote_path - remote location to used for creating temporary files
 *	file_filter - pattern for filtering out unnecessary files from source code directory. 
 * Default is ".*,*.tar,*.html,*.zip,*.jpg.*.orgin".
 * These files will stay untouched on local machine, but will not be uploaded to server.  
 *	makefiles - makefiles to look into for replacement placeholders
 *  
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