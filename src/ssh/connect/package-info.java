/**
 *  v.0.21
 *  2013.05.31
 * 
 * 
 *  Last added features:
 *  
 * Simultaneous stdout and stderr output. 
 * Error handling.
 * Works with K-scope.
 * 
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
 * 1. host - host address  
 * 2. user - remote user name for connecting by SSH
 * 3. password - remote user password
 * 4. group_id - remote "kscope" group ID
 * 5. port - host SSH port number. Default 22.
 * 6. remote_path - remote location to used for creating temporary files
 * 7. file_filter - pattern for filtering out unnecessary files from source code directory. 
 * Default is ".*,*.tar,*.html,*.zip,*.jpg.*.orgin".
 * These files will stay untouched on local machine, but will not be uploaded to server.  
 * 8. makefiles - makefiles to look into for replacement placeholders
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