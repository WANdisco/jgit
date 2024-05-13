/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
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

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.EPOCH;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.CommandFailedException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.LockFailedException;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.file.FileSnapshot;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileEntry;
import org.eclipse.jgit.treewalk.FileTreeIterator.FileModeStrategy;
import org.eclipse.jgit.treewalk.WorkingTreeIterator.Entry;
import org.eclipse.jgit.util.ProcessResult.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstraction to support various file system operations not in Java.
 */
public abstract class FS {
	private static final Logger LOG = LoggerFactory.getLogger(FS.class);

	/**
	 * An empty array of entries, suitable as a return value for
	 * {@link #list(File, FileModeStrategy)}.
	 *
	 * @since 5.0
	 */
	protected static final Entry[] NO_ENTRIES = {};

	/**
	 * This class creates FS instances. It will be overridden by a Java7 variant
	 * if such can be detected in {@link #detect(Boolean)}.
	 *
	 * @since 3.0
	 */
	public static class FSFactory {
		/**
		 * Constructor
		 */
		protected FSFactory() {
			// empty
		}

		/**
		 * Detect the file system
		 *
		 * @param cygwinUsed
		 * @return FS instance
		 */
		public FS detect(Boolean cygwinUsed) {
			if (SystemReader.getInstance().isWindows()) {
				if (cygwinUsed == null)
					cygwinUsed = Boolean.valueOf(FS_Win32_Cygwin.isCygwin());
				if (cygwinUsed.booleanValue())
					return new FS_Win32_Cygwin();
				else
					return new FS_Win32();
			} else {
				return new FS_POSIX();
			}
		}
	}

	/**
	 * Result of an executed process. The caller is responsible to close the
	 * contained {@link TemporaryBuffer}s
	 *
	 * @since 4.2
	 */
	public static class ExecutionResult {
		private TemporaryBuffer stdout;

		private TemporaryBuffer stderr;

		private int rc;

		/**
		 * @param stdout
		 * @param stderr
		 * @param rc
		 */
		public ExecutionResult(TemporaryBuffer stdout, TemporaryBuffer stderr,
				int rc) {
			this.stdout = stdout;
			this.stderr = stderr;
			this.rc = rc;
		}

		/**
		 * @return buffered standard output stream
		 */
		public TemporaryBuffer getStdout() {
			return stdout;
		}

		/**
		 * @return buffered standard error stream
		 */
		public TemporaryBuffer getStderr() {
			return stderr;
		}

		/**
		 * @return the return code of the process
		 */
		public int getRc() {
			return rc;
		}
	}

	/**
	 * Attributes of FileStores on this system
	 *
	 * @since 5.1.9
	 */
	public final static class FileStoreAttributes {

		private static final Duration UNDEFINED_DURATION = Duration
				.ofNanos(Long.MAX_VALUE);

		/**
		 * Fallback filesystem timestamp resolution. The worst case timestamp
		 * resolution on FAT filesystems is 2 seconds.
		 */
		public static final Duration FALLBACK_TIMESTAMP_RESOLUTION = Duration
				.ofMillis(2000);

		/**
		 * Fallback FileStore attributes used when we can't measure the
		 * filesystem timestamp resolution. The last modified time granularity
		 * of FAT filesystems is 2 seconds.
		 */
		public static final FileStoreAttributes FALLBACK_FILESTORE_ATTRIBUTES = new FileStoreAttributes(
				FALLBACK_TIMESTAMP_RESOLUTION);

		private static final Map<FileStore, FileStoreAttributes> attributeCache = new ConcurrentHashMap<>();

		private static final SimpleLruCache<Path, FileStoreAttributes> attrCacheByPath = new SimpleLruCache<>(
				100, 0.2f);

		private static AtomicBoolean background = new AtomicBoolean();

		private static Map<FileStore, Lock> locks = new ConcurrentHashMap<>();

		private static void setBackground(boolean async) {
			background.set(async);
		}

		private static final String javaVersionPrefix = System
				.getProperty("java.vendor") + '|' //$NON-NLS-1$
				+ System.getProperty("java.version") + '|'; //$NON-NLS-1$

		private static final Duration FALLBACK_MIN_RACY_INTERVAL = Duration
				.ofMillis(10);

		/**
		 * Configures size and purge factor of the path-based cache for file
		 * system attributes. Caching of file system attributes avoids recurring
		 * lookup of @{code FileStore} of files which may be expensive on some
		 * platforms.
		 *
		 * @param maxSize
		 *            maximum size of the cache, default is 100
		 * @param purgeFactor
		 *            when the size of the map reaches maxSize the oldest
		 *            entries will be purged to free up some space for new
		 *            entries, {@code purgeFactor} is the fraction of
		 *            {@code maxSize} to purge when this happens
		 * @since 5.1.9
		 */
		public static void configureAttributesPathCache(int maxSize,
				float purgeFactor) {
			FileStoreAttributes.attrCacheByPath.configure(maxSize, purgeFactor);
		}

		/**
		 * Get the FileStoreAttributes for the given FileStore
		 *
		 * @param path
		 *            file residing in the FileStore to get attributes for
		 * @return FileStoreAttributes for the given path.
		 */
		public static FileStoreAttributes get(Path path) {
			path = path.toAbsolutePath();
			Path dir = Files.isDirectory(path) ? path : path.getParent();
			FileStoreAttributes cached = attrCacheByPath.get(dir);
			if (cached != null) {
				return cached;
			}
			FileStoreAttributes attrs = getFileStoreAttributes(dir);
			attrCacheByPath.put(dir, attrs);
			return attrs;
		}

		private static FileStoreAttributes getFileStoreAttributes(Path dir) {
			FileStore s;
			try {
				if (Files.exists(dir)) {
					s = Files.getFileStore(dir);
					FileStoreAttributes c = attributeCache.get(s);
					if (c != null) {
						return c;
					}
					if (!Files.isWritable(dir)) {
						// cannot measure resolution in a read-only directory
						LOG.debug(
								"{}: cannot measure timestamp resolution in read-only directory {}", //$NON-NLS-1$
								Thread.currentThread(), dir);
						return FALLBACK_FILESTORE_ATTRIBUTES;
					}
				} else {
					// cannot determine FileStore of an unborn directory
					LOG.debug(
							"{}: cannot measure timestamp resolution of unborn directory {}", //$NON-NLS-1$
							Thread.currentThread(), dir);
					return FALLBACK_FILESTORE_ATTRIBUTES;
				}

				CompletableFuture<Optional<FileStoreAttributes>> f = CompletableFuture
						.supplyAsync(() -> {
							Lock lock = locks.computeIfAbsent(s,
									l -> new ReentrantLock());
							if (!lock.tryLock()) {
								LOG.debug(
										"{}: couldn't get lock to measure timestamp resolution in {}", //$NON-NLS-1$
										Thread.currentThread(), dir);
								return Optional.empty();
							}
							Optional<FileStoreAttributes> attributes = Optional
									.empty();
							try {
								// Some earlier future might have set the value
								// and removed itself since we checked for the
								// value above. Hence check cache again.
								FileStoreAttributes c = attributeCache
										.get(s);
								if (c != null) {
									return Optional.of(c);
								}
								attributes = readFromConfig(s);
								if (attributes.isPresent()) {
									attributeCache.put(s, attributes.get());
									return attributes;
								}

								Optional<Duration> resolution = measureFsTimestampResolution(
										s, dir);
								if (resolution.isPresent()) {
									c = new FileStoreAttributes(
											resolution.get());
									attributeCache.put(s, c);
									// for high timestamp resolution measure
									// minimal racy interval
									if (c.fsTimestampResolution
											.toNanos() < 100_000_000L) {
										c.minimalRacyInterval = measureMinimalRacyInterval(
												dir);
									}
									if (LOG.isDebugEnabled()) {
										LOG.debug(c.toString());
									}
									saveToConfig(s, c);
								}
								attributes = Optional.of(c);
							} finally {
								lock.unlock();
								locks.remove(s);
							}
							return attributes;
						});
				f.exceptionally(e -> {
					LOG.error(e.getLocalizedMessage(), e);
					return Optional.empty();
				});
				// even if measuring in background wait a little - if the result
				// arrives, it's better than returning the large fallback
				Optional<FileStoreAttributes> d = background.get() ? f.get(
						100, TimeUnit.MILLISECONDS) : f.get();
				if (d.isPresent()) {
					return d.get();
				}
				// return fallback until measurement is finished
			} catch (IOException | InterruptedException
					| ExecutionException | CancellationException e) {
				LOG.error(e.getMessage(), e);
			} catch (TimeoutException | SecurityException e) {
				// use fallback
			}
			LOG.debug("{}: use fallback timestamp resolution for directory {}", //$NON-NLS-1$
					Thread.currentThread(), dir);
			return FALLBACK_FILESTORE_ATTRIBUTES;
		}

		@SuppressWarnings("boxing")
		private static Duration measureMinimalRacyInterval(Path dir) {
			LOG.debug("{}: start measure minimal racy interval in {}", //$NON-NLS-1$
					Thread.currentThread(), dir);
			int n = 0;
			int failures = 0;
			long racyNanos = 0;
			ArrayList<Long> deltas = new ArrayList<>();
			Path probe = dir.resolve(".probe-" + UUID.randomUUID()); //$NON-NLS-1$
			Instant end = Instant.now().plusSeconds(3);
			try {
				Files.createFile(probe);
				do {
					n++;
					write(probe, "a"); //$NON-NLS-1$
					FileSnapshot snapshot = FileSnapshot.save(probe.toFile());
					read(probe);
					write(probe, "b"); //$NON-NLS-1$
					if (!snapshot.isModified(probe.toFile())) {
						deltas.add(Long.valueOf(snapshot.lastDelta()));
						racyNanos = snapshot.lastRacyThreshold();
						failures++;
					}
				} while (Instant.now().compareTo(end) < 0);
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
				return FALLBACK_MIN_RACY_INTERVAL;
			} finally {
				deleteProbe(probe);
			}
			if (failures > 0) {
				Stats stats = new Stats();
				for (Long d : deltas) {
					stats.add(d);
				}
				LOG.debug(
						"delta [ns] since modification FileSnapshot failed to detect\n" //$NON-NLS-1$
								+ "count, failures, racy limit [ns], delta min [ns]," //$NON-NLS-1$
								+ " delta max [ns], delta avg [ns]," //$NON-NLS-1$
								+ " delta stddev [ns]\n" //$NON-NLS-1$
								+ "{}, {}, {}, {}, {}, {}, {}", //$NON-NLS-1$
						n, failures, racyNanos, stats.min(), stats.max(),
						stats.avg(), stats.stddev());
				return Duration
						.ofNanos(Double.valueOf(stats.max()).longValue());
			}
			// since no failures occurred using the measured filesystem
			// timestamp resolution there is no need for minimal racy interval
			LOG.debug("{}: no failures when measuring minimal racy interval", //$NON-NLS-1$
					Thread.currentThread());
			return Duration.ZERO;
		}

		private static void write(Path p, String body) throws IOException {
			FileUtils.mkdirs(p.getParent().toFile(), true);
			try (Writer w = new OutputStreamWriter(Files.newOutputStream(p),
					UTF_8)) {
				w.write(body);
			}
		}

		private static String read(Path p) throws IOException {
			final byte[] body = IO.readFully(p.toFile());
			return new String(body, 0, body.length, UTF_8);
		}

		private static Optional<Duration> measureFsTimestampResolution(
			FileStore s, Path dir) {
			LOG.debug("{}: start measure timestamp resolution {} in {}", //$NON-NLS-1$
					Thread.currentThread(), s, dir);
			Path probe = dir.resolve(".probe-" + UUID.randomUUID()); //$NON-NLS-1$
			try {
				Files.createFile(probe);
				FileTime t1 = Files.getLastModifiedTime(probe);
				FileTime t2 = t1;
				Instant t1i = t1.toInstant();
				for (long i = 1; t2.compareTo(t1) <= 0; i += 1 + i / 20) {
					Files.setLastModifiedTime(probe,
							FileTime.from(t1i.plusNanos(i * 1000)));
					t2 = Files.getLastModifiedTime(probe);
				}
				Duration fsResolution = Duration.between(t1.toInstant(), t2.toInstant());
				Duration clockResolution = measureClockResolution();
				fsResolution = fsResolution.plus(clockResolution);
				LOG.debug("{}: end measure timestamp resolution {} in {}", //$NON-NLS-1$
						Thread.currentThread(), s, dir);
				return Optional.of(fsResolution);
			} catch (AccessDeniedException e) {
				LOG.warn(e.getLocalizedMessage(), e); // see bug 548648
			} catch (IOException e) {
				LOG.error(e.getLocalizedMessage(), e);
			} finally {
				deleteProbe(probe);
			}
			return Optional.empty();
		}

		private static Duration measureClockResolution() {
			Duration clockResolution = Duration.ZERO;
			for (int i = 0; i < 10; i++) {
				Instant t1 = Instant.now();
				Instant t2 = t1;
				while (t2.compareTo(t1) <= 0) {
					t2 = Instant.now();
				}
				Duration r = Duration.between(t1, t2);
				if (r.compareTo(clockResolution) > 0) {
					clockResolution = r;
				}
			}
			return clockResolution;
		}

		private static void deleteProbe(Path probe) {
			try {
				FileUtils.delete(probe.toFile(),
						FileUtils.SKIP_MISSING | FileUtils.RETRY);
			} catch (IOException e) {
				LOG.error(e.getMessage(), e);
			}
		}

		private static Optional<FileStoreAttributes> readFromConfig(
				FileStore s) {
			StoredConfig userConfig;
			try {
				userConfig = SystemReader.getInstance().getUserConfig();
			} catch (IOException | ConfigInvalidException e) {
				LOG.error(JGitText.get().readFileStoreAttributesFailed, e);
				return Optional.empty();
			}
			String key = getConfigKey(s);
			Duration resolution = Duration.ofNanos(userConfig.getTimeUnit(
					ConfigConstants.CONFIG_FILESYSTEM_SECTION, key,
					ConfigConstants.CONFIG_KEY_TIMESTAMP_RESOLUTION,
					UNDEFINED_DURATION.toNanos(), TimeUnit.NANOSECONDS));
			if (UNDEFINED_DURATION.equals(resolution)) {
				return Optional.empty();
			}
			Duration minRacyThreshold = Duration.ofNanos(userConfig.getTimeUnit(
					ConfigConstants.CONFIG_FILESYSTEM_SECTION, key,
					ConfigConstants.CONFIG_KEY_MIN_RACY_THRESHOLD,
					UNDEFINED_DURATION.toNanos(), TimeUnit.NANOSECONDS));
			FileStoreAttributes c = new FileStoreAttributes(resolution);
			if (!UNDEFINED_DURATION.equals(minRacyThreshold)) {
				c.minimalRacyInterval = minRacyThreshold;
			}
			return Optional.of(c);
		}

		private static void saveToConfig(FileStore s,
				FileStoreAttributes c) {
			StoredConfig userConfig;
			try {
				userConfig = SystemReader.getInstance().getUserConfig();
			} catch (IOException | ConfigInvalidException e) {
				LOG.error(JGitText.get().saveFileStoreAttributesFailed, e);
				return;
			}
			long resolution = c.getFsTimestampResolution().toNanos();
			TimeUnit resolutionUnit = getUnit(resolution);
			long resolutionValue = resolutionUnit.convert(resolution,
					TimeUnit.NANOSECONDS);

			long minRacyThreshold = c.getMinimalRacyInterval().toNanos();
			TimeUnit minRacyThresholdUnit = getUnit(minRacyThreshold);
			long minRacyThresholdValue = minRacyThresholdUnit
					.convert(minRacyThreshold, TimeUnit.NANOSECONDS);

			final int max_retries = 5;
			int retries = 0;
			boolean succeeded = false;
			String key = getConfigKey(s);
			while (!succeeded && retries < max_retries) {
				try {
					userConfig.load();
					userConfig.setString(
							ConfigConstants.CONFIG_FILESYSTEM_SECTION, key,
							ConfigConstants.CONFIG_KEY_TIMESTAMP_RESOLUTION,
							String.format("%d %s", //$NON-NLS-1$
									Long.valueOf(resolutionValue),
									resolutionUnit.name().toLowerCase()));
					userConfig.setString(
							ConfigConstants.CONFIG_FILESYSTEM_SECTION, key,
							ConfigConstants.CONFIG_KEY_MIN_RACY_THRESHOLD,
							String.format("%d %s", //$NON-NLS-1$
									Long.valueOf(minRacyThresholdValue),
									minRacyThresholdUnit.name().toLowerCase()));
					userConfig.save();
					succeeded = true;
				} catch (LockFailedException e) {
					// race with another thread, wait a bit and try again
					try {
						retries++;
						if (retries < max_retries) {
							Thread.sleep(100);
							LOG.debug("locking {} failed, retries {}/{}", //$NON-NLS-1$
									userConfig, Integer.valueOf(retries),
									Integer.valueOf(max_retries));
						} else {
							LOG.warn(MessageFormat.format(
									JGitText.get().lockFailedRetry, userConfig,
									Integer.valueOf(retries)));
						}
					} catch (InterruptedException e1) {
						Thread.currentThread().interrupt();
						break;
					}
				} catch (IOException e) {
					LOG.error(MessageFormat.format(
							JGitText.get().cannotSaveConfig, userConfig), e);
					break;
				} catch (ConfigInvalidException e) {
					LOG.error(MessageFormat.format(
							JGitText.get().repositoryConfigFileInvalid,
							userConfig, e.getMessage()));
					break;
				}
			}
		}

		private static String getConfigKey(FileStore s) {
			final String storeKey;
			if (SystemReader.getInstance().isWindows()) {
				Object attribute = null;
				try {
					attribute = s.getAttribute("volume:vsn"); //$NON-NLS-1$
				} catch (IOException ignored) {
					// ignore
				}
				if (attribute instanceof Integer) {
					storeKey = attribute.toString();
				} else {
					storeKey = s.name();
				}
			} else {
				storeKey = s.name();
			}
			return javaVersionPrefix + storeKey;
		}

		private static TimeUnit getUnit(long nanos) {
			TimeUnit unit;
			if (nanos < 200_000L) {
				unit = TimeUnit.NANOSECONDS;
			} else if (nanos < 200_000_000L) {
				unit = TimeUnit.MICROSECONDS;
			} else {
				unit = TimeUnit.MILLISECONDS;
			}
			return unit;
		}

		private final @NonNull Duration fsTimestampResolution;

		private Duration minimalRacyInterval;

		/**
		 * @return the measured minimal interval after a file has been modified
		 *         in which we cannot rely on lastModified to detect
		 *         modifications
		 */
		public Duration getMinimalRacyInterval() {
			return minimalRacyInterval;
		}

		/**
		 * @return the measured filesystem timestamp resolution
		 */
		@NonNull
		public Duration getFsTimestampResolution() {
			return fsTimestampResolution;
		}

		/**
		 * Construct a FileStoreAttributeCache entry for the given filesystem
		 * timestamp resolution
		 *
		 * @param fsTimestampResolution
		 */
		public FileStoreAttributes(
				@NonNull Duration fsTimestampResolution) {
			this.fsTimestampResolution = fsTimestampResolution;
			this.minimalRacyInterval = Duration.ZERO;
		}

		@SuppressWarnings({ "nls", "boxing" })
		@Override
		public String toString() {
			return String.format(
					"FileStoreAttributes[fsTimestampResolution=%,d µs, "
							+ "minimalRacyInterval=%,d µs]",
					fsTimestampResolution.toNanos() / 1000,
					minimalRacyInterval.toNanos() / 1000);
		}

	}

	/** The auto-detected implementation selected for this operating system and JRE. */
	public static final FS DETECTED = detect();

	private volatile static FSFactory factory;

	/**
	 * Auto-detect the appropriate file system abstraction.
	 *
	 * @return detected file system abstraction
	 */
	public static FS detect() {
		return detect(null);
	}

	/**
	 * Whether FileStore attributes should be determined asynchronously
	 *
	 * @param asynch
	 *            whether FileStore attributes should be determined
	 *            asynchronously. If false access to cached attributes may block
	 *            for some seconds for the first call per FileStore
	 * @since 5.1.9
	 */
	public static void setAsyncFileStoreAttributes(boolean asynch) {
		FileStoreAttributes.setBackground(asynch);
	}

	/**
	 * Auto-detect the appropriate file system abstraction, taking into account
	 * the presence of a Cygwin installation on the system. Using jgit in
	 * combination with Cygwin requires a more elaborate (and possibly slower)
	 * resolution of file system paths.
	 *
	 * @param cygwinUsed
	 *            <ul>
	 *            <li><code>Boolean.TRUE</code> to assume that Cygwin is used in
	 *            combination with jgit</li>
	 *            <li><code>Boolean.FALSE</code> to assume that Cygwin is
	 *            <b>not</b> used with jgit</li>
	 *            <li><code>null</code> to auto-detect whether a Cygwin
	 *            installation is present on the system and in this case assume
	 *            that Cygwin is used</li>
	 *            </ul>
	 *
	 *            Note: this parameter is only relevant on Windows.
	 * @return detected file system abstraction
	 */
	public static FS detect(Boolean cygwinUsed) {
		if (factory == null) {
			factory = new FS.FSFactory();
		}
		return factory.detect(cygwinUsed);
	}

	/**
	 * Get cached FileStore attributes, if not yet available measure them using
	 * a probe file under the given directory.
	 *
	 * @param dir
	 *            the directory under which the probe file will be created to
	 *            measure the timer resolution.
	 * @return measured filesystem timestamp resolution
	 * @since 5.1.9
	 */
	public static FileStoreAttributes getFileStoreAttributes(
			@NonNull Path dir) {
		return FileStoreAttributes.get(dir);
	}

	private volatile Holder<File> userHome;

	private volatile Holder<File> gitSystemConfig;

	/**
	 * Constructs a file system abstraction.
	 */
	protected FS() {
		// Do nothing by default.
	}

	/**
	 * Initialize this FS using another's current settings.
	 *
	 * @param src
	 *            the source FS to copy from.
	 */
	protected FS(FS src) {
		userHome = src.userHome;
		gitSystemConfig = src.gitSystemConfig;
	}

	/**
	 * Create a new instance of the same type of FS.
	 *
	 * @return a new instance of the same type of FS.
	 */
	public abstract FS newInstance();

	/**
	 * Does this operating system and JRE support the execute flag on files?
	 *
	 * @return true if this implementation can provide reasonably accurate
	 *         executable bit information; false otherwise.
	 */
	public abstract boolean supportsExecute();

	/**
	 * Does this file system support atomic file creation via
	 * java.io.File#createNewFile()? In certain environments (e.g. on NFS) it is
	 * not guaranteed that when two file system clients run createNewFile() in
	 * parallel only one will succeed. In such cases both clients may think they
	 * created a new file.
	 *
	 * @return true if this implementation support atomic creation of new Files
	 *         by {@link java.io.File#createNewFile()}
	 * @since 4.5
	 */
	public boolean supportsAtomicCreateNewFile() {
		return true;
	}

	/**
	 * Does this operating system and JRE supports symbolic links. The
	 * capability to handle symbolic links is detected at runtime.
	 *
	 * @return true if symbolic links may be used
	 * @since 3.0
	 */
	public boolean supportsSymlinks() {
		return false;
	}

	/**
	 * Is this file system case sensitive
	 *
	 * @return true if this implementation is case sensitive
	 */
	public abstract boolean isCaseSensitive();

	/**
	 * Determine if the file is executable (or not).
	 * <p>
	 * Not all platforms and JREs support executable flags on files. If the
	 * feature is unsupported this method will always return false.
	 * <p>
	 * <em>If the platform supports symbolic links and <code>f</code> is a symbolic link
	 * this method returns false, rather than the state of the executable flags
	 * on the target file.</em>
	 *
	 * @param f
	 *            abstract path to test.
	 * @return true if the file is believed to be executable by the user.
	 */
	public abstract boolean canExecute(File f);

	/**
	 * Set a file to be executable by the user.
	 * <p>
	 * Not all platforms and JREs support executable flags on files. If the
	 * feature is unsupported this method will always return false and no
	 * changes will be made to the file specified.
	 *
	 * @param f
	 *            path to modify the executable status of.
	 * @param canExec
	 *            true to enable execution; false to disable it.
	 * @return true if the change succeeded; false otherwise.
	 */
	public abstract boolean setExecute(File f, boolean canExec);

	/**
	 * Get the last modified time of a file system object. If the OS/JRE support
	 * symbolic links, the modification time of the link is returned, rather
	 * than that of the link target.
	 *
	 * @param f
	 *            a {@link java.io.File} object.
	 * @return last modified time of f
	 * @throws java.io.IOException
	 * @since 3.0
	 * @deprecated use {@link #lastModifiedInstant(Path)} instead
	 */
	@Deprecated
	public long lastModified(File f) throws IOException {
		return Files.getLastModifiedTime(f.toPath(), LinkOption.NOFOLLOW_LINKS).toMillis();
	}

	/**
	 * Get the last modified time of a file system object. If the OS/JRE support
	 * symbolic links, the modification time of the link is returned, rather
	 * than that of the link target.
	 *
	 * @param p
	 *            a {@link Path} object.
	 * @return last modified time of p
	 * @since 5.1.9
	 */
	public Instant lastModifiedInstant(Path p) {
		return FileUtils.lastModifiedInstant(p);
	}

	/**
	 * Get the last modified time of a file system object. If the OS/JRE support
	 * symbolic links, the modification time of the link is returned, rather
	 * than that of the link target.
	 *
	 * @param f
	 *            a {@link File} object.
	 * @return last modified time of p
	 * @since 5.1.9
	 */
	public Instant lastModifiedInstant(File f) {
		return FileUtils.lastModifiedInstant(f.toPath());
	}

	/**
	 * Set the last modified time of a file system object. If the OS/JRE support
	 * symbolic links, the link is modified, not the target,
	 *
	 * @param f
	 *            a {@link java.io.File} object.
	 * @param time
	 *            last modified time
	 * @throws java.io.IOException
	 * @since 3.0
	 * @deprecated use {@link #setLastModified(Path, Instant)} instead
	 */
	@Deprecated
	public void setLastModified(File f, long time) throws IOException {
		FileUtils.setLastModified(f, time);
	}

	/**
	 * Set the last modified time of a file system object. If the OS/JRE support
	 * symbolic links, the link is modified, not the target,
	 *
	 * @param p
	 *            a {@link Path} object.
	 * @param time
	 *            last modified time
	 * @throws java.io.IOException
	 * @since 5.1.9
	 */
	public void setLastModified(Path p, Instant time) throws IOException {
		FileUtils.setLastModified(p, time);
	}

	/**
	 * Get the length of a file or link, If the OS/JRE supports symbolic links
	 * it's the length of the link, else the length of the target.
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return length of a file
	 * @throws java.io.IOException
	 * @since 3.0
	 */
	public long length(File path) throws IOException {
		return FileUtils.getLength(path);
	}

	/**
	 * Delete a file. Throws an exception if delete fails.
	 *
	 * @param f
	 *            a {@link java.io.File} object.
	 * @throws java.io.IOException
	 *             this may be a Java7 subclass with detailed information
	 * @since 3.3
	 */
	public void delete(File f) throws IOException {
		FileUtils.delete(f);
	}

	/**
	 * Resolve this file to its actual path name that the JRE can use.
	 * <p>
	 * This method can be relatively expensive. Computing a translation may
	 * require forking an external process per path name translated. Callers
	 * should try to minimize the number of translations necessary by caching
	 * the results.
	 * <p>
	 * Not all platforms and JREs require path name translation. Currently only
	 * Cygwin on Win32 require translation for Cygwin based paths.
	 *
	 * @param dir
	 *            directory relative to which the path name is.
	 * @param name
	 *            path name to translate.
	 * @return the translated path. <code>new File(dir,name)</code> if this
	 *         platform does not require path name translation.
	 */
	public File resolve(File dir, String name) {
		final File abspn = new File(name);
		if (abspn.isAbsolute())
			return abspn;
		return new File(dir, name);
	}

	/**
	 * Determine the user's home directory (location where preferences are).
	 * <p>
	 * This method can be expensive on the first invocation if path name
	 * translation is required. Subsequent invocations return a cached result.
	 * <p>
	 * Not all platforms and JREs require path name translation. Currently only
	 * Cygwin on Win32 requires translation of the Cygwin HOME directory.
	 *
	 * @return the user's home directory; null if the user does not have one.
	 */
	public File userHome() {
		Holder<File> p = userHome;
		if (p == null) {
			p = new Holder<>(userHomeImpl());
			userHome = p;
		}
		return p.value;
	}

	/**
	 * Set the user's home directory location.
	 *
	 * @param path
	 *            the location of the user's preferences; null if there is no
	 *            home directory for the current user.
	 * @return {@code this}.
	 */
	public FS setUserHome(File path) {
		userHome = new Holder<>(path);
		return this;
	}

	/**
	 * Does this file system have problems with atomic renames?
	 *
	 * @return true if the caller should retry a failed rename of a lock file.
	 */
	public abstract boolean retryFailedLockFileCommit();

	/**
	 * Return all the attributes of a file, without following symbolic links.
	 *
	 * @param file
	 * @return {@link BasicFileAttributes} of the file
	 * @throws IOException in case of any I/O errors accessing the file
	 *
	 * @since 4.5.6
	 */
	public BasicFileAttributes fileAttributes(File file) throws IOException {
		return FileUtils.fileAttributes(file);
	}

	/**
	 * Determine the user's home directory (location where preferences are).
	 *
	 * @return the user's home directory; null if the user does not have one.
	 */
	protected File userHomeImpl() {
		final String home = AccessController
				.doPrivileged(new PrivilegedAction<String>() {
					@Override
					public String run() {
						return System.getProperty("user.home"); //$NON-NLS-1$
					}
				});
		if (home == null || home.length() == 0)
			return null;
		return new File(home).getAbsoluteFile();
	}

	/**
	 * Searches the given path to see if it contains one of the given files.
	 * Returns the first it finds. Returns null if not found or if path is null.
	 *
	 * @param path
	 *            List of paths to search separated by File.pathSeparator
	 * @param lookFor
	 *            Files to search for in the given path
	 * @return the first match found, or null
	 * @since 3.0
	 */
	protected static File searchPath(String path, String... lookFor) {
		if (path == null)
			return null;

		for (String p : path.split(File.pathSeparator)) {
			for (String command : lookFor) {
				final File e = new File(p, command);
				if (e.isFile())
					return e.getAbsoluteFile();
			}
		}
		return null;
	}

	/**
	 * Execute a command and return a single line of output as a String
	 *
	 * @param dir
	 *            Working directory for the command
	 * @param command
	 *            as component array
	 * @param encoding
	 *            to be used to parse the command's output
	 * @return the one-line output of the command or {@code null} if there is
	 *         none
	 * @throws org.eclipse.jgit.errors.CommandFailedException
	 *             thrown when the command failed (return code was non-zero)
	 */
	@Nullable
	protected static String readPipe(File dir, String[] command,
			String encoding) throws CommandFailedException {
		return readPipe(dir, command, encoding, null);
	}

	/**
	 * Execute a command and return a single line of output as a String
	 *
	 * @param dir
	 *            Working directory for the command
	 * @param command
	 *            as component array
	 * @param encoding
	 *            to be used to parse the command's output
	 * @param env
	 *            Map of environment variables to be merged with those of the
	 *            current process
	 * @return the one-line output of the command or {@code null} if there is
	 *         none
	 * @throws org.eclipse.jgit.errors.CommandFailedException
	 *             thrown when the command failed (return code was non-zero)
	 * @since 4.0
	 */
	@Nullable
	protected static String readPipe(File dir, String[] command,
			String encoding, Map<String, String> env)
			throws CommandFailedException {
		final boolean debug = LOG.isDebugEnabled();
		try {
			if (debug) {
				LOG.debug("readpipe " + Arrays.asList(command) + "," //$NON-NLS-1$ //$NON-NLS-2$
						+ dir);
			}
			ProcessBuilder pb = new ProcessBuilder(command);
			pb.directory(dir);
			if (env != null) {
				pb.environment().putAll(env);
			}
			Process p;
			try {
				p = pb.start();
			} catch (IOException e) {
				// Process failed to start
				throw new CommandFailedException(-1, e.getMessage(), e);
			}
			p.getOutputStream().close();
			GobblerThread gobbler = new GobblerThread(p, command, dir);
			gobbler.start();
			String r = null;
			try (BufferedReader lineRead = new BufferedReader(
					new InputStreamReader(p.getInputStream(), encoding))) {
				r = lineRead.readLine();
				if (debug) {
					LOG.debug("readpipe may return '" + r + "'"); //$NON-NLS-1$ //$NON-NLS-2$
					LOG.debug("remaining output:\n"); //$NON-NLS-1$
					String l;
					while ((l = lineRead.readLine()) != null) {
						LOG.debug(l);
					}
				}
			}

			for (;;) {
				try {
					int rc = p.waitFor();
					gobbler.join();
					if (rc == 0 && !gobbler.fail.get()) {
						return r;
					} else {
						if (debug) {
							LOG.debug("readpipe rc=" + rc); //$NON-NLS-1$
						}
						throw new CommandFailedException(rc,
								gobbler.errorMessage.get(),
								gobbler.exception.get());
					}
				} catch (InterruptedException ie) {
					// Stop bothering me, I have a zombie to reap.
				}
			}
		} catch (IOException e) {
			LOG.error("Caught exception in FS.readPipe()", e); //$NON-NLS-1$
		}
		if (debug) {
			LOG.debug("readpipe returns null"); //$NON-NLS-1$
		}
		return null;
	}

	private static class GobblerThread extends Thread {

		/* The process has 5 seconds to exit after closing stderr */
		private static final int PROCESS_EXIT_TIMEOUT = 5;

		private final Process p;
		private final String desc;
		private final String dir;
		final AtomicBoolean fail = new AtomicBoolean();
		final AtomicReference<String> errorMessage = new AtomicReference<>();
		final AtomicReference<Throwable> exception = new AtomicReference<>();

		GobblerThread(Process p, String[] command, File dir) {
			this.p = p;
			this.desc = Arrays.toString(command);
			this.dir = Objects.toString(dir);
		}

		@Override
		public void run() {
			StringBuilder err = new StringBuilder();
			try (InputStream is = p.getErrorStream()) {
				int ch;
				while ((ch = is.read()) != -1) {
					err.append((char) ch);
				}
			} catch (IOException e) {
				if (waitForProcessCompletion(e) && p.exitValue() != 0) {
					setError(e, e.getMessage(), p.exitValue());
					fail.set(true);
				} else {
					// ignore. command terminated faster and stream was just closed
					// or the process didn't terminate within timeout
				}
			} finally {
				if (waitForProcessCompletion(null) && err.length() > 0) {
					setError(null, err.toString(), p.exitValue());
					if (p.exitValue() != 0) {
						fail.set(true);
					}
				}
			}
		}

		@SuppressWarnings("boxing")
		private boolean waitForProcessCompletion(IOException originalError) {
			try {
				if (!p.waitFor(PROCESS_EXIT_TIMEOUT, TimeUnit.SECONDS)) {
					setError(originalError, MessageFormat.format(
							JGitText.get().commandClosedStderrButDidntExit,
							desc, PROCESS_EXIT_TIMEOUT), -1);
					fail.set(true);
					return false;
				}
			} catch (InterruptedException e) {
				setError(originalError, MessageFormat.format(
						JGitText.get().threadInterruptedWhileRunning, desc), -1);
				fail.set(true);
				return false;
			}
			return true;
		}

		private void setError(IOException e, String message, int exitCode) {
			exception.set(e);
			errorMessage.set(MessageFormat.format(
					JGitText.get().exceptionCaughtDuringExecutionOfCommand,
					desc, dir, Integer.valueOf(exitCode), message));
		}
	}

	/**
	 * Discover the path to the Git executable.
	 *
	 * @return the path to the Git executable or {@code null} if it cannot be
	 *         determined.
	 * @since 4.0
	 */
	protected abstract File discoverGitExe();

	/**
	 * Discover the path to the system-wide Git configuration file
	 *
	 * @return the path to the system-wide Git configuration file or
	 *         {@code null} if it cannot be determined.
	 * @since 4.0
	 */
	protected File discoverGitSystemConfig() {
		File gitExe = discoverGitExe();
		if (gitExe == null) {
			return null;
		}

		// Bug 480782: Check if the discovered git executable is JGit CLI
		String v;
		try {
			v = readPipe(gitExe.getParentFile(),
				new String[] { "git", "--version" }, //$NON-NLS-1$ //$NON-NLS-2$
				Charset.defaultCharset().name());
		} catch (CommandFailedException e) {
			LOG.warn(e.getMessage());
			return null;
		}
		if (StringUtils.isEmptyOrNull(v)
				|| (v != null && v.startsWith("jgit"))) { //$NON-NLS-1$
			return null;
		}

		// Trick Git into printing the path to the config file by using "echo"
		// as the editor.
		Map<String, String> env = new HashMap<>();
		env.put("GIT_EDITOR", "echo"); //$NON-NLS-1$ //$NON-NLS-2$

		String w;
		try {
			w = readPipe(gitExe.getParentFile(),
				new String[] { "git", "config", "--system", "--edit" }, //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				Charset.defaultCharset().name(), env);
		} catch (CommandFailedException e) {
			LOG.warn(e.getMessage());
			return null;
		}
		if (StringUtils.isEmptyOrNull(w)) {
			return null;
		}

		return new File(w);
	}

	/**
	 * Get the currently used path to the system-wide Git configuration file.
	 *
	 * @return the currently used path to the system-wide Git configuration file
	 *         or {@code null} if none has been set.
	 * @since 4.0
	 */
	public File getGitSystemConfig() {
		if (gitSystemConfig == null) {
			gitSystemConfig = new Holder<>(discoverGitSystemConfig());
		}
		return gitSystemConfig.value;
	}

	/**
	 * Set the path to the system-wide Git configuration file to use.
	 *
	 * @param configFile
	 *            the path to the config file.
	 * @return {@code this}
	 * @since 4.0
	 */
	public FS setGitSystemConfig(File configFile) {
		gitSystemConfig = new Holder<>(configFile);
		return this;
	}

	/**
	 * Get the parent directory of this file's parent directory
	 *
	 * @param grandchild
	 *            a {@link java.io.File} object.
	 * @return the parent directory of this file's parent directory or
	 *         {@code null} in case there's no grandparent directory
	 * @since 4.0
	 */
	protected static File resolveGrandparentFile(File grandchild) {
		if (grandchild != null) {
			File parent = grandchild.getParentFile();
			if (parent != null)
				return parent.getParentFile();
		}
		return null;
	}

	/**
	 * Check if a file is a symbolic link and read it
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return target of link or null
	 * @throws java.io.IOException
	 * @since 3.0
	 */
	public String readSymLink(File path) throws IOException {
		return FileUtils.readSymLink(path);
	}

	/**
	 * Whether the path is a symbolic link (and we support these).
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return true if the path is a symbolic link (and we support these)
	 * @throws java.io.IOException
	 * @since 3.0
	 */
	public boolean isSymLink(File path) throws IOException {
		return FileUtils.isSymlink(path);
	}

	/**
	 * Tests if the path exists, in case of a symbolic link, true even if the
	 * target does not exist
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return true if path exists
	 * @since 3.0
	 */
	public boolean exists(File path) {
		return FileUtils.exists(path);
	}

	/**
	 * Check if path is a directory. If the OS/JRE supports symbolic links and
	 * path is a symbolic link to a directory, this method returns false.
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return true if file is a directory,
	 * @since 3.0
	 */
	public boolean isDirectory(File path) {
		return FileUtils.isDirectory(path);
	}

	/**
	 * Examine if path represents a regular file. If the OS/JRE supports
	 * symbolic links the test returns false if path represents a symbolic link.
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return true if path represents a regular file
	 * @since 3.0
	 */
	public boolean isFile(File path) {
		return FileUtils.isFile(path);
	}

	/**
	 * Whether path is hidden, either starts with . on unix or has the hidden
	 * attribute in windows
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return true if path is hidden, either starts with . on unix or has the
	 *         hidden attribute in windows
	 * @throws java.io.IOException
	 * @since 3.0
	 */
	public boolean isHidden(File path) throws IOException {
		return FileUtils.isHidden(path);
	}

	/**
	 * Set the hidden attribute for file whose name starts with a period.
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @param hidden
	 *            whether to set the file hidden
	 * @throws java.io.IOException
	 * @since 3.0
	 */
	public void setHidden(File path, boolean hidden) throws IOException {
		FileUtils.setHidden(path, hidden);
	}

	/**
	 * Create a symbolic link
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @param target
	 *            target path of the symlink
	 * @throws java.io.IOException
	 * @since 3.0
	 */
	public void createSymLink(File path, String target) throws IOException {
		FileUtils.createSymLink(path, target);
	}

	/**
	 * Create a new file. See {@link java.io.File#createNewFile()}. Subclasses
	 * of this class may take care to provide a safe implementation for this
	 * even if {@link #supportsAtomicCreateNewFile()} is <code>false</code>
	 *
	 * @param path
	 *            the file to be created
	 * @return <code>true</code> if the file was created, <code>false</code> if
	 *         the file already existed
	 * @throws java.io.IOException
	 * @deprecated use {@link #createNewFileAtomic(File)} instead
	 * @since 4.5
	 */
	@Deprecated
	public boolean createNewFile(File path) throws IOException {
		return path.createNewFile();
	}

	/**
	 * A token representing a file created by
	 * {@link #createNewFileAtomic(File)}. The token must be retained until the
	 * file has been deleted in order to guarantee that the unique file was
	 * created atomically. As soon as the file is no longer needed the lock
	 * token must be closed.
	 *
	 * @since 4.7
	 */
	public static class LockToken implements Closeable {
		private boolean isCreated;

		private Optional<Path> link;

		LockToken(boolean isCreated, Optional<Path> link) {
			this.isCreated = isCreated;
			this.link = link;
		}

		/**
		 * @return {@code true} if the file was created successfully
		 */
		public boolean isCreated() {
			return isCreated;
		}

		@Override
		public void close() {
			if (!link.isPresent()) {
				return;
			}
			Path p = link.get();
			if (!Files.exists(p)) {
				return;
			}
			try {
				Files.delete(p);
			} catch (IOException e) {
				LOG.error(MessageFormat
						.format(JGitText.get().closeLockTokenFailed, this), e);
			}
		}

		@Override
		public String toString() {
			return "LockToken [lockCreated=" + isCreated + //$NON-NLS-1$
					", link=" //$NON-NLS-1$
					+ (link.isPresent() ? link.get().getFileName() + "]" //$NON-NLS-1$
							: "<null>]"); //$NON-NLS-1$
		}
	}

	/**
	 * Create a new file. See {@link java.io.File#createNewFile()}. Subclasses
	 * of this class may take care to provide a safe implementation for this
	 * even if {@link #supportsAtomicCreateNewFile()} is <code>false</code>
	 *
	 * @param path
	 *            the file to be created
	 * @return LockToken this token must be closed after the created file was
	 *         deleted
	 * @throws IOException
	 * @since 4.7
	 */
	public LockToken createNewFileAtomic(File path) throws IOException {
		return new LockToken(path.createNewFile(), Optional.empty());
	}

	/**
	 * See
	 * {@link org.eclipse.jgit.util.FileUtils#relativizePath(String, String, String, boolean)}.
	 *
	 * @param base
	 *            The path against which <code>other</code> should be
	 *            relativized.
	 * @param other
	 *            The path that will be made relative to <code>base</code>.
	 * @return A relative path that, when resolved against <code>base</code>,
	 *         will yield the original <code>other</code>.
	 * @see FileUtils#relativizePath(String, String, String, boolean)
	 * @since 3.7
	 */
	public String relativize(String base, String other) {
		return FileUtils.relativizePath(base, other, File.separator, this.isCaseSensitive());
	}

	/**
	 * Enumerates children of a directory.
	 *
	 * @param directory
	 *            to get the children of
	 * @param fileModeStrategy
	 *            to use to calculate the git mode of a child
	 * @return an array of entries for the children
	 *
	 * @since 5.0
	 */
	public Entry[] list(File directory, FileModeStrategy fileModeStrategy) {
		final File[] all = directory.listFiles();
		if (all == null) {
			return NO_ENTRIES;
		}
		final Entry[] result = new Entry[all.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = new FileEntry(all[i], this, fileModeStrategy);
		}
		return result;
	}

	/**
	 * Checks whether the given hook is defined for the given repository, then
	 * runs it with the given arguments.
	 * <p>
	 * The hook's standard output and error streams will be redirected to
	 * <code>System.out</code> and <code>System.err</code> respectively. The
	 * hook will have no stdin.
	 * </p>
	 *
	 * @param repository
	 *            The repository for which a hook should be run.
	 * @param hookName
	 *            The name of the hook to be executed.
	 * @param args
	 *            Arguments to pass to this hook. Cannot be <code>null</code>,
	 *            but can be an empty array.
	 * @return The ProcessResult describing this hook's execution.
	 * @throws org.eclipse.jgit.api.errors.JGitInternalException
	 *             if we fail to run the hook somehow. Causes may include an
	 *             interrupted process or I/O errors.
	 * @since 4.0
	 */
	public ProcessResult runHookIfPresent(Repository repository,
			final String hookName,
			String[] args) throws JGitInternalException {
		return runHookIfPresent(repository, hookName, args, System.out, System.err,
				null);
	}

	/**
	 * Checks whether the given hook is defined for the given repository, then
	 * runs it with the given arguments.
	 *
	 * @param repository
	 *            The repository for which a hook should be run.
	 * @param hookName
	 *            The name of the hook to be executed.
	 * @param args
	 *            Arguments to pass to this hook. Cannot be <code>null</code>,
	 *            but can be an empty array.
	 * @param outRedirect
	 *            A print stream on which to redirect the hook's stdout. Can be
	 *            <code>null</code>, in which case the hook's standard output
	 *            will be lost.
	 * @param errRedirect
	 *            A print stream on which to redirect the hook's stderr. Can be
	 *            <code>null</code>, in which case the hook's standard error
	 *            will be lost.
	 * @param stdinArgs
	 *            A string to pass on to the standard input of the hook. May be
	 *            <code>null</code>.
	 * @return The ProcessResult describing this hook's execution.
	 * @throws org.eclipse.jgit.api.errors.JGitInternalException
	 *             if we fail to run the hook somehow. Causes may include an
	 *             interrupted process or I/O errors.
	 * @since 4.0
	 */
	public ProcessResult runHookIfPresent(Repository repository,
			final String hookName,
			String[] args, PrintStream outRedirect, PrintStream errRedirect,
			String stdinArgs) throws JGitInternalException {
		return new ProcessResult(Status.NOT_SUPPORTED);
	}

	/**
	 * See
	 * {@link #runHookIfPresent(Repository, String, String[], PrintStream, PrintStream, String)}
	 * . Should only be called by FS supporting shell scripts execution.
	 *
	 * @param repository
	 *            The repository for which a hook should be run.
	 * @param hookName
	 *            The name of the hook to be executed.
	 * @param args
	 *            Arguments to pass to this hook. Cannot be <code>null</code>,
	 *            but can be an empty array.
	 * @param outRedirect
	 *            A print stream on which to redirect the hook's stdout. Can be
	 *            <code>null</code>, in which case the hook's standard output
	 *            will be lost.
	 * @param errRedirect
	 *            A print stream on which to redirect the hook's stderr. Can be
	 *            <code>null</code>, in which case the hook's standard error
	 *            will be lost.
	 * @param stdinArgs
	 *            A string to pass on to the standard input of the hook. May be
	 *            <code>null</code>.
	 * @return The ProcessResult describing this hook's execution.
	 * @throws org.eclipse.jgit.api.errors.JGitInternalException
	 *             if we fail to run the hook somehow. Causes may include an
	 *             interrupted process or I/O errors.
	 * @since 4.0
	 */
	protected ProcessResult internalRunHookIfPresent(Repository repository,
			final String hookName, String[] args, PrintStream outRedirect,
			PrintStream errRedirect, String stdinArgs)
			throws JGitInternalException {
		final File hookFile = findHook(repository, hookName);
		if (hookFile == null)
			return new ProcessResult(Status.NOT_PRESENT);

		final String hookPath = hookFile.getAbsolutePath();
		final File runDirectory;
		if (repository.isBare())
			runDirectory = repository.getDirectory();
		else
			runDirectory = repository.getWorkTree();
		final String cmd = relativize(runDirectory.getAbsolutePath(),
				hookPath);
		ProcessBuilder hookProcess = runInShell(cmd, args);
		hookProcess.directory(runDirectory);
		try {
			return new ProcessResult(runProcess(hookProcess, outRedirect,
					errRedirect, stdinArgs), Status.OK);
		} catch (IOException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().exceptionCaughtDuringExecutionOfHook,
					hookName), e);
		} catch (InterruptedException e) {
			throw new JGitInternalException(MessageFormat.format(
					JGitText.get().exceptionHookExecutionInterrupted,
							hookName), e);
		}
	}


	/**
	 * Tries to find a hook matching the given one in the given repository.
	 *
	 * @param repository
	 *            The repository within which to find a hook.
	 * @param hookName
	 *            The name of the hook we're trying to find.
	 * @return The {@link java.io.File} containing this particular hook if it
	 *         exists in the given repository, <code>null</code> otherwise.
	 * @since 4.0
	 */
	public File findHook(Repository repository, String hookName) {
		File gitDir = repository.getDirectory();
		if (gitDir == null)
			return null;
		final File hookFile = new File(new File(gitDir,
				Constants.HOOKS), hookName);
		return hookFile.isFile() ? hookFile : null;
	}

	/**
	 * Runs the given process until termination, clearing its stdout and stderr
	 * streams on-the-fly.
	 *
	 * @param processBuilder
	 *            The process builder configured for this process.
	 * @param outRedirect
	 *            A OutputStream on which to redirect the processes stdout. Can
	 *            be <code>null</code>, in which case the processes standard
	 *            output will be lost.
	 * @param errRedirect
	 *            A OutputStream on which to redirect the processes stderr. Can
	 *            be <code>null</code>, in which case the processes standard
	 *            error will be lost.
	 * @param stdinArgs
	 *            A string to pass on to the standard input of the hook. Can be
	 *            <code>null</code>.
	 * @return the exit value of this process.
	 * @throws java.io.IOException
	 *             if an I/O error occurs while executing this process.
	 * @throws java.lang.InterruptedException
	 *             if the current thread is interrupted while waiting for the
	 *             process to end.
	 * @since 4.2
	 */
	public int runProcess(ProcessBuilder processBuilder,
			OutputStream outRedirect, OutputStream errRedirect, String stdinArgs)
			throws IOException, InterruptedException {
		InputStream in = (stdinArgs == null) ? null : new ByteArrayInputStream(
						stdinArgs.getBytes(UTF_8));
		return runProcess(processBuilder, outRedirect, errRedirect, in);
	}

	/**
	 * Runs the given process until termination, clearing its stdout and stderr
	 * streams on-the-fly.
	 *
	 * @param processBuilder
	 *            The process builder configured for this process.
	 * @param outRedirect
	 *            An OutputStream on which to redirect the processes stdout. Can
	 *            be <code>null</code>, in which case the processes standard
	 *            output will be lost.
	 * @param errRedirect
	 *            An OutputStream on which to redirect the processes stderr. Can
	 *            be <code>null</code>, in which case the processes standard
	 *            error will be lost.
	 * @param inRedirect
	 *            An InputStream from which to redirect the processes stdin. Can
	 *            be <code>null</code>, in which case the process doesn't get
	 *            any data over stdin. It is assumed that the whole InputStream
	 *            will be consumed by the process. The method will close the
	 *            inputstream after all bytes are read.
	 * @return the return code of this process.
	 * @throws java.io.IOException
	 *             if an I/O error occurs while executing this process.
	 * @throws java.lang.InterruptedException
	 *             if the current thread is interrupted while waiting for the
	 *             process to end.
	 * @since 4.2
	 */
	public int runProcess(ProcessBuilder processBuilder,
			OutputStream outRedirect, OutputStream errRedirect,
			InputStream inRedirect) throws IOException,
			InterruptedException {
		final ExecutorService executor = Executors.newFixedThreadPool(2);
		Process process = null;
		// We'll record the first I/O exception that occurs, but keep on trying
		// to dispose of our open streams and file handles
		IOException ioException = null;
		try {
			process = processBuilder.start();
			executor.execute(
					new StreamGobbler(process.getErrorStream(), errRedirect));
			executor.execute(
					new StreamGobbler(process.getInputStream(), outRedirect));
			@SuppressWarnings("resource") // Closed in the finally block
			OutputStream outputStream = process.getOutputStream();
			try {
				if (inRedirect != null) {
					new StreamGobbler(inRedirect, outputStream).copy();
				}
			} finally {
				try {
					outputStream.close();
				} catch (IOException e) {
					// When the process exits before consuming the input, the OutputStream
					// is replaced with the null output stream. This null output stream
					// throws IOException for all write calls. When StreamGobbler fails to
					// flush the buffer because of this, this close call tries to flush it
					// again. This causes another IOException. Since we ignore the
					// IOException in StreamGobbler, we also ignore the exception here.
				}
			}
			return process.waitFor();
		} catch (IOException e) {
			ioException = e;
		} finally {
			shutdownAndAwaitTermination(executor);
			if (process != null) {
				try {
					process.waitFor();
				} catch (InterruptedException e) {
					// Thrown by the outer try.
					// Swallow this one to carry on our cleanup, and clear the
					// interrupted flag (processes throw the exception without
					// clearing the flag).
					Thread.interrupted();
				}
				// A process doesn't clean its own resources even when destroyed
				// Explicitly try and close all three streams, preserving the
				// outer I/O exception if any.
				if (inRedirect != null) {
					inRedirect.close();
				}
				try {
					process.getErrorStream().close();
				} catch (IOException e) {
					ioException = ioException != null ? ioException : e;
				}
				try {
					process.getInputStream().close();
				} catch (IOException e) {
					ioException = ioException != null ? ioException : e;
				}
				try {
					process.getOutputStream().close();
				} catch (IOException e) {
					ioException = ioException != null ? ioException : e;
				}
				process.destroy();
			}
		}
		// We can only be here if the outer try threw an IOException.
		throw ioException;
	}

	/**
	 * Shuts down an {@link ExecutorService} in two phases, first by calling
	 * {@link ExecutorService#shutdown() shutdown} to reject incoming tasks, and
	 * then calling {@link ExecutorService#shutdownNow() shutdownNow}, if
	 * necessary, to cancel any lingering tasks. Returns true if the pool has
	 * been properly shutdown, false otherwise.
	 * <p>
	 *
	 * @param pool
	 *            the pool to shutdown
	 * @return <code>true</code> if the pool has been properly shutdown,
	 *         <code>false</code> otherwise.
	 */
	private static boolean shutdownAndAwaitTermination(ExecutorService pool) {
		boolean hasShutdown = true;
		pool.shutdown(); // Disable new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
				pool.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being canceled
				if (!pool.awaitTermination(60, TimeUnit.SECONDS))
					hasShutdown = false;
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			pool.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
			hasShutdown = false;
		}
		return hasShutdown;
	}

	/**
	 * Initialize a ProcessBuilder to run a command using the system shell.
	 *
	 * @param cmd
	 *            command to execute. This string should originate from the
	 *            end-user, and thus is platform specific.
	 * @param args
	 *            arguments to pass to command. These should be protected from
	 *            shell evaluation.
	 * @return a partially completed process builder. Caller should finish
	 *         populating directory, environment, and then start the process.
	 */
	public abstract ProcessBuilder runInShell(String cmd, String[] args);

	/**
	 * Execute a command defined by a {@link java.lang.ProcessBuilder}.
	 *
	 * @param pb
	 *            The command to be executed
	 * @param in
	 *            The standard input stream passed to the process
	 * @return The result of the executed command
	 * @throws java.lang.InterruptedException
	 * @throws java.io.IOException
	 * @since 4.2
	 */
	public ExecutionResult execute(ProcessBuilder pb, InputStream in)
			throws IOException, InterruptedException {
		try (TemporaryBuffer stdout = new TemporaryBuffer.LocalFile(null);
				TemporaryBuffer stderr = new TemporaryBuffer.Heap(1024,
						1024 * 1024)) {
			int rc = runProcess(pb, stdout, stderr, in);
			return new ExecutionResult(stdout, stderr, rc);
		}
	}

	private static class Holder<V> {
		final V value;

		Holder(V value) {
			this.value = value;
		}
	}

	/**
	 * File attributes we typically care for.
	 *
	 * @since 3.3
	 */
	public static class Attributes {

		/**
		 * @return true if this are the attributes of a directory
		 */
		public boolean isDirectory() {
			return isDirectory;
		}

		/**
		 * @return true if this are the attributes of an executable file
		 */
		public boolean isExecutable() {
			return isExecutable;
		}

		/**
		 * @return true if this are the attributes of a symbolic link
		 */
		public boolean isSymbolicLink() {
			return isSymbolicLink;
		}

		/**
		 * @return true if this are the attributes of a regular file
		 */
		public boolean isRegularFile() {
			return isRegularFile;
		}

		/**
		 * @return the time when the file was created
		 */
		public long getCreationTime() {
			return creationTime;
		}

		/**
		 * @return the time (milliseconds since 1970-01-01) when this object was
		 *         last modified
		 * @deprecated use getLastModifiedInstant instead
		 */
		@Deprecated
		public long getLastModifiedTime() {
			return lastModifiedInstant.toEpochMilli();
		}

		/**
		 * @return the time when this object was last modified
		 * @since 5.1.9
		 */
		public Instant getLastModifiedInstant() {
			return lastModifiedInstant;
		}

		private final boolean isDirectory;

		private final boolean isSymbolicLink;

		private final boolean isRegularFile;

		private final long creationTime;

		private final Instant lastModifiedInstant;

		private final boolean isExecutable;

		private final File file;

		private final boolean exists;

		/**
		 * file length
		 */
		protected long length = -1;

		final FS fs;

		Attributes(FS fs, File file, boolean exists, boolean isDirectory,
				boolean isExecutable, boolean isSymbolicLink,
				boolean isRegularFile, long creationTime,
				Instant lastModifiedInstant, long length) {
			this.fs = fs;
			this.file = file;
			this.exists = exists;
			this.isDirectory = isDirectory;
			this.isExecutable = isExecutable;
			this.isSymbolicLink = isSymbolicLink;
			this.isRegularFile = isRegularFile;
			this.creationTime = creationTime;
			this.lastModifiedInstant = lastModifiedInstant;
			this.length = length;
		}

		/**
		 * Constructor when there are issues with reading. All attributes except
		 * given will be set to the default values.
		 *
		 * @param fs
		 * @param path
		 */
		public Attributes(File path, FS fs) {
			this(fs, path, false, false, false, false, false, 0L, EPOCH, 0L);
		}

		/**
		 * @return length of this file object
		 */
		public long getLength() {
			if (length == -1)
				return length = file.length();
			return length;
		}

		/**
		 * @return the filename
		 */
		public String getName() {
			return file.getName();
		}

		/**
		 * @return the file the attributes apply to
		 */
		public File getFile() {
			return file;
		}

		boolean exists() {
			return exists;
		}
	}

	/**
	 * Get the file attributes we care for.
	 *
	 * @param path
	 *            a {@link java.io.File} object.
	 * @return the file attributes we care for.
	 * @since 3.3
	 */
	public Attributes getAttributes(File path) {
		boolean isDirectory = isDirectory(path);
		boolean isFile = !isDirectory && path.isFile();
		assert path.exists() == isDirectory || isFile;
		boolean exists = isDirectory || isFile;
		boolean canExecute = exists && !isDirectory && canExecute(path);
		boolean isSymlink = false;
		Instant lastModified = exists ? lastModifiedInstant(path) : EPOCH;
		long createTime = 0L;
		return new Attributes(this, path, exists, isDirectory, canExecute,
				isSymlink, isFile, createTime, lastModified, -1);
	}

	/**
	 * Normalize the unicode path to composed form.
	 *
	 * @param file
	 *            a {@link java.io.File} object.
	 * @return NFC-format File
	 * @since 3.3
	 */
	public File normalize(File file) {
		return file;
	}

	/**
	 * Normalize the unicode path to composed form.
	 *
	 * @param name
	 *            path name
	 * @return NFC-format string
	 * @since 3.3
	 */
	public String normalize(String name) {
		return name;
	}

	/**
	 * This runnable will consume an input stream's content into an output
	 * stream as soon as it gets available.
	 * <p>
	 * Typically used to empty processes' standard output and error, preventing
	 * them to choke.
	 * </p>
	 * <p>
	 * <b>Note</b> that a {@link StreamGobbler} will never close either of its
	 * streams.
	 * </p>
	 */
	private static class StreamGobbler implements Runnable {
		private InputStream in;

		private OutputStream out;

		public StreamGobbler(InputStream stream, OutputStream output) {
			this.in = stream;
			this.out = output;
		}

		@Override
		public void run() {
			try {
				copy();
			} catch (IOException e) {
				// Do nothing on read failure; leave streams open.
			}
		}

		void copy() throws IOException {
			boolean writeFailure = false;
			byte buffer[] = new byte[4096];
			int readBytes;
			while ((readBytes = in.read(buffer)) != -1) {
				// Do not try to write again after a failure, but keep
				// reading as long as possible to prevent the input stream
				// from choking.
				if (!writeFailure && out != null) {
					try {
						out.write(buffer, 0, readBytes);
						out.flush();
					} catch (IOException e) {
						writeFailure = true;
					}
				}
			}
		}
	}
}
