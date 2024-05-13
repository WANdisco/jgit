/*
 * Copyright (C) 2015, Matthias Sohn <matthias.sohn@sap.com>
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
     * @param id id of the object to download
     * @return Action for downloading the object
     */
    public Response.Action getDownloadAction(AnyLongObjectId id);

    /**
     * Get upload action
     *
     * @param id   id of the object to upload
     * @param size size of the object to be uploaded
     * @return Action for uploading the object
     */
    public Response.Action getUploadAction(AnyLongObjectId id, long size);

    /**
     * Get verify action
     *
     * @param id id of the object to be verified
     * @return Action for verifying the object, or {@code null} if the server
     * doesn't support or require verification
     */
    public @Nullable
    Response.Action getVerifyAction(AnyLongObjectId id);

    /**
     * Get size of an object
     *
     * @param id id of the object
     * @return length of the object content in bytes, -1 if the object doesn't
     * exist, or hasn't been replicated yet for this replication group.
     * @throws IOException
     */
    public long getSize(AnyLongObjectId id) throws IOException;

    /**
     * Replicated Repositories have additional information present to indicate
     * what replica group they are in.
     * @param replicationInfo
     */
    public void setReplicationInfo(final ReplicationInfo replicationInfo);

    /**
     * Replication projectName
     *
     * @return string with projectname
     */
    public String getProjectName();

    /**
     * Replication project identity
     *
     * @return string with project ident.
     */
    public String getProjectIdentity();

    /**
     * Replication Group Identifier
     *
     * @return string with replica group ident
     */
    public String getReplicaGroupIdentifier();

    /**
     * Replica = true
     *
     * @return return true if is replicated repo.
     */
    public boolean isReplica();

    /**
     * IsReplication indicates that the item is on disk, and also has been
     * replicated to other nodes in the same replication group as our identifier.
     *
     * @param id
     * @return boolean true if item represented by the id is replicated
     */
    public boolean isReplicated(AnyLongObjectId id);
}
