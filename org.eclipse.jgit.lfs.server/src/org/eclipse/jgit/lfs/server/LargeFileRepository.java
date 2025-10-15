/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;

import java.io.IOException;

/**
 * Abstraction of a repository for storing large objects
 *
 * @since 4.3
 */
public interface LargeFileRepository {

	/**
	 * Get download action
	 *
	 * @param id
	 *            id of the object to download
	 * @return Action for downloading the object
	 */
	Response.Action getDownloadAction(AnyLongObjectId id);

	/**
	 * Get upload action
	 *
	 * @param id
	 *            id of the object to upload
	 * @param size
	 *            size of the object to be uploaded
	 * @return Action for uploading the object
	 */
	Response.Action getUploadAction(AnyLongObjectId id, long size);

	/**
	 * Get verify action
	 *
	 * @param id
	 *            id of the object to be verified
	 * @return Action for verifying the object, or {@code null} if the server
	 *         doesn't support or require verification
	 */
	@Nullable
	Response.Action getVerifyAction(AnyLongObjectId id);

	/**
	 * Get size of an object
	 *
	 * @param id
	 *            id of the object
	 * @return length of the object content in bytes, -1 if the object doesn't
	 * exist, or hasn't been replicated yet for this replication group.
	 * @throws java.io.IOException
	 *             if an IO error occurred
	 */
	long getSize(AnyLongObjectId id) throws IOException;

	/**
	 * Replicated Repositories have additional information present to indicate
	 * what replica group they are in.
	 * @param replicationInfo replication group info for LFS repo.
	 */
	void setReplicationInfo(final ReplicationInfo replicationInfo);

	/**
	 * Replication projectName
	 *
	 * @return string with projectname
	 */
	String getProjectName();

	/**
	 * Replication project identity
	 *
	 * @return string with project ident.
	 */
	String getProjectIdentity();

	/**
	 * Replication Group Identifier
	 *
	 * @return string with replica group ident
	 */
	String getReplicaGroupIdentifier();

	/**
	 * Replica = true
	 *
	 * @return return true if is replicated repo.
	 */
	boolean isReplica();

	/**
	 * IsReplication indicates that the item is on disk, and also has been
	 * replicated to other nodes in the same replication group as our identifier.
	 *
	 * @param id Item ID to check
	 * @return boolean true if item represented by the id is replicated
	 */
	boolean isReplicated(AnyLongObjectId id);
}
