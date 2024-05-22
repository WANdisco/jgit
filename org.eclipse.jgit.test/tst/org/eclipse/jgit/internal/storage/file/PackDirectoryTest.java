package org.eclipse.jgit.internal.storage.file;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class PackDirectoryTest extends RepositoryTestCase {

	@Parameterized.Parameter
	public boolean trustFolderStats;

	@Parameterized.Parameters(name = "core.trustfolderstat={0}")
	public static Collection<Boolean> data() {
		return Arrays.asList(true, false);
	}

	@Test
	public void testShouldNotSearchPacksAgainTheSecondTime() throws Exception {
		FileRepository bareRepository = newTestRepositoryWithOnePackfile();
		ObjectDirectory dir = bareRepository.getObjectDatabase();

		Config cfg = bareRepository.getConfig();
		File packDir = new File(dir.getDirectory(), "pack");
		PackDirectory packs = new PackDirectory(cfg, packDir);

		// Make sure that timestamps are modified and read so that a full
		// file snapshot check is performed
		fsTick(packDir);

		assertTrue(packDir.exists());
		assertTrue(packs.searchPacksAgain());
		assertFalse(packs.searchPacksAgain());
	}

	private FileRepository newTestRepositoryWithOnePackfile() throws Exception {
		FileRepository repository = createBareRepository();
		TestRepository<FileRepository> testRepository = new TestRepository<FileRepository>(repository);
		testRepository.commit();
		testRepository.packAndPrune();

		FileBasedConfig repoConfig = repository.getConfig();
		repoConfig.setBoolean(ConfigConstants.CONFIG_CORE_SECTION,null,
				ConfigConstants.CONFIG_KEY_TRUSTFOLDERSTAT,
				trustFolderStats);
		repoConfig.save();

		return repository;
	}
}
