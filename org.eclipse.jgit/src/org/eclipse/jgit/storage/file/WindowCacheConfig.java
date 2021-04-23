/*
 * Copyright (C) 2009, Google Inc.
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

package org.eclipse.jgit.storage.file;

import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_CORE_SECTION;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_DELTA_BASE_CACHE_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_LIMIT;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_MMAP;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_OPENFILES;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_DELAY;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_ENABLED;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_PEROD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_WINDOWSIZE;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_STREAM_FILE_TRESHOLD;
import static org.eclipse.jgit.lib.ConfigConstants.CONFIG_KEY_PACKED_GIT_USE_STRONGREFS;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.internal.storage.file.WindowCache;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.util.SystemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Configuration parameters for JVM-wide buffer cache used by JGit.
 */
public class WindowCacheConfig {

	private final static Logger LOG = LoggerFactory
			.getLogger(WindowCacheConfig.class);

	/** 1024 (number of bytes in one kibibyte/kilobyte) */
	public static final int KB = 1024;

	/** 1024 {@link #KB} (number of bytes in one mebibyte/megabyte) */
	public static final int MB = 1024 * KB;

	private static final long DEFAULT_CACHE_CLEAN_DELAY = TimeUnit.HOURS.toMillis(1L);

	private static final long DEFAULT_CACHE_CLEAN_PERIOD = TimeUnit.HOURS.toMillis(24L);

	private static final boolean DEFAULT_CACHE_CLEAN_ENABLED = true;

	private static final int DEFAULT_PACKED_GIT_OPEN_FILES = 128;

	private static final long DEFAULT_PACKED_GIT_LIMIT = 10 * MB;

	private static final boolean DEFAULT_USE_STRONG_REFS = false;

	private static final int DEFAULT_PACKED_GIT_WINDOW_SIZE = 8 * KB;

	private static final boolean DEFAULT_PACKED_GIT_MMAP = false;

	private static final int DEFAULT_DELTA_BASE_CACHE_LIMIT = 10 * MB;

	private static final int DEFAULT_STREAM_FILE_THRESHOLD = PackConfig.DEFAULT_BIG_FILE_THRESHOLD;

	private long packedGitOpenFilesCacheCleanDelay;

	private long packedGitOpenFilesCacheCleanPeriod;

	private boolean packedGitOpenFilesCacheCleanEnabled;

	private int packedGitOpenFiles;

	private long packedGitLimit;

	private boolean useStrongRefs;

	private int packedGitWindowSize;

	private boolean packedGitMMAP;

	private int deltaBaseCacheLimit;

	private int streamFileThreshold;

	/**
	 * Create a default configuration.
	 */
	public WindowCacheConfig() {
		StoredConfig config;
		try {
			config = SystemReader.getInstance().getUserConfig();
			fromConfig(config);
		} catch (ConfigInvalidException | IOException e) {
			LOG.error("Something went wrong whilst loading system config. Using defaults instead", e);

			packedGitOpenFilesCacheCleanDelay = DEFAULT_CACHE_CLEAN_DELAY;
			packedGitOpenFilesCacheCleanPeriod = DEFAULT_CACHE_CLEAN_PERIOD;
			packedGitOpenFilesCacheCleanEnabled = DEFAULT_CACHE_CLEAN_ENABLED;
			packedGitOpenFiles = DEFAULT_PACKED_GIT_OPEN_FILES;
			packedGitLimit = DEFAULT_PACKED_GIT_LIMIT;
			useStrongRefs = DEFAULT_USE_STRONG_REFS;
			packedGitWindowSize = DEFAULT_PACKED_GIT_WINDOW_SIZE;
			packedGitMMAP = DEFAULT_PACKED_GIT_MMAP;
			deltaBaseCacheLimit = DEFAULT_DELTA_BASE_CACHE_LIMIT;
			streamFileThreshold = DEFAULT_STREAM_FILE_THRESHOLD;
		}
	}

	/**
	 * Get delay millis for {@link WindowCache} cleaner TimerTask.
	 *
	 * @return delay millis <b>Default is 1 second</b>.
	 */
	public long getPackedGitOpenFilesCacheCleanDelay() {
		return packedGitOpenFilesCacheCleanDelay;
	}

	/**
	 * Get period between runs of {@link WindowCache} cleaner TimerTask.
	 *
	 * @return period millis <b>Default is 1 day</b>.
	 */
	public long getPackedGitOpenFilesCacheCleanPeriod() {
		return packedGitOpenFilesCacheCleanPeriod;
	}

	/**
	 * Is {@link WindowCache} cleaner TimerTask enabled.
	 *
	 * @return True if {@link WindowCache} cleaner TimerTask is enabled.
	 */
	public boolean isPackedGitOpenFilesCacheCleanEnabled() {
		return packedGitOpenFilesCacheCleanEnabled;
	}

	/**
	 * Set delay millis for {@link WindowCache} cleaner TimerTask.
	 *
	 * @param packedGitOpenFilesCacheCleanDelay delay millis
	 */
	public void setPackedGitOpenFilesCacheCleanDelay(long packedGitOpenFilesCacheCleanDelay) {
		this.packedGitOpenFilesCacheCleanDelay = packedGitOpenFilesCacheCleanDelay;
	}

	/**
	 * Set period between runs of {@link WindowCache} cleaner TimerTask.
	 *
	 * @param packedGitOpenFilesCacheCleanPeriod period millis
	 */
	public void setPackedGitOpenFilesCacheCleanPeriod(long packedGitOpenFilesCacheCleanPeriod) {
		this.packedGitOpenFilesCacheCleanPeriod = packedGitOpenFilesCacheCleanPeriod;
	}

	/**
	 * Set {@link WindowCache} cleaner TimerTask enabled.
	 *
	 * @param packedGitOpenFilesCacheCleanEnabled enabled
	 */
	public void setPackedGitOpenFilesCacheCleanEnabled(boolean packedGitOpenFilesCacheCleanEnabled) {
		this.packedGitOpenFilesCacheCleanEnabled = packedGitOpenFilesCacheCleanEnabled;
	}

	/**
	 * Get maximum number of streams to open at a time.
	 *
	 * @return maximum number of streams to open at a time. Open packs count
	 *         against the process limits. <b>Default is 128.</b>
	 */
	public int getPackedGitOpenFiles() {
		return packedGitOpenFiles;
	}

	/**
	 * Set maximum number of streams to open at a time.
	 *
	 * @param fdLimit
	 *            maximum number of streams to open at a time. Open packs count
	 *            against the process limits
	 */
	public void setPackedGitOpenFiles(int fdLimit) {
		packedGitOpenFiles = fdLimit;
	}

	/**
	 * Get maximum number bytes of heap memory to dedicate to caching pack file
	 * data.
	 *
	 * @return maximum number bytes of heap memory to dedicate to caching pack
	 *         file data. <b>Default is 10 MB.</b>
	 */
	public long getPackedGitLimit() {
		return packedGitLimit;
	}

	/**
	 * Set maximum number bytes of heap memory to dedicate to caching pack file
	 * data.
	 *
	 * @param newLimit
	 *            maximum number bytes of heap memory to dedicate to caching
	 *            pack file data.
	 */
	public void setPackedGitLimit(long newLimit) {
		packedGitLimit = newLimit;
	}

	/**
	 * Get whether the window cache should use strong references or
	 * SoftReferences
	 *
	 * @return {@code true} if the window cache should use strong references,
	 *         otherwise it will use {@link java.lang.ref.SoftReference}s
	 * @since 5.1.13
	 */
	public boolean isPackedGitUseStrongRefs() {
		return useStrongRefs;
	}

	/**
	 * Set if the cache should use strong refs or soft refs
	 *
	 * @param useStrongRefs
	 *            if @{code true} the cache strongly references cache pages
	 *            otherwise it uses {@link java.lang.ref.SoftReference}s which
	 *            can be evicted by the Java gc if heap is almost full
	 * @since 5.1.13
	 */
	public void setPackedGitUseStrongRefs(boolean useStrongRefs) {
		this.useStrongRefs = useStrongRefs;
	}

	/**
	 * Get size in bytes of a single window mapped or read in from the pack
	 * file.
	 *
	 * @return size in bytes of a single window mapped or read in from the pack
	 *         file. <b>Default is 8 KB.</b>
	 */
	public int getPackedGitWindowSize() {
		return packedGitWindowSize;
	}

	/**
	 * Set size in bytes of a single window read in from the pack file.
	 *
	 * @param newSize
	 *            size in bytes of a single window read in from the pack file.
	 */
	public void setPackedGitWindowSize(int newSize) {
		packedGitWindowSize = newSize;
	}

	/**
	 * Whether to use Java NIO virtual memory mapping for windows
	 *
	 * @return {@code true} enables use of Java NIO virtual memory mapping for
	 *         windows; false reads entire window into a byte[] with standard
	 *         read calls. <b>Default false.</b>
	 */
	public boolean isPackedGitMMAP() {
		return packedGitMMAP;
	}

	/**
	 * Set whether to enable use of Java NIO virtual memory mapping for windows
	 *
	 * @param usemmap
	 *            {@code true} enables use of Java NIO virtual memory mapping
	 *            for windows; false reads entire window into a byte[] with
	 *            standard read calls.
	 */
	public void setPackedGitMMAP(boolean usemmap) {
		packedGitMMAP = usemmap;
	}

	/**
	 * Get maximum number of bytes to cache in delta base cache for inflated,
	 * recently accessed objects, without delta chains.
	 *
	 * @return maximum number of bytes to cache in delta base cache for
	 *         inflated, recently accessed objects, without delta chains.
	 *         <b>Default 10 MB.</b>
	 */
	public int getDeltaBaseCacheLimit() {
		return deltaBaseCacheLimit;
	}

	/**
	 * Set maximum number of bytes to cache in delta base cache for inflated,
	 * recently accessed objects, without delta chains.
	 *
	 * @param newLimit
	 *            maximum number of bytes to cache in delta base cache for
	 *            inflated, recently accessed objects, without delta chains.
	 */
	public void setDeltaBaseCacheLimit(int newLimit) {
		deltaBaseCacheLimit = newLimit;
	}

	/**
	 * Get the size threshold beyond which objects must be streamed.
	 *
	 * @return the size threshold beyond which objects must be streamed.
	 */
	public int getStreamFileThreshold() {
		return streamFileThreshold;
	}

	/**
	 * Set new byte limit for objects that must be streamed.
	 *
	 * @param newLimit
	 *            new byte limit for objects that must be streamed. Objects
	 *            smaller than this size can be obtained as a contiguous byte
	 *            array, while objects bigger than this size require using an
	 *            {@link org.eclipse.jgit.lib.ObjectStream}.
	 */
	public void setStreamFileThreshold(int newLimit) {
		streamFileThreshold = newLimit;
	}

	/**
	 * Update properties by setting fields from the configuration.
	 * <p>
	 * If a property is not defined in the configuration, then it is left
	 * unmodified.
	 *
	 * @param rc
	 *            configuration to read properties from.
	 * @return {@code this}.
	 * @since 3.0
	 */
	public WindowCacheConfig fromConfig(Config rc) {
		setPackedGitUseStrongRefs(rc.getBoolean(CONFIG_CORE_SECTION,
				CONFIG_KEY_PACKED_GIT_USE_STRONGREFS,
				DEFAULT_USE_STRONG_REFS));
		setPackedGitOpenFilesCacheCleanEnabled(rc.getBoolean(CONFIG_CORE_SECTION, null,
		        CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_ENABLED, DEFAULT_CACHE_CLEAN_ENABLED));
		setPackedGitOpenFilesCacheCleanDelay(rc.getLong(CONFIG_CORE_SECTION, null,
		        CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_DELAY, DEFAULT_CACHE_CLEAN_DELAY));
		setPackedGitOpenFilesCacheCleanPeriod(rc.getLong(CONFIG_CORE_SECTION, null,
		        CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_PEROD, DEFAULT_CACHE_CLEAN_PERIOD));
		setPackedGitOpenFiles(rc.getInt(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_PACKED_GIT_OPENFILES, DEFAULT_PACKED_GIT_OPEN_FILES));
		setPackedGitLimit(rc.getLong(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_PACKED_GIT_LIMIT, DEFAULT_PACKED_GIT_LIMIT));
		setPackedGitWindowSize(rc.getInt(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_PACKED_GIT_WINDOWSIZE, DEFAULT_PACKED_GIT_WINDOW_SIZE));
		setPackedGitMMAP(rc.getBoolean(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_PACKED_GIT_MMAP, DEFAULT_PACKED_GIT_MMAP));
		setDeltaBaseCacheLimit(rc.getInt(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_DELTA_BASE_CACHE_LIMIT, DEFAULT_DELTA_BASE_CACHE_LIMIT));

		long maxMem = Runtime.getRuntime().maxMemory();
		long sft = rc.getLong(CONFIG_CORE_SECTION, null,
				CONFIG_KEY_STREAM_FILE_TRESHOLD, DEFAULT_STREAM_FILE_THRESHOLD);
		sft = Math.min(sft, maxMem / 4); // don't use more than 1/4 of the heap
		sft = Math.min(sft, Integer.MAX_VALUE); // cannot exceed array length
		setStreamFileThreshold((int) sft);
		return this;
	}

	/**
	 * Install this configuration as the live settings.
	 * <p>
	 * The new configuration is applied immediately. If the new limits are
	 * smaller than what what is currently cached, older entries will be purged
	 * as soon as possible to allow the cache to meet the new limit.
	 *
	 * @since 3.0
	 */
	@SuppressWarnings("deprecation")
	public void install() {
		WindowCache.reconfigure(this);
	}
}
