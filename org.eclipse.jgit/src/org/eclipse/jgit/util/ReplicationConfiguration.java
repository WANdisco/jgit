package org.eclipse.jgit.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.GITMSCONFIG;
import static org.eclipse.jgit.lib.Constants.REPLICATION_DISABLED;

/**
 * Simply the ability to obtain, or check if we are using replication.
 */
public class ReplicationConfiguration {

    private static File appPropertiesFile = null;
    private static long appPropsLastModified = 0;
    private static Properties appProperties = new Properties();
    private final static Object appPropertiesLocking = new Object();

    // A flag, which allow there to be no application properties and for us to behave like a
    // normal vanilla non replicated environment.
    private static Boolean replicationDisabled = null;

    /**
     * isReplicatedSystem - returns details about whether the system is
     * replicated or not.
     * <p>
     * Returns TRUE:
     * if this system is replicated,
     * replication configuration file pointed to from main gitconfig
     * (gitmsconfig),
     * replicated configuration file exists in pointed to location,
     * that we can actually read the contents of the replicated configuration
     * file.
     *
     * @return TRUE if replication is configured otherwise FALSE.
     * @throws IOException
     */
    public static boolean isReplicatedSystem() throws IOException {
        if (appPropertiesFile == null) {
            return loadApplicationProperties();
        }

        return isReplicationEnabled() && checkIsReplicatedConfigValid(true);
    }

    /**
     * Indicates if we replication is enabled for this component.
     * @return (Default:True)
     *
     * returns TRUE when replication enabled ( default ).
      */

    public static boolean isReplicationEnabled(){
        return !isReplicationDisabled();
    }

    /**
     * Indicates if we are to disable replication for this component.
     * @return (Default:False)
     * TRUE when replication has been overriden to be disabled in this environment.
     * Used mainly during installation with a replicated jar, or dev testing currently.
     */
    public static boolean isReplicationDisabled(){
        if (replicationDisabled == null) {
            replicationDisabled = getOverrideBehaviour(REPLICATION_DISABLED);
        }

        return replicationDisabled;
    }

    /**
     * Utility method to get the system override properties and returns them as a boolean
     * indicating whether they are enabled / disabled.
     *
     * @param overrideName
     * @return (Default:False)
     * Returns boolean indicating whether the requested behaviour property has
     * been found and a value setup for its behaviour.
     */
    public static boolean getOverrideBehaviour(String overrideName) {

        return getOverrideBehaviour(overrideName, null);
    }

    /**
     * Utility method to get the system override properties and returns them as a boolean
     * indicating whether they are enabled / disabled.
     *
     * @param overrideName
     * @param defaultValue
     * @return Returns boolean indicating whether the requested behaviour property has
     * been found and a value setup for its behaviour.  Default value is used if no property is found.
     */
    public static boolean getOverrideBehaviour(String overrideName, Boolean defaultValue) {

        // work out system env value first... Note as env is case sensitive and properties usually lower case, we will
        // use what the client has passed in, but also request toUpper for the environment option JIC.
        // e.g. 'replication_disabled' the property would be 'REPLICATION_DISABLED' the environment var.
        String env = System.getenv(overrideName);
        if ( StringUtils.isEmptyOrNull(env)){
            // retry with uppercase
            env = System.getenv(overrideName.toUpperCase());
        }

        // if we have no env value found, and there has been a default specified, lets use its value now.
        if ( StringUtils.isEmptyOrNull(env) &&  (defaultValue != null) ){
            return defaultValue;
        }

        // Otherwise we have a value, lets turn it back to a boolean from a String for ease of use.
        return Boolean.parseBoolean(System.getProperty(overrideName, env));
    }

    /**
     * Check if the cached replicated config can be read, and is still
     * valid.  We have an override, allowRefreshOfProperties, which if it
     * is false prevents the refresh - this prevent recursion of this
     * method...
     *
     * @param allowRefreshOfProperties  True will allow application
     *                                  properties to be refreshed / loaded.
     * @return True if the replicated config exists can be read and is valid.
     * @throws IOException
     */
    private static boolean checkIsReplicatedConfigValid(boolean allowRefreshOfProperties) throws IOException {
        // we already have the file if for any reason its updated, just
        // reload the props we hold!
        if (appPropertiesFile.exists() && appPropertiesFile.canRead()) {
            if (appPropsLastModified <= appPropertiesFile.lastModified()) {
                return true;
            }

            // otherwise the file has been modified, lets force the pickup of
            // new properties info!
            if (allowRefreshOfProperties) {
                return loadApplicationProperties();
            }
        }

        return false;
    }

    /**
     * Obtain a property from the application properties using the specified
     * propertyname.
     *
     * @param propertyName name of the property to obtain a value for.
     * @return property string value or null if not present.
     * @throws IOException
     */
    public static String getProperty(final String propertyName) throws IOException {

        return getProperty(propertyName, null);
    }

    /**
     * Obtain a property from the application properties using the specified
     * propertyname.  Value is returned if found or default value is used.
     *
     * @param propertyName Name of the property to get the value for.
     * @param defaultVal   Default value for the property, if no value is
     *                     obtained.
     * @return property.
     * @throws IOException
     */
    public static String getProperty(final String propertyName,
                                     final String defaultVal) throws IOException {

        isReplicatedSystem();

        String retval = appProperties.getProperty(propertyName);

        if (retval == null || retval.isEmpty()) {
            return defaultVal;
        }

        return retval;
    }

    /**
     * Get replicated gitms local jetty port.
     *
     * @return String value of local jetty port.
     * @throws IOException
     */
    public static String getPort() throws IOException {
        return getProperty("gitms.local.jetty.port");
    }

    /**
     * Get replicated repo deployment timeout value for requests.
     *
     * @return String value of the timeout ,or the default timeout.
     * @throws IOException
     */
    public static String getRepoDeployTimeout() throws IOException {
        return getProperty("gitms.repo.deploy.timeout", "60");
    }

    private static boolean loadApplicationProperties() throws IOException {

        // Get the vanilla git configuration file, and obtain from it the
        // gitmsconfig property pointing to
        // our replication file.
        String appPropertiesLocation =
                GitConfiguration.getGitConfigProperty(CONFIG_CORE_SECTION,
                        null, GITMSCONFIG);
        if (StringUtils.isEmptyOrNull(appPropertiesLocation)) {
            return false;
        }

        // If it is set, we are using replication, but we go a step further,
        // check we can read the file!
        File tmpAppPropertiesFile = new File(appPropertiesLocation);

        if (!tmpAppPropertiesFile.exists()) {
            throw new IOException("Failed to find replication configuration " + "file - application.properties, check the gitmsconfig " + "path set in .gitconfig.");
        }

        if (!tmpAppPropertiesFile.canRead()) {
            throw new IOException("Failed to read application.properties, " + "check permissions on the replication configuration file " + "- application.properties.");
        }

        synchronized (appPropertiesLocking) {
            // Keep hold of the properties info now we have a hold of it.
            appPropertiesFile = tmpAppPropertiesFile;
            appPropsLastModified = appPropertiesFile.lastModified();

            try (InputStream input = new FileInputStream(appPropertiesFile)) {
                appProperties.load(input);
            } catch (IOException e) {
                throw new IOException("Could not read " + appPropertiesFile.getAbsolutePath(), e);
            }
        }
        return isReplicationEnabled() &&
                checkIsReplicatedConfigValid(false);
    }


}
