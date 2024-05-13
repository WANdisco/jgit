package org.eclipse.jgit.util;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;

import java.io.File;
import java.io.IOException;

/**
 * A new singleton git configuration class, used to enable reading the Git
 * configuration
 * file and its properties.  This can be used to read properties in a generic
 * fasion on to read
 * some of the known Replication named properties like where our GitMS
 * application properties file is.
 */
public final class GitConfiguration {

    private final static Object gitConfigLocking = new Object();
    private static FileBasedConfig config = null;


    /**
     * Get main git configuration file property.
     * <p></p>
     * The git config file to be used can defined using the GIT_CONFIG
     * environment
     * variable, if this is not set the current user's .gitconfig file is used.
     *
     * @param core
     * @param subsection
     * @param gitmsconfig
     * @return String value of the requested property or subsection if available
     * @throws IOException
     */
    public static String getGitConfigProperty(final String core,
                                              final String subsection,
                                              final String gitmsconfig) throws IOException {
        try {
            loadGitConfig();
        } catch (ConfigInvalidException e) {
            throw new IOException("Unable to read main git configuration core.", e);
        }

        return config.getString(core, subsection, gitmsconfig);
    }

    /**
     * Attempt to find the git update script hook name and path.
     * Uses the getGitConfigProperty so uses the same method of finding the
     * git config file.
     *
     * @param section
     * @param subsection
     * @param name
     * @param defaultName
     * @return The name of the update script including path if it exists in the
     * git config or the default hook name,
     * e.g. 'rp-git-update' which should exist on the path otherwise.
     * @throws IOException git config could not be read or is incorrect format.
     */
    public static String getAndCheckGitHook(final String section,
                                            final String subsection,
                                            final String name,
                                            final String defaultName) throws IOException {

        final String configScript = getGitConfigProperty(section, subsection,
                name);

        if (configScript != null && new File(configScript).exists()) {
            return configScript;
        }

        return defaultName;
    }

    /**
     * Find out the location of the main git configuration file.
     *
     * @return Location of config file.
     */
    public static String getGitConfigLocation() {
        String gitConfigLoc = System.getenv("GIT_CONFIG");

        if (System.getenv("GIT_CONFIG") == null) {
            gitConfigLoc = System.getProperty("user.home") + "/.gitconfig";
        }
        return gitConfigLoc;
    }

    /**
     * Internal method to safely load the Git Configuration file and its
     * properties in a thead
     * safe way. This method will also refresh an already loaded Git
     * Configuration if it becomes
     * outdated ( stale ).
     *
     * @throws IOException
     * @throws ConfigInvalidException
     */
    private static void loadGitConfig() throws IOException,
            ConfigInvalidException {
        // If we have the config file already cached, use it.
        if (config != null) {
            // if the config file on disk has been updated, just update our
            // cached snapshot of info.
            if (config.isOutdated()) {
                synchronized (gitConfigLocking) {
                    // check is it still outdated - double checked locking as
                    // someone could have updated
                    // this while we were waiting on the lock!
                    if (config.isOutdated()) {
                        config.load();
                    }
                }
            }
            return;
        }

        // take out a lock
        synchronized (gitConfigLocking) {

            // use double check locking JIC.
            if (config != null) {
                // someone has loaded the config while we waiting on the
                // locking object...
                // return now.
                return;
            }

            // and load our configuration for the first time.
            // find out where it is located.
            final String gitConfigLoc = getGitConfigLocation();

            FileBasedConfig tmpConfig =
                    new FileBasedConfig(new File(gitConfigLoc), FS.DETECTED);
            try {
                tmpConfig.load();
            } catch (ConfigInvalidException e) {
                // Configuration file is not in the valid format, throw
                // exception back.
                throw new IOException(e);
            }
            config = tmpConfig;
        }
    }


}
