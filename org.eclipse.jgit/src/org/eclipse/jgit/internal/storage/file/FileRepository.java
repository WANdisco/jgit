/*
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008-2010, Google Inc.
 * Copyright (C) 2006-2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006-2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/********************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.jgit.internal.storage.file;

import java.io.BufferedReader;

import static org.eclipse.jgit.lib.RefDatabase.ALL;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.eclipse.jgit.attributes.AttributesNode;
import org.eclipse.jgit.attributes.AttributesNodeProvider;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryAlreadyExistsException;
import org.eclipse.jgit.events.ConfigChangedEvent;
import org.eclipse.jgit.events.ConfigChangedListener;
import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.reftree.RefTreeDatabase;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory.AlternateHandle;
import org.eclipse.jgit.internal.storage.file.ObjectDirectory.AlternateRepository;
import org.eclipse.jgit.lib.BaseRepositoryBuilder;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.HideDotFiles;
import org.eclipse.jgit.lib.CoreConfig.SymLinks;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.jgit.util.SystemReader;

/**
 * Represents a Git repository. A repository holds all objects and refs used for
 * managing source code (could by any type of file, but source code is what
 * SCM's are typically used for).
 *
 * In Git terms all data is stored in GIT_DIR, typically a directory called
 * .git. A work tree is maintained unless the repository is a bare repository.
 * Typically the .git directory is located at the root of the work dir.
 *
 * <ul>
 * <li>GIT_DIR
 * 	<ul>
 * 		<li>objects/ - objects</li>
 * 		<li>refs/ - tags and heads</li>
 * 		<li>config - configuration</li>
 * 		<li>info/ - more configurations</li>
 * 	</ul>
 * </li>
 * </ul>
 * <p>
 * This class is thread-safe.
 * <p>
 * This implementation only handles a subtly undocumented subset of git features.
 *
 */
public class FileRepository extends Repository {
	private final FileBasedConfig systemConfig;

	private final FileBasedConfig userConfig;

	private final FileBasedConfig repoConfig;

	private final RefDatabase refs;

	private final ObjectDirectory objectDatabase;

	private FileSnapshot snapshot;

	/**
	 * Construct a representation of a Git repository.
	 * <p>
	 * The work tree, object directory, alternate object directories and index
	 * file locations are deduced from the given git directory and the default
	 * rules by running {@link FileRepositoryBuilder}. This constructor is the
	 * same as saying:
	 *
	 * <pre>
	 * new FileRepositoryBuilder().setGitDir(gitDir).build()
	 * </pre>
	 *
	 * @param gitDir
	 *            GIT_DIR (the location of the repository metadata).
	 * @throws IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 * @see FileRepositoryBuilder
	 */
	public FileRepository(final File gitDir) throws IOException {
		this(new FileRepositoryBuilder().setGitDir(gitDir).setup());
	}

	/**
	 * A convenience API for {@link #FileRepository(File)}.
	 *
	 * @param gitDir
	 *            GIT_DIR (the location of the repository metadata).
	 * @throws IOException
	 *             the repository appears to already exist but cannot be
	 *             accessed.
	 * @see FileRepositoryBuilder
	 */
	public FileRepository(final String gitDir) throws IOException {
		this(new File(gitDir));
	}

	/**
	 * Create a repository using the local file system.
	 *
	 * @param options
	 *            description of the repository's important paths.
	 * @throws IOException
	 *             the user configuration file or repository configuration file
	 *             cannot be accessed.
	 */
	public FileRepository(final BaseRepositoryBuilder options) throws IOException {
		super(options);

		if (StringUtils.isEmptyOrNull(SystemReader.getInstance().getenv(
				Constants.GIT_CONFIG_NOSYSTEM_KEY)))
			systemConfig = SystemReader.getInstance().openSystemConfig(null,
					getFS());
		else
			systemConfig = new FileBasedConfig(null, FS.DETECTED) {
				public void load() {
					// empty, do not load
				}

				public boolean isOutdated() {
					// regular class would bomb here
					return false;
				}
			};
		userConfig = SystemReader.getInstance().openUserConfig(systemConfig,
				getFS());
		repoConfig = new FileBasedConfig(userConfig, getFS().resolve(
				getDirectory(), Constants.CONFIG),
				getFS());

		loadSystemConfig();
		loadUserConfig();
		loadRepoConfig();

		repoConfig.addChangeListener(new ConfigChangedListener() {
			public void onConfigChanged(ConfigChangedEvent event) {
				fireEvent(event);
			}
		});

		final long repositoryFormatVersion = getConfig().getLong(
				ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 0);

		String reftype = repoConfig.getString(
				"extensions", null, "refsStorage"); //$NON-NLS-1$ //$NON-NLS-2$
		if (repositoryFormatVersion >= 1 && reftype != null) {
			if (StringUtils.equalsIgnoreCase(reftype, "reftree")) { //$NON-NLS-1$
				refs = new RefTreeDatabase(this, new RefDirectory(this));
			} else {
				throw new IOException(JGitText.get().unknownRepositoryFormat);
			}
		} else {
			refs = new RefDirectory(this);
		}

		objectDatabase = new ObjectDirectory(repoConfig, //
				options.getObjectDirectory(), //
				options.getAlternateObjectDirectories(), //
				getFS(), //
				new File(getDirectory(), Constants.SHALLOW));

		if (objectDatabase.exists()) {
			if (repositoryFormatVersion > 1)
				throw new IOException(MessageFormat.format(
						JGitText.get().unknownRepositoryFormat2,
						Long.valueOf(repositoryFormatVersion)));
		}

		if (!isBare())
			snapshot = FileSnapshot.save(getIndexFile());
	}

	private void loadSystemConfig() throws IOException {
		try {
			systemConfig.load();
		} catch (ConfigInvalidException e1) {
			IOException e2 = new IOException(MessageFormat.format(JGitText
					.get().systemConfigFileInvalid, systemConfig.getFile()
					.getAbsolutePath(), e1));
			e2.initCause(e1);
			throw e2;
		}
	}

	private void loadUserConfig() throws IOException {
		try {
			userConfig.load();
		} catch (ConfigInvalidException e1) {
			IOException e2 = new IOException(MessageFormat.format(JGitText
					.get().userConfigFileInvalid, userConfig.getFile()
					.getAbsolutePath(), e1));
			e2.initCause(e1);
			throw e2;
		}
	}

	private void loadRepoConfig() throws IOException {
		try {
			repoConfig.load();
		} catch (ConfigInvalidException e1) {
			IOException e2 = new IOException(JGitText.get().unknownRepositoryFormat);
			e2.initCause(e1);
			throw e2;
		}
	}

        /**
         * @param appProps
         * @param propertyName
         * @return property
         * @throws IOException
         */
        public String getProperty(File appProps, String propertyName) throws IOException{
          Properties props = new Properties();
          InputStream input = null;
          try {
            input = new FileInputStream(appProps);
            props.load(input);
            return props.getProperty(propertyName);
          } catch (IOException e) {
            throw new IOException("Could not read " + appProps.getAbsolutePath());
          } finally {
            if (input != null) {
              try {
                input.close();
              } catch (IOException ex) {
                // NO-OP
              }
            }
          }
        }

  @Override
  public void unreplicatedCreate(boolean bare) throws IOException {
    final FileBasedConfig cfg = getConfig();
    if (cfg.getFile().exists()) {
      throw new IllegalStateException(MessageFormat.format(
              JGitText.get().repositoryAlreadyExists, getDirectory()));
    }
    FileUtils.mkdirs(getDirectory(), true);
    HideDotFiles hideDotFiles = getConfig().getEnum(
            ConfigConstants.CONFIG_CORE_SECTION, null,
            ConfigConstants.CONFIG_KEY_HIDEDOTFILES,
            HideDotFiles.DOTGITONLY);
    if (hideDotFiles != HideDotFiles.FALSE && !isBare()
            && getDirectory().getName().startsWith(".")) //$NON-NLS-1$
    {
      getFS().setHidden(getDirectory(), true);
    }
    refs.create();
    objectDatabase.create();

    FileUtils.mkdir(new File(getDirectory(), "branches")); //$NON-NLS-1$
    FileUtils.mkdir(new File(getDirectory(), "hooks")); //$NON-NLS-1$

    RefUpdate head = updateRef(Constants.HEAD);
    head.disableRefLog();
    head.link(Constants.R_HEADS + Constants.MASTER);

    final boolean fileMode;
    if (getFS().supportsExecute()) {
      File tmp = File.createTempFile("try", "execute", getDirectory()); //$NON-NLS-1$ //$NON-NLS-2$

      getFS().setExecute(tmp, true);
      final boolean on = getFS().canExecute(tmp);

      getFS().setExecute(tmp, false);
      final boolean off = getFS().canExecute(tmp);
      FileUtils.delete(tmp);

      fileMode = on && !off;
    } else {
      fileMode = false;
    }

    SymLinks symLinks = SymLinks.FALSE;
    if (getFS().supportsSymlinks()) {
      File tmp = new File(getDirectory(), "tmplink"); //$NON-NLS-1$
      try {
        getFS().createSymLink(tmp, "target"); //$NON-NLS-1$
        symLinks = null;
        FileUtils.delete(tmp);
      } catch (IOException e) {
        // Normally a java.nio.file.FileSystemException
      }
    }
    if (symLinks != null) {
      cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
              ConfigConstants.CONFIG_KEY_SYMLINKS, symLinks.name()
              .toLowerCase());
    }
    cfg.setInt(ConfigConstants.CONFIG_CORE_SECTION, null,
            ConfigConstants.CONFIG_KEY_REPO_FORMAT_VERSION, 0);
    cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
            ConfigConstants.CONFIG_KEY_FILEMODE, fileMode);
    if (bare) {
      cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
              ConfigConstants.CONFIG_KEY_BARE, true);
    }
    cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
            ConfigConstants.CONFIG_KEY_LOGALLREFUPDATES, !bare);
    if (SystemReader.getInstance().isMacOS()) // Java has no other way
    {
      cfg.setBoolean(ConfigConstants.CONFIG_CORE_SECTION, null,
              ConfigConstants.CONFIG_KEY_PRECOMPOSEUNICODE, true);
    }
    if (!bare) {
      File workTree = getWorkTree();
      if (!getDirectory().getParentFile().equals(workTree)) {
        cfg.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
                ConfigConstants.CONFIG_KEY_WORKTREE, getWorkTree()
                .getAbsolutePath());
        LockFile dotGitLockFile = new LockFile(new File(workTree,
                Constants.DOT_GIT), getFS());
        try {
          if (dotGitLockFile.lock()) {
            dotGitLockFile.write(Constants.encode(Constants.GITDIR
                    + getDirectory().getAbsolutePath()));
            dotGitLockFile.commit();
          }
        } finally {
          dotGitLockFile.unlock();
        }
      }
    }
    cfg.save();
  }


        /**
         * Create a new Git repository initializing the necessary files and
         * directories.
         *
         * @param bare
         *            if true, a bare repository is created.
         *
         * @throws IOException
         *             in case of IO problem
         */
        public void create(boolean bare) throws IOException {

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

                String port = null;
                String timeout = null;
                String repoPath = null;
                String appProperties = config.getString("core", null, "gitmsconfig");


                if (!StringUtils.isEmptyOrNull(appProperties)) {
                  File appPropertiesFile = new File(appProperties);
                  if (appPropertiesFile.canRead()) {
                    port = getProperty(appPropertiesFile, "gitms.local.jetty.port");
                    timeout = getProperty(appPropertiesFile, "gitms.repo.deploy.timeout");
                    if (timeout == null || timeout.isEmpty()) {
                      timeout = "60";
                    }
                  }
                }

                if (port != null && !port.isEmpty()) {

                  BufferedReader reader = null;

                  try {
                    repoPath = URLEncoder.encode(getDirectory().getAbsolutePath(), "UTF-8");
                    URL url = new URL("http://127.0.0.1:" + port + "/gerrit/deploy?"
                            + "timeout=" + timeout + "&repoPath=" + repoPath);
                    HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
                    httpCon.setDoOutput(true);
                    httpCon.setUseCaches(false);
                    httpCon.setRequestMethod("PUT");
                    httpCon.setRequestProperty("Content-Type", "application/xml");
                    httpCon.setRequestProperty("Accept", "application/xml");
                    int response = httpCon.getResponseCode();

                    //an error may have happened, and if it did, the errorstream will be available
                    //to get more details - but if repo deployment was successful, getErrorStream
                    //will be null
                    StringBuilder responseString = new StringBuilder();
                    if (httpCon.getErrorStream() != null) {
                      reader = new BufferedReader(new InputStreamReader(httpCon.getErrorStream()));

                      String line;
                      while ((line = reader.readLine()) != null) {
                        responseString.append(line);
                        responseString.append("\n");
                      }
                      reader.close();
                    }

                    httpCon.disconnect();

                    if (response == 412) {
                      // there has been a problem with the deployment
                      throw new RepositoryAlreadyExistsException(
                        "Failure to create the git repository on the GitMS Replicator, response code: "
                          + response + "Replicator response: "
                          + responseString.toString());
                    }

                    if (response != 200) {
                      //there has been a problem with the deployment
                      throw new IOException("Failure to create the git repository on the GitMS Replicator, response code: " + response
                              + "Replicator response: " + responseString.toString());
                    }

                  } catch (RepositoryAlreadyExistsException ex) {
                    throw ex;
                  } catch (IOException ex) {
                    throw new IOException("Error with deploying repo: " + ex.toString());
                  } finally {
                    if (reader != null) {
                      reader.close();
                    }
                  }
                } else {
                  //unreplicated deployment fall through due to insufficient settings
                  //TODO: Make this report a log message
                  unreplicatedCreate(bare);
                }
	}

	/**
	 * @return the directory containing the objects owned by this repository.
	 */
	public File getObjectsDirectory() {
		return objectDatabase.getDirectory();
	}

	/**
	 * @return the object database which stores this repository's data.
	 */
	public ObjectDirectory getObjectDatabase() {
		return objectDatabase;
	}

	/** @return the reference database which stores the reference namespace. */
	public RefDatabase getRefDatabase() {
		return refs;
	}

	/**
	 * @return the configuration of this repository
	 */
	public FileBasedConfig getConfig() {
		if (systemConfig.isOutdated()) {
			try {
				loadSystemConfig();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		if (userConfig.isOutdated()) {
			try {
				loadUserConfig();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		if (repoConfig.isOutdated()) {
				try {
					loadRepoConfig();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
		}
		return repoConfig;
	}

	/**
	 * Objects known to exist but not expressed by {@link #getAllRefs()}.
	 * <p>
	 * When a repository borrows objects from another repository, it can
	 * advertise that it safely has that other repository's references, without
	 * exposing any other details about the other repository.  This may help
	 * a client trying to push changes avoid pushing more than it needs to.
	 *
	 * @return unmodifiable collection of other known objects.
	 */
	public Set<ObjectId> getAdditionalHaves() {
		HashSet<ObjectId> r = new HashSet<ObjectId>();
		for (AlternateHandle d : objectDatabase.myAlternates()) {
			if (d instanceof AlternateRepository) {
				Repository repo;

				repo = ((AlternateRepository) d).repository;
				for (Ref ref : repo.getAllRefs().values()) {
					if (ref.getObjectId() != null)
						r.add(ref.getObjectId());
					if (ref.getPeeledObjectId() != null)
						r.add(ref.getPeeledObjectId());
				}
				r.addAll(repo.getAdditionalHaves());
			}
		}
		return r;
	}

	/**
	 * Add a single existing pack to the list of available pack files.
	 *
	 * @param pack
	 *            path of the pack file to open.
	 * @throws IOException
	 *             index file could not be opened, read, or is not recognized as
	 *             a Git pack file index.
	 */
	public void openPack(final File pack) throws IOException {
		objectDatabase.openPack(pack);
	}

	@Override
	public void scanForRepoChanges() throws IOException {
		getRefDatabase().getRefs(ALL); // This will look for changes to refs
		detectIndexChanges();
	}

	/**
	 * Detect index changes.
	 */
	private void detectIndexChanges() {
		if (isBare())
			return;

		File indexFile = getIndexFile();
		if (snapshot == null)
			snapshot = FileSnapshot.save(indexFile);
		else if (snapshot.isModified(indexFile))
			notifyIndexChanged();
	}

	@Override
	public void notifyIndexChanged() {
		snapshot = FileSnapshot.save(getIndexFile());
		fireEvent(new IndexChangedEvent());
	}

	/**
	 * @param refName
	 * @return a {@link ReflogReader} for the supplied refname, or null if the
	 *         named ref does not exist.
	 * @throws IOException the ref could not be accessed.
	 */
	public ReflogReader getReflogReader(String refName) throws IOException {
		Ref ref = findRef(refName);
		if (ref != null)
			return new ReflogReaderImpl(this, ref.getName());
		return null;
	}

	@Override
	public AttributesNodeProvider createAttributesNodeProvider() {
		return new AttributesNodeProviderImpl(this);
	}

	/**
	 * Implementation a {@link AttributesNodeProvider} for a
	 * {@link FileRepository}.
	 *
	 * @author <a href="mailto:arthur.daussy@obeo.fr">Arthur Daussy</a>
	 *
	 */
	static class AttributesNodeProviderImpl implements
			AttributesNodeProvider {

		private AttributesNode infoAttributesNode;

		private AttributesNode globalAttributesNode;

		/**
		 * Constructor.
		 *
		 * @param repo
		 *            {@link Repository} that will provide the attribute nodes.
		 */
		protected AttributesNodeProviderImpl(Repository repo) {
			infoAttributesNode = new InfoAttributesNode(repo);
			globalAttributesNode = new GlobalAttributesNode(repo);
		}

		public AttributesNode getInfoAttributesNode() throws IOException {
			if (infoAttributesNode instanceof InfoAttributesNode)
				infoAttributesNode = ((InfoAttributesNode) infoAttributesNode)
						.load();
			return infoAttributesNode;
		}

		public AttributesNode getGlobalAttributesNode() throws IOException {
			if (globalAttributesNode instanceof GlobalAttributesNode)
				globalAttributesNode = ((GlobalAttributesNode) globalAttributesNode)
						.load();
			return globalAttributesNode;
		}

		static void loadRulesFromFile(AttributesNode r, File attrs)
				throws FileNotFoundException, IOException {
			if (attrs.exists()) {
				FileInputStream in = new FileInputStream(attrs);
				try {
					r.parse(in);
				} finally {
					in.close();
				}
			}
		}

	}

}
