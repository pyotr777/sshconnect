package ssh.connect;

import java.io.File;

public interface FileFilter {
	boolean filter(File file);
}
