package org.eclipse.jgit.util;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ReplicationConfigurationTest extends RepositoryTestCase {
    private Git git;


    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        git = new Git(db);
    }


    @Test
    public void test_git_config_replicatedFlagSetToTrue() throws IOException {
        StoredConfig config = git.getRepository().getConfig();
        config.setBoolean("core", null, "replicated", true);
        config.save();

        String replicatedFlagValue =
                ReplicationConfiguration.getRepositoryCoreConfigKey("replicated", git.getRepository());
        assertTrue(replicatedFlagValue.equals("true"));
    }

    @Test
    public void test_git_config_replicatedFlagSetToFalse() throws IOException {
        StoredConfig config = git.getRepository().getConfig();
        config.setBoolean("core", null, "replicated", false);
        config.save();

        String replicatedFlagValue =
                ReplicationConfiguration.getRepositoryCoreConfigKey("replicated", git.getRepository());
        assertTrue(replicatedFlagValue.equals("false"));
    }

    @Test
    public void test_git_config_replicatedFlagNotSet() {
        String replicatedFlagValue =
                ReplicationConfiguration.getRepositoryCoreConfigKey("replicated", git.getRepository());
        assertNull(replicatedFlagValue);
    }
}