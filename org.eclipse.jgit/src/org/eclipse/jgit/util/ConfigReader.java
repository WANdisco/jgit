package org.eclipse.jgit.util;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;

import java.io.File;
import java.io.IOException;

public class ConfigReader {
	/**
	 * The git config file to be used can be defined using the GIT_CONFIG environment
	 * variable, if this is not set the current user's .gitconfig file is used.
	 *
	 * @return The requested name found the the section/subsection of the git config
	 *         or the given default if not found
	 * @throws IOException
	 *           git config could not be read or is incorrect format.
	 */
	public static String getGitConfigProperty(String section, String subsection, String name, String defaultName) throws IOException {
		String gitConfigLoc = System.getenv("GIT_CONFIG");

		if (System.getenv("GIT_CONFIG") == null) {
			gitConfigLoc = System.getProperty("user.home") + "/.gitconfig";
		}

		FileBasedConfig config = new FileBasedConfig(new File(gitConfigLoc), FS.DETECTED);
		try {
			config.load();
		} catch (ConfigInvalidException e) {
			// Configuration file is not in the valid format, throw exception back.
			throw new IOException(e);
		}

		String configValue = config.getString(section,subsection,name);

		if (configValue != null) {
			return configValue;
		} else {
			return defaultName;
		}

	}
}
