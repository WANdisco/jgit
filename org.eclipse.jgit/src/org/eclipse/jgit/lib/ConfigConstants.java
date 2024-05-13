/*
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2010, Chris Aniszczyk <caniszczyk@gmail.com>
 * Copyright (C) 2012-2013, Robin Rosenberg
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
package org.eclipse.jgit.lib;

/**
 * Constants for use with the Configuration classes: section names,
 * configuration keys
 */
@SuppressWarnings("nls")
public final class ConfigConstants {
	/** The "core" section */
	public static final String CONFIG_CORE_SECTION = "core";

	/** The "branch" section */
	public static final String CONFIG_BRANCH_SECTION = "branch";

	/** The "remote" section */
	public static final String CONFIG_REMOTE_SECTION = "remote";

	/** The "diff" section */
	public static final String CONFIG_DIFF_SECTION = "diff";

	/** The "dfs" section */
	public static final String CONFIG_DFS_SECTION = "dfs";

	/**
	 * The "receive" section
	 * @since 4.6
	 */
	public static final String CONFIG_RECEIVE_SECTION = "receive";

	/** The "user" section */
	public static final String CONFIG_USER_SECTION = "user";

	/** The "gerrit" section */
	public static final String CONFIG_GERRIT_SECTION = "gerrit";

	/** The "workflow" section */
	public static final String CONFIG_WORKFLOW_SECTION = "workflow";

	/** The "submodule" section */
	public static final String CONFIG_SUBMODULE_SECTION = "submodule";

	/**
	 * The "rebase" section
	 * @since 3.2
	 */
	public static final String CONFIG_REBASE_SECTION = "rebase";

	/** The "gc" section */
	public static final String CONFIG_GC_SECTION = "gc";

	/** The "pack" section */
	public static final String CONFIG_PACK_SECTION = "pack";

	/**
	 * The "fetch" section
	 * @since 3.3
	 */
	public static final String CONFIG_FETCH_SECTION = "fetch";

	/**
	 * The "pull" section
	 * @since 3.5
	 */
	public static final String CONFIG_PULL_SECTION = "pull";

	/**
	 * The "merge" section
	 * @since 4.9
	 */
	public static final String CONFIG_MERGE_SECTION = "merge";

	/**
	 * The "filter" section
	 * @since 4.6
	 */
	public static final String CONFIG_FILTER_SECTION = "filter";

	/** The "algorithm" key */
	public static final String CONFIG_KEY_ALGORITHM = "algorithm";

	/** The "autocrlf" key */
	public static final String CONFIG_KEY_AUTOCRLF = "autocrlf";

	/**
	 * The "auto" key
	 * @since 4.6
	 */
	public static final String CONFIG_KEY_AUTO = "auto";

	/**
	 * The "autogc" key
	 * @since 4.6
	 */
	public static final String CONFIG_KEY_AUTOGC = "autogc";

	/**
	 * The "autopacklimit" key
	 * @since 4.6
	 */
	public static final String CONFIG_KEY_AUTOPACKLIMIT = "autopacklimit";

	/**
	 * The "eol" key
	 *
	 * @since 4.3
	 */
	public static final String CONFIG_KEY_EOL = "eol";

	/** The "bare" key */
	public static final String CONFIG_KEY_BARE = "bare";

	/** The "excludesfile" key */
	public static final String CONFIG_KEY_EXCLUDESFILE = "excludesfile";

	/**
	 * The "attributesfile" key
	 *
	 * @since 3.7
	 */
	public static final String CONFIG_KEY_ATTRIBUTESFILE = "attributesfile";

	/** The "filemode" key */
	public static final String CONFIG_KEY_FILEMODE = "filemode";

	/** The "logallrefupdates" key */
	public static final String CONFIG_KEY_LOGALLREFUPDATES = "logallrefupdates";

	/** The "repositoryformatversion" key */
	public static final String CONFIG_KEY_REPO_FORMAT_VERSION = "repositoryformatversion";

	/** The "worktree" key */
	public static final String CONFIG_KEY_WORKTREE = "worktree";

	/** The "blockLimit" key */
	public static final String CONFIG_KEY_BLOCK_LIMIT = "blockLimit";

	/** The "blockSize" key */
	public static final String CONFIG_KEY_BLOCK_SIZE = "blockSize";

	/**
	 * The "concurrencyLevel" key
	 *
	 * @since 4.6
	 */
	public static final String CONFIG_KEY_CONCURRENCY_LEVEL = "concurrencyLevel";

	/** The "deltaBaseCacheLimit" key */
	public static final String CONFIG_KEY_DELTA_BASE_CACHE_LIMIT = "deltaBaseCacheLimit";

	/**
	 * The "symlinks" key
	 * @since 3.3
	 */
	public static final String CONFIG_KEY_SYMLINKS = "symlinks";

	/** The "streamFileThreshold" key */
	public static final String CONFIG_KEY_STREAM_FILE_TRESHOLD = "streamFileThreshold";

	/**
	 * The "packedGitMmap" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_MMAP = "packedgitmmap";

	/**
	 * The "packedGitWindowSize" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_WINDOWSIZE = "packedgitwindowsize";

	/**
	 * The "packedGitLimit" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_LIMIT = "packedgitlimit";

	/**
	 * The "packedGitOpenFiles" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_OPENFILES = "packedgitopenfiles";

	/**
	 * The "packedGitOpenFilesCacheCleanEnabled" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_ENABLED =
			"packedgitopenfilescachecleanenabled";

	/**
	 * The "packedGitOpenFilesCacheCleanDelay" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_DELAY = "packedgitopenfilescachecleandelay";

	/**
	 * The "packedGitOpenFilesCacheCleanPeriod" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_OPENFILES_CACHE_CLEAN_PEROD = "packedgitopenfilescachecleanperiod";

	/**
	 * The "packedGitUseStrongRefs" key
	 * @since 5.1.13
	 */
	public static final String CONFIG_KEY_PACKED_GIT_USE_STRONGREFS = "packedgitusestrongrefs";

	/** The "remote" key */
	public static final String CONFIG_KEY_REMOTE = "remote";

	/** The "merge" key */
	public static final String CONFIG_KEY_MERGE = "merge";

	/** The "rebase" key */
	public static final String CONFIG_KEY_REBASE = "rebase";

	/** The "url" key */
	public static final String CONFIG_KEY_URL = "url";

	/** The "autosetupmerge" key */
	public static final String CONFIG_KEY_AUTOSETUPMERGE = "autosetupmerge";

	/** The "autosetuprebase" key */
	public static final String CONFIG_KEY_AUTOSETUPREBASE = "autosetuprebase";

	/**
	 * The "autostash" key
	 * @since 3.2
	 */
	public static final String CONFIG_KEY_AUTOSTASH = "autostash";

	/** The "name" key */
	public static final String CONFIG_KEY_NAME = "name";

	/** The "email" key */
	public static final String CONFIG_KEY_EMAIL = "email";

	/** The "false" key (used to configure {@link #CONFIG_KEY_AUTOSETUPMERGE} */
	public static final String CONFIG_KEY_FALSE = "false";

	/** The "true" key (used to configure {@link #CONFIG_KEY_AUTOSETUPMERGE} */
	public static final String CONFIG_KEY_TRUE = "true";

	/**
	 * The "always" key (used to configure {@link #CONFIG_KEY_AUTOSETUPREBASE}
	 * and {@link #CONFIG_KEY_AUTOSETUPMERGE}
	 */
	public static final String CONFIG_KEY_ALWAYS = "always";

	/** The "never" key (used to configure {@link #CONFIG_KEY_AUTOSETUPREBASE} */
	public static final String CONFIG_KEY_NEVER = "never";

	/** The "local" key (used to configure {@link #CONFIG_KEY_AUTOSETUPREBASE} */
	public static final String CONFIG_KEY_LOCAL = "local";

	/** The "createchangeid" key */
	public static final String CONFIG_KEY_CREATECHANGEID = "createchangeid";

	/** The "defaultsourceref" key */
	public static final String CONFIG_KEY_DEFBRANCHSTARTPOINT = "defbranchstartpoint";

	/** The "path" key */
	public static final String CONFIG_KEY_PATH = "path";

	/** The "update" key */
	public static final String CONFIG_KEY_UPDATE = "update";

	/**
	 * The "ignore" key
	 * @since 3.6
	 */
	public static final String CONFIG_KEY_IGNORE = "ignore";

	/** The "compression" key */
	public static final String CONFIG_KEY_COMPRESSION = "compression";

	/** The "indexversion" key */
	public static final String CONFIG_KEY_INDEXVERSION = "indexversion";

	/**
	 * The "hidedotfiles" key
	 * @since 3.5
	 */
	public static final String CONFIG_KEY_HIDEDOTFILES = "hidedotfiles";

	/**
	 * The "dirnogitlinks" key
	 * @since 4.3
	 */
	public static final String CONFIG_KEY_DIRNOGITLINKS = "dirNoGitLinks";

	/** The "precomposeunicode" key */
	public static final String CONFIG_KEY_PRECOMPOSEUNICODE = "precomposeunicode";

	/** The "pruneexpire" key */
	public static final String CONFIG_KEY_PRUNEEXPIRE = "pruneexpire";

	/**
	 * The "prunepackexpire" key
	 * @since 4.3
	 */
	public static final String CONFIG_KEY_PRUNEPACKEXPIRE = "prunepackexpire";

	/**
	 * The "logexpiry" key
	 *
	 * @since 4.7
	 */
	public static final String CONFIG_KEY_LOGEXPIRY = "logExpiry";

	/**
	 * The "autodetach" key
	 *
	 * @since 4.7
	 */
	public static final String CONFIG_KEY_AUTODETACH = "autoDetach";

	/**
	 * The "aggressiveDepth" key
	 * @since 3.6
	 */
	public static final String CONFIG_KEY_AGGRESSIVE_DEPTH = "aggressiveDepth";

	/**
	 * The "aggressiveWindow" key
	 * @since 3.6
	 */
	public static final String CONFIG_KEY_AGGRESSIVE_WINDOW = "aggressiveWindow";

	/** The "mergeoptions" key */
	public static final String CONFIG_KEY_MERGEOPTIONS = "mergeoptions";

	/** The "ff" key */
	public static final String CONFIG_KEY_FF = "ff";

	/**
	 * The "checkstat" key
	 * @since 3.0
	 */
	public static final String CONFIG_KEY_CHECKSTAT = "checkstat";

	/**
	 * The "renamelimit" key in the "diff section"
	 * @since 3.0
	 */
	public static final String CONFIG_KEY_RENAMELIMIT = "renamelimit";

	/**
	 * The "trustfolderstat" key in the "core section"
	 * @since 3.6
	 */
	public static final String CONFIG_KEY_TRUSTFOLDERSTAT = "trustfolderstat";

	/**
	 * The "supportsAtomicFileCreation" key in the "core section"
	 *
	 * @since 4.5
	 */
	public static final String CONFIG_KEY_SUPPORTSATOMICFILECREATION = "supportsatomicfilecreation";

	/**
	 * The "noprefix" key in the "diff section"
	 * @since 3.0
	 */
	public static final String CONFIG_KEY_NOPREFIX = "noprefix";

	/**
	 * A "renamelimit" value in the "diff section"
	 * @since 3.0
	 */
	public static final String CONFIG_RENAMELIMIT_COPY = "copy";

	/**
	 * A "renamelimit" value in the "diff section"
	 * @since 3.0
	 */
	public static final String CONFIG_RENAMELIMIT_COPIES = "copies";

	/**
	 * The "renames" key in the "diff section"
	 * @since 3.0
	 */
	public static final String CONFIG_KEY_RENAMES = "renames";

	/**
	 * The "inCoreLimit" key in the "merge section". It's a size limit (bytes) used to
	 * control a file to be stored in {@code Heap} or {@code LocalFile} during the merge.
	 * @since 4.9
	 */
	public static final String CONFIG_KEY_IN_CORE_LIMIT = "inCoreLimit";

	/**
	 * The "prune" key
	 * @since 3.3
	 */
	public static final String CONFIG_KEY_PRUNE = "prune";

	/**
	 * The "streamBuffer" key
	 * @since 4.0
	 */
	public static final String CONFIG_KEY_STREAM_BUFFER = "streamBuffer";

	/**
	 * The "streamRatio" key
	 * @since 4.0
	 */
	public static final String CONFIG_KEY_STREAM_RATIO = "streamRatio";

	/**
	 * Flag in the filter section whether to use JGit's implementations of
	 * filters and hooks
	 * @since 4.6
	 */
	public static final String CONFIG_KEY_USEJGITBUILTIN = "useJGitBuiltin";

	/**
	 * The "fetchRecurseSubmodules" key
	 * @since 4.7
	 */
	public static final String CONFIG_KEY_FETCH_RECURSE_SUBMODULES = "fetchRecurseSubmodules";

	/**
	 * The "recurseSubmodules" key
	 * @since 4.7
	 */
	public static final String CONFIG_KEY_RECURSE_SUBMODULES = "recurseSubmodules";

	/**
	 * The "required" key
	 * @since 4.11
	 */
	public static final String CONFIG_KEY_REQUIRED = "required";

	/**
	 * The "lfs" section
	 * @since 4.11
	 */
	public static final String CONFIG_SECTION_LFS = "lfs";

	/**
	 * The Git MultiSite Replication section
	 */
	public static final String GITMSCONFIG = "gitmsconfig";


	/**
	 * The "filesystem" section
	 * @since 5.1.9
	 */
	public static final String CONFIG_FILESYSTEM_SECTION = "filesystem";

	/**
	 * The "timestampResolution" key
	 * @since 5.1.9
	 */
	public static final String CONFIG_KEY_TIMESTAMP_RESOLUTION = "timestampResolution";

	/**
	 * The "minRacyThreshold" key
	 *
	 * @since 5.1.9
	 */
	public static final String CONFIG_KEY_MIN_RACY_THRESHOLD = "minRacyThreshold";

	/**
	 * The "jmx" section
	 * @since 5.1.13
	 */
	public static final String CONFIG_JMX_SECTION = "jmx";
}
