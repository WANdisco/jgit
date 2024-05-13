/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2010, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
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

package org.eclipse.jgit.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.util.FS.Attributes;

/**
 * File Utilities
 */
public class FileUtils {

	/**
	 * Option to delete given {@code File}
	 */
	public static final int NONE = 0;

	/**
	 * Option to recursively delete given {@code File}
	 */
	public static final int RECURSIVE = 1;

	/**
	 * Option to retry deletion if not successful
	 */
	public static final int RETRY = 2;

	/**
	 * Option to skip deletion if file doesn't exist
	 */
	public static final int SKIP_MISSING = 4;

	/**
	 * Option not to throw exceptions when a deletion finally doesn't succeed.
	 * @since 2.0
	 */
	public static final int IGNORE_ERRORS = 8;

	/**
	 * Option to only delete empty directories. This option can be combined with
	 * {@link #RECURSIVE}
	 *
	 * @since 3.0
	 */
	public static final int EMPTY_DIRECTORIES_ONLY = 16;

	/**
	 * Delete file or empty folder
	 *
	 * @param f
	 *            {@code File} to be deleted
	 * @throws IOException
	 *             if deletion of {@code f} fails. This may occur if {@code f}
	 *             didn't exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to delete the same file.
	 */
	public static void delete(final File f) throws IOException {
		delete(f, NONE);
	}

	/**
	 * Delete file or folder
	 *
	 * @param f
	 *            {@code File} to be deleted
	 * @param options
	 *            deletion options, {@code RECURSIVE} for recursive deletion of
	 *            a subtree, {@code RETRY} to retry when deletion failed.
	 *            Retrying may help if the underlying file system doesn't allow
	 *            deletion of files being read by another thread.
	 * @throws IOException
	 *             if deletion of {@code f} fails. This may occur if {@code f}
	 *             didn't exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to delete the same file. This
	 *             exception is not thrown when IGNORE_ERRORS is set.
	 */
	public static void delete(final File f, int options) throws IOException {
		FS fs = FS.DETECTED;
		if ((options & SKIP_MISSING) != 0 && !fs.exists(f))
			return;

		if ((options & RECURSIVE) != 0 && fs.isDirectory(f)) {
			final File[] items = f.listFiles();
			if (items != null) {
				List<File> files = new ArrayList<File>();
				List<File> dirs = new ArrayList<File>();
				for (File c : items)
					if (c.isFile())
						files.add(c);
					else
						dirs.add(c);
				// Try to delete files first, otherwise options
				// EMPTY_DIRECTORIES_ONLY|RECURSIVE will delete empty
				// directories before aborting, depending on order.
				for (File file : files)
					delete(file, options);
				for (File d : dirs)
					delete(d, options);
			}
		}

		boolean delete = false;
		if ((options & EMPTY_DIRECTORIES_ONLY) != 0) {
			if (f.isDirectory()) {
				delete = true;
			} else {
				if ((options & IGNORE_ERRORS) == 0)
					throw new IOException(MessageFormat.format(
							JGitText.get().deleteFileFailed,
							f.getAbsolutePath()));
			}
		} else {
			delete = true;
		}

		if (delete && !f.delete()) {
			if ((options & RETRY) != 0 && fs.exists(f)) {
				for (int i = 1; i < 10; i++) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// ignore
					}
					if (f.delete())
						return;
				}
			}
			if ((options & IGNORE_ERRORS) == 0)
				throw new IOException(MessageFormat.format(
						JGitText.get().deleteFileFailed, f.getAbsolutePath()));
		}
	}

	/**
	 * Rename a file or folder. If the rename fails and if we are running on a
	 * filesystem where it makes sense to repeat a failing rename then repeat
	 * the rename operation up to 9 times with 100ms sleep time between two
	 * calls. Furthermore if the destination exists and is directory hierarchy
	 * with only directories in it, the whole directory hierarchy will be
	 * deleted. If the target represents a non-empty directory structure, empty
	 * subdirectories within that structure may or may not be deleted even if
	 * the method fails. Furthermore if the destination exists and is a file
	 * then the file will be deleted and then the rename is retried.
	 * <p>
	 * This operation is <em>not</em> atomic.
	 *
	 * @see FS#retryFailedLockFileCommit()
	 * @param src
	 *            the old {@code File}
	 * @param dst
	 *            the new {@code File}
	 * @throws IOException
	 *             if the rename has failed
	 * @since 3.0
	 */
	public static void rename(final File src, final File dst)
			throws IOException {
		rename(src, dst, StandardCopyOption.REPLACE_EXISTING);
	}

	/**
	 * Rename a file or folder using the passed {@link CopyOption}s. If the
	 * rename fails and if we are running on a filesystem where it makes sense
	 * to repeat a failing rename then repeat the rename operation up to 9 times
	 * with 100ms sleep time between two calls. Furthermore if the destination
	 * exists and is a directory hierarchy with only directories in it, the
	 * whole directory hierarchy will be deleted. If the target represents a
	 * non-empty directory structure, empty subdirectories within that structure
	 * may or may not be deleted even if the method fails. Furthermore if the
	 * destination exists and is a file then the file will be replaced if
	 * {@link StandardCopyOption#REPLACE_EXISTING} has been set. If
	 * {@link StandardCopyOption#ATOMIC_MOVE} has been set the rename will be
	 * done atomically or fail with an {@link AtomicMoveNotSupportedException}
	 *
	 * @param src
	 *            the old file
	 * @param dst
	 *            the new file
	 * @param options
	 *            options to pass to
	 *            {@link Files#move(java.nio.file.Path, java.nio.file.Path, CopyOption...)}
	 * @throws AtomicMoveNotSupportedException
	 *             if file cannot be moved as an atomic file system operation
	 * @throws IOException
	 * @since 4.1
	 */
	public static void rename(final File src, final File dst,
			CopyOption... options)
					throws AtomicMoveNotSupportedException, IOException {
		int attempts = FS.DETECTED.retryFailedLockFileCommit() ? 10 : 1;
		while (--attempts >= 0) {
			try {
				Files.move(src.toPath(), dst.toPath(), options);
				return;
			} catch (AtomicMoveNotSupportedException e) {
				throw e;
			} catch (IOException e) {
				try {
					if (!dst.delete()) {
						delete(dst, EMPTY_DIRECTORIES_ONLY | RECURSIVE);
					}
					// On *nix there is no try, you do or do not
					Files.move(src.toPath(), dst.toPath(), options);
					return;
				} catch (IOException e2) {
					// ignore and continue retry
				}
			}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				throw new IOException(
						MessageFormat.format(JGitText.get().renameFileFailed,
								src.getAbsolutePath(), dst.getAbsolutePath()));
			}
		}
		throw new IOException(
				MessageFormat.format(JGitText.get().renameFileFailed,
						src.getAbsolutePath(), dst.getAbsolutePath()));
	}

	/**
	 * Creates the directory named by this abstract pathname.
	 *
	 * @param d
	 *            directory to be created
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdir(final File d)
			throws IOException {
		mkdir(d, false);
	}

	/**
	 * Creates the directory named by this abstract pathname.
	 *
	 * @param d
	 *            directory to be created
	 * @param skipExisting
	 *            if {@code true} skip creation of the given directory if it
	 *            already exists in the file system
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdir(final File d, boolean skipExisting)
			throws IOException {
		if (!d.mkdir()) {
			if (skipExisting && d.isDirectory())
				return;
			throw new IOException(MessageFormat.format(
					JGitText.get().mkDirFailed, d.getAbsolutePath()));
		}
	}

	/**
	 * Creates the directory named by this abstract pathname, including any
	 * necessary but nonexistent parent directories. Note that if this operation
	 * fails it may have succeeded in creating some of the necessary parent
	 * directories.
	 *
	 * @param d
	 *            directory to be created
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdirs(final File d) throws IOException {
		mkdirs(d, false);
	}

	/**
	 * Creates the directory named by this abstract pathname, including any
	 * necessary but nonexistent parent directories. Note that if this operation
	 * fails it may have succeeded in creating some of the necessary parent
	 * directories.
	 *
	 * @param d
	 *            directory to be created
	 * @param skipExisting
	 *            if {@code true} skip creation of the given directory if it
	 *            already exists in the file system
	 * @throws IOException
	 *             if creation of {@code d} fails. This may occur if {@code d}
	 *             did exist when the method was called. This can therefore
	 *             cause IOExceptions during race conditions when multiple
	 *             concurrent threads all try to create the same directory.
	 */
	public static void mkdirs(final File d, boolean skipExisting)
			throws IOException {
		if (!d.mkdirs()) {
			if (skipExisting && d.isDirectory())
				return;
			throw new IOException(MessageFormat.format(
					JGitText.get().mkDirsFailed, d.getAbsolutePath()));
		}
	}

	/**
	 * Atomically creates a new, empty file named by this abstract pathname if
	 * and only if a file with this name does not yet exist. The check for the
	 * existence of the file and the creation of the file if it does not exist
	 * are a single operation that is atomic with respect to all other
	 * filesystem activities that might affect the file.
	 * <p>
	 * Note: this method should not be used for file-locking, as the resulting
	 * protocol cannot be made to work reliably. The {@link FileLock} facility
	 * should be used instead.
	 *
	 * @param f
	 *            the file to be created
	 * @throws IOException
	 *             if the named file already exists or if an I/O error occurred
	 */
	public static void createNewFile(File f) throws IOException {
		if (!f.createNewFile())
			throw new IOException(MessageFormat.format(
					JGitText.get().createNewFileFailed, f));
	}

	/**
	 * Create a symbolic link
	 *
	 * @param path
	 *            the path of the symbolic link to create
	 * @param target
	 *            the target of the symbolic link
	 * @return the path to the symbolic link
	 * @throws IOException
	 * @since 4.2
	 */
	public static Path createSymLink(File path, String target)
			throws IOException {
		Path nioPath = path.toPath();
		if (Files.exists(nioPath, LinkOption.NOFOLLOW_LINKS)) {
			BasicFileAttributes attrs = Files.readAttributes(nioPath,
					BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
			if (attrs.isRegularFile() || attrs.isSymbolicLink()) {
				delete(path);
			} else {
				delete(path, EMPTY_DIRECTORIES_ONLY | RECURSIVE);
			}
		}
		if (SystemReader.getInstance().isWindows()) {
			target = target.replace('/', '\\');
		}
		Path nioTarget = new File(target).toPath();
		return Files.createSymbolicLink(nioPath, nioTarget);
	}

	/**
	 * @param path
	 * @return target path of the symlink, or null if it is not a symbolic link
	 * @throws IOException
	 * @since 3.0
	 */
	public static String readSymLink(File path) throws IOException {
		Path nioPath = path.toPath();
		Path target = Files.readSymbolicLink(nioPath);
		String targetString = target.toString();
		if (SystemReader.getInstance().isWindows()) {
			targetString = targetString.replace('\\', '/');
		} else if (SystemReader.getInstance().isMacOS()) {
			targetString = Normalizer.normalize(targetString, Form.NFC);
		}
		return targetString;
	}

	/**
	 * Create a temporary directory.
	 *
	 * @param prefix
	 * @param suffix
	 * @param dir
	 *            The parent dir, can be null to use system default temp dir.
	 * @return the temp dir created.
	 * @throws IOException
	 * @since 3.4
	 */
	public static File createTempDir(String prefix, String suffix, File dir)
			throws IOException {
		final int RETRIES = 1; // When something bad happens, retry once.
		for (int i = 0; i < RETRIES; i++) {
			File tmp = File.createTempFile(prefix, suffix, dir);
			if (!tmp.delete())
				continue;
			if (!tmp.mkdir())
				continue;
			return tmp;
		}
		throw new IOException(JGitText.get().cannotCreateTempDir);
	}

	/**
	 * This will try and make a given path relative to another.
	 * <p>
	 * For example, if this is called with the two following paths :
	 *
	 * <pre>
	 * <code>base = "c:\\Users\\jdoe\\eclipse\\git\\project"</code>
	 * <code>other = "c:\\Users\\jdoe\\eclipse\\git\\another_project\\pom.xml"</code>
	 * </pre>
	 *
	 * This will return "..\\another_project\\pom.xml".
	 * </p>
	 * <p>
	 * This method uses {@link File#separator} to split the paths into segments.
	 * </p>
	 * <p>
	 * <b>Note</b> that this will return the empty String if <code>base</code>
	 * and <code>other</code> are equal.
	 * </p>
	 *
	 * @param base
	 *            The path against which <code>other</code> should be
	 *            relativized. This will be assumed to denote the path to a
	 *            folder and not a file.
	 * @param other
	 *            The path that will be made relative to <code>base</code>.
	 * @return A relative path that, when resolved against <code>base</code>,
	 *         will yield the original <code>other</code>.
	 * @since 3.7
	 */
	public static String relativize(String base, String other) {
		if (base.equals(other))
			return ""; //$NON-NLS-1$

		final boolean ignoreCase = !FS.DETECTED.isCaseSensitive();
		final String[] baseSegments = base.split(Pattern.quote(File.separator));
		final String[] otherSegments = other.split(Pattern
				.quote(File.separator));

		int commonPrefix = 0;
		while (commonPrefix < baseSegments.length
				&& commonPrefix < otherSegments.length) {
			if (ignoreCase
					&& baseSegments[commonPrefix]
							.equalsIgnoreCase(otherSegments[commonPrefix]))
				commonPrefix++;
			else if (!ignoreCase
					&& baseSegments[commonPrefix]
							.equals(otherSegments[commonPrefix]))
				commonPrefix++;
			else
				break;
		}

		final StringBuilder builder = new StringBuilder();
		for (int i = commonPrefix; i < baseSegments.length; i++)
			builder.append("..").append(File.separator); //$NON-NLS-1$
		for (int i = commonPrefix; i < otherSegments.length; i++) {
			builder.append(otherSegments[i]);
			if (i < otherSegments.length - 1)
				builder.append(File.separator);
		}
		return builder.toString();
	}

	/**
	 * Determine if an IOException is a Stale NFS File Handle
	 *
	 * @param ioe
	 * @return a boolean true if the IOException is a Stale NFS FIle Handle
	 * @since 4.1
	 */
	public static boolean isStaleFileHandle(IOException ioe) {
		String msg = ioe.getMessage();
		return msg != null
				&& msg.toLowerCase().matches("stale .*file .*handle"); //$NON-NLS-1$
	}

	/**
	 * @param file
	 * @return {@code true} if the passed file is a symbolic link
	 */
	static boolean isSymlink(File file) {
		return Files.isSymbolicLink(file.toPath());
	}

	/**
	 * @param file
	 * @return lastModified attribute for given file, not following symbolic
	 *         links
	 * @throws IOException
	 */
	static long lastModified(File file) throws IOException {
		return Files.getLastModifiedTime(file.toPath(), LinkOption.NOFOLLOW_LINKS)
				.toMillis();
	}

	/**
	 * Return all the attributes of a file, without following symbolic links.
	 *
	 * @param file
	 * @return {@link BasicFileAttributes} of the file
	 * @throws IOException in case of any I/O errors accessing the file
	 *
	 * @since 4.5.6
	 */
	static BasicFileAttributes fileAttributes(File file) throws IOException {
		return Files.readAttributes(file.toPath(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * @param file
	 * @param time
	 * @throws IOException
	 */
	static void setLastModified(File file, long time) throws IOException {
		Files.setLastModifiedTime(file.toPath(), FileTime.fromMillis(time));
	}

	/**
	 * @param file
	 * @return {@code true} if the given file exists, not following symbolic
	 *         links
	 */
	static boolean exists(File file) {
		return Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * @param file
	 * @return {@code true} if the given file is hidden
	 * @throws IOException
	 */
	static boolean isHidden(File file) throws IOException {
		return Files.isHidden(file.toPath());
	}

	/**
	 * @param file
	 * @param hidden
	 * @throws IOException
	 * @since 4.1
	 */
	public static void setHidden(File file, boolean hidden) throws IOException {
		Files.setAttribute(file.toPath(), "dos:hidden", Boolean.valueOf(hidden), //$NON-NLS-1$
				LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * @param file
	 * @return length of the given file
	 * @throws IOException
	 * @since 4.1
	 */
	public static long getLength(File file) throws IOException {
		Path nioPath = file.toPath();
		if (Files.isSymbolicLink(nioPath))
			return Files.readSymbolicLink(nioPath).toString()
					.getBytes(Constants.CHARSET).length;
		return Files.size(nioPath);
	}

	/**
	 * @param file
	 * @return {@code true} if the given file is a directory, not following
	 *         symbolic links
	 */
	static boolean isDirectory(File file) {
		return Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * @param file
	 * @return {@code true} if the given file is a file, not following symbolic
	 *         links
	 */
	static boolean isFile(File file) {
		return Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * @param file
	 * @return {@code true} if the given file can be executed
	 * @since 4.1
	 */
	public static boolean canExecute(File file) {
		if (!isFile(file)) {
			return false;
		}
		return Files.isExecutable(file.toPath());
	}

	/**
	 * @param fs
	 * @param file
	 * @return non null attributes object
	 */
	static Attributes getFileAttributesBasic(FS fs, File file) {
		try {
			Path nioPath = file.toPath();
			BasicFileAttributes readAttributes = nioPath
					.getFileSystem()
					.provider()
					.getFileAttributeView(nioPath,
							BasicFileAttributeView.class,
							LinkOption.NOFOLLOW_LINKS).readAttributes();
			Attributes attributes = new Attributes(fs, file,
					true,
					readAttributes.isDirectory(),
					fs.supportsExecute() ? file.canExecute() : false,
					readAttributes.isSymbolicLink(),
					readAttributes.isRegularFile(), //
					readAttributes.creationTime().toMillis(), //
					readAttributes.lastModifiedTime().toMillis(),
					readAttributes.isSymbolicLink() ? Constants
							.encode(readSymLink(file)).length
							: readAttributes.size());
			return attributes;
		} catch (IOException e) {
			return new Attributes(file, fs);
		}
	}

	/**
	 * @param fs
	 * @param file
	 * @return file system attributes for the given file
	 * @since 4.1
	 */
	public static Attributes getFileAttributesPosix(FS fs, File file) {
		try {
			Path nioPath = file.toPath();
			PosixFileAttributes readAttributes = nioPath
					.getFileSystem()
					.provider()
					.getFileAttributeView(nioPath,
							PosixFileAttributeView.class,
							LinkOption.NOFOLLOW_LINKS).readAttributes();
			Attributes attributes = new Attributes(
					fs,
					file,
					true, //
					readAttributes.isDirectory(), //
					readAttributes.permissions().contains(
							PosixFilePermission.OWNER_EXECUTE),
					readAttributes.isSymbolicLink(),
					readAttributes.isRegularFile(), //
					readAttributes.creationTime().toMillis(), //
					readAttributes.lastModifiedTime().toMillis(),
					readAttributes.size());
			return attributes;
		} catch (IOException e) {
			return new Attributes(file, fs);
		}
	}

	/**
	 * @param file
	 * @return on Mac: NFC normalized {@link File}, otherwise the passed file
	 * @since 4.1
	 */
	public static File normalize(File file) {
		if (SystemReader.getInstance().isMacOS()) {
			// TODO: Would it be faster to check with isNormalized first
			// assuming normalized paths are much more common
			String normalized = Normalizer.normalize(file.getPath(),
					Normalizer.Form.NFC);
			return new File(normalized);
		}
		return file;
	}

	/**
	 * @param name
	 * @return on Mac: NFC normalized form of given name
	 * @since 4.1
	 */
	public static String normalize(String name) {
		if (SystemReader.getInstance().isMacOS()) {
			if (name == null)
				return null;
			return Normalizer.normalize(name, Normalizer.Form.NFC);
		}
		return name;
	}

	/**
	 * Best-effort variation of {@link File#getCanonicalFile()} returning the
	 * input file if the file cannot be canonicalized instead of throwing
	 * {@link IOException}.
	 *
	 * @param file
	 *            to be canonicalized; may be {@code null}
	 * @return canonicalized file, or the unchanged input file if
	 *         canonicalization failed or if {@code file == null}
	 * @throws SecurityException
	 *             if {@link File#getCanonicalFile()} throws one
	 * @since 4.2
	 */
	public static File canonicalize(File file) {
		if (file == null) {
			return null;
		}
		try {
			return file.getCanonicalFile();
		} catch (IOException e) {
			return file;
		}
	}

}
