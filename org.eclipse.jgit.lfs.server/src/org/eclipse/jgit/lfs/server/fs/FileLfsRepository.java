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

package org.eclipse.jgit.lfs.server.fs;

import com.google.common.base.Strings;
import com.wandisco.gerrit.gitms.shared.api.lfs.dto.LfsObjectInfoDTO;
import com.wandisco.gerrit.gitms.shared.api.lfs.dto.LfsRequestTypeProperties.LfsItemType;
import com.wandisco.gerrit.gitms.shared.lfs.GitLfsCache;
import com.wandisco.gerrit.gitms.shared.lfs.GitLfsCacheAccessor;
import static org.eclipse.jgit.util.HttpSupport.HDR_AUTHORIZATION;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lfs.lib.AnyLongObjectId;
import org.eclipse.jgit.lfs.lib.Constants;
import org.eclipse.jgit.lfs.server.LargeFileRepository;
import org.eclipse.jgit.lfs.server.ReplicationInfo;
import org.eclipse.jgit.lfs.server.Response;
import org.eclipse.jgit.lfs.server.Response.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository storing large objects in the file system
 *
 * @since 4.3
 */
public class FileLfsRepository implements LargeFileRepository {

    private final String url;
    private final Path dir;
    // replicated additions
    private ReplicationInfo replicationInfo;
    private static Logger logger = LoggerFactory.getLogger(FileLfsRepository.class);

    /**
     * @param url external URL of this repository
     * @param dir storage directory
     * @throws IOException
     */
    public FileLfsRepository(String url, Path dir) throws IOException {
        this.url = url;
        this.dir = dir;
        Files.createDirectories(dir);
    }

    /**
     * @param url external URL of this repository
     * @param dir storage directory
     * @throws IOException
     */
    public FileLfsRepository(
            final String url,
            final Path dir,
            final ReplicationInfo replicaInfo
    ) throws IOException {
        this.url = url;
        this.dir = dir;
        Files.createDirectories(dir);
        this.replicationInfo = replicaInfo;
    }

    @Override
    public Response.Action getDownloadAction(AnyLongObjectId id) {
        return getAction(id);
    }

    @Override
    public Action getUploadAction(AnyLongObjectId id, long size) {
        return getAction(id);
    }

    @Override
    public @Nullable
    Action getVerifyAction(AnyLongObjectId id) {
        return null;
    }

    /**
     * Get the size of the item: return >-1 to indicate its present and its
     * size. return -1 to indicate its not present.
     *
     * Note: If item is a replica: Check if it is already replicated for this
     * replication group (not just on disk) continue as normal. If the item is
     * not a replica, or has already been replicated, check to see if we have
     * the item and return size on disk.
     *
     * @param id
     * @return
     * @throws IOException
     */
    @Override
    public long getSize(AnyLongObjectId id) throws IOException {

        // If we are dealing with a replica repo, and this item is already
        // replicated then return the size, otherwise return -1 to upload the item
        // and have it replicted.
        if (isReplica() && !isReplicated(id)) {
            // TODO: Phase2 in future we may be abke to shortcut the client upload by copying
            // the item if it already exists on disk for another rep group either to CD
            // or by direct proposal into the cache, if all nodes already have the content.
            return -1;
        }

        Path p = getPath(id);
        if (Files.exists(p)) {
            return Files.size(p);
        } else {
            return -1;
        }
    }

    @Override
    public void setReplicationInfo(ReplicationInfo replicationInfo) {
        this.replicationInfo = replicationInfo;
    }

    @Override
    public boolean isReplica() {
        return replicationInfo != null && replicationInfo.isReplica;
    }

    @Override
    public boolean isReplicated(AnyLongObjectId id) {
        if (!isReplica()) {
            return false;
        }

        // check if we already have the repGroupId, if not have we enough info
        // to get it from the cache (if present in cache).
        checkHaveCachedReplicationGroupIdentificationInfo();

        // now we can make decisions if we have the replication group id using the local
        // cache information only at this point! but this is only a first pass check!
        if (checkIsItemInLocalCache(id)) {
            return true;
        }

        // If we get to here we either haven't got it in the cache or haven't enough
        // information to check.  
        // Either way request a definitive answer about whether its replicated or not, 
        // with our Remote LFS Cache - REST API.
        LfsObjectInfoDTO objectInfo;
        try {
            String keyValue;
            LfsItemType keyType;

            // For safety, to make sure replication group id we haven't isn't stale always
            // request here using repoId or repoName.
            if (replicationInfo == null || Strings.isNullOrEmpty(replicationInfo.repositoryId)) {
                // we dont have the repoId, use the repo name.
                keyValue = replicationInfo.repositoryName;
                keyType = LfsItemType.REPOSITORYNAME;
            } else {
                keyValue = replicationInfo.repositoryId;
                keyType = LfsItemType.REPOSITORY;
            }

            objectInfo = GitLfsCacheAccessor.loadRemoteObjectInfo(keyValue, id.getName(), keyType);

            if (objectInfo == null)
            {
                return false;
            }
            
            // update any information that has been returned by the remote server, like
            // our repoId or repGroupId etc. 
            updateRepositoryReplicationInformation(objectInfo);

        } catch (Exception ex) {
            logger.error("Error has been thrown from remote LFS Rest api. Details: {}", ex);
            // we can't decide if its cached or not as its thrown, maybe timeout or interrupt?
            // If we return false, worst that will happen is that the client will reupload the content
            // and we will throw it away when it comes to store it as we already have it.
            return false;
        }

        // if we get this object back we have already updated our cache locally with any relevant informaiton.
        // Now we just need to indicate to the caller whether we have the item replicated and in the cache.
        final boolean isReplicated = 
                ((objectInfo != null) && 
                (objectInfo.objectInfo != null) && 
                !Strings.isNullOrEmpty(objectInfo.objectInfo.oid));
        
        logger.info("LFS Remote server indicated item replicated status: {}", Boolean.toString(isReplicated));

        return isReplicated;

    }

    /**
     * Used to update the replication information on an LfsRepository object.
     * This is usually updating the repositoryId and replicationGroupId.
     * 
     * @param objectInfo 
     */
    private void updateRepositoryReplicationInformation(LfsObjectInfoDTO objectInfo) {
        // if we got a result, we will have update our GitLFS cache, but lets try to reflect
        // this up to date information on our project replication info for next time.
        if (objectInfo.replicationGroupInfo != null) {
            replicationInfo.replicationGroupIdentifier = objectInfo.replicationGroupInfo.id;
        }
        
        // update our repo id if possible
        if ((objectInfo.repositoryInfo != null) && (objectInfo.repositoryInfo.id != null)) {
            replicationInfo.repositoryId = objectInfo.repositoryInfo.id;
        }
    }

    /**
     * This method is NOT a definitive answer as to whether the content item is
     * in fact replicated. It is only a check if we have it in our local cache
     * and can optimize and return isReplicated without calling gitMS.
     *
     * @return
     */
    private boolean checkIsItemInLocalCache(AnyLongObjectId id) {
        if (Strings.isNullOrEmpty(getReplicaGroupIdentifier())) {
            // if we have'nt got the rep group identifier, then just return false.
            // we can't do any more here.
            return false;
        }

        // so with the replication group id, lookup our cache, and see if this item is cached.
        GitLfsCache lfsCache = null;
        try {
            lfsCache = GitLfsCacheAccessor.getOrCreateGitLFSCache(getReplicaGroupIdentifier());

        } catch (InterruptedException ex) {
            logger.error("LFS isReplicated check was interrupted, possible timeout or shutdown?. {}", ex);

            // return false to indicate we havne't got it cached as we can't make a decision about it otherwise.
            // this is safe as it will only force client to re-upload the content at which point we will see its 
            // already in the backend store.
            return false;
        }

        // now we have the actual cache for this repository, check if we have this object in our
        // local cache.  This is here just to prevent a rest API call to check with GitMS each time.
        if (lfsCache.contains(id.getName())) {
            // the lfs cache contains the object 
            return true;
        }

        // we haven't got it locally, best to go and check the remote gitms server
        // for a definitive answer.
        return false;
    }

    /**
     * checkHaveCachedReplicationGroupIdentificationInfo
     *
     * Checks if we already have the replication group identifier. If we dont,
     * do we have the repository identifier.  Either of these allow us to lookup if the 
     * item has been cached before now.
     * Either using repoId->repGroup mapping, then repGroup->Oid, or just repGroup->Oid directly.
     * This first pass check is only an optimization check.  
     * Any hits are always valid, regardless of whether its stale or not, as LFS content can not be deleted. 
     */
    private void checkHaveCachedReplicationGroupIdentificationInfo() {
        // check if we have replication group identifier for this repo if we have an id
        // to look it up by.
        if (Strings.isNullOrEmpty(getReplicaGroupIdentifier())
                && (!Strings.isNullOrEmpty(replicationInfo.repositoryId))) {
            // we dont have the replica group identifier locally.
            // ok see if we can request the information about this object using our repository
            // identifier instead.
            // TODO does repId persist or is this better to use projectName?
            final String repGroupId = GitLfsCacheAccessor.getReplicationGroupFromRepository(replicationInfo.repositoryId);

            logger.trace("checkHaveCachedReplicationGroupIdentificationInfo found repGroupId from repositoryId in cache.");

            replicationInfo.replicationGroupIdentifier = repGroupId;
        }
    }

    @Override
    public String getReplicaGroupIdentifier() {
        if (!isReplica()) {
            return null;
        }
        return replicationInfo.replicationGroupIdentifier;
    }

    @Override
    public String getProjectName() {
        if (!isReplica()) {
            return null;
        }
        return replicationInfo.repositoryName;
    }
    
    @Override
    public String getProjectIdentity() {
        if (!isReplica()) {
            return null;
        }
        return replicationInfo.repositoryId;
    }

    /**
     * Get the storage directory
     *
     * @return the path of the storage directory
     */
    public Path getDir() {
        return dir;
    }

    /**
     * Get the path where the given object is stored
     *
     * @param id id of a large object
     * @return path the object's storage path
     */
    protected Path getPath(AnyLongObjectId id) {
        StringBuilder s = new StringBuilder(
                Constants.LONG_OBJECT_ID_STRING_LENGTH + 6);
        s.append(toHexCharArray(id.getFirstByte())).append('/');
        s.append(toHexCharArray(id.getSecondByte())).append('/');
        s.append(id.name());
        return dir.resolve(s.toString());
    }

    private Response.Action getAction(AnyLongObjectId id) {
        Response.Action a = new Response.Action();
        a.href = url + id.getName();
        a.header = Collections.singletonMap(HDR_AUTHORIZATION, "not:required"); //$NON-NLS-1$
        return a;
    }

    ReadableByteChannel getReadChannel(AnyLongObjectId id)
            throws IOException {
        return FileChannel.open(getPath(id), StandardOpenOption.READ);
    }

    AtomicObjectOutputStream getOutputStream(AnyLongObjectId id)
            throws IOException {
        Path path = getPath(id);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // TREV TODO ASK WHY THE OVERRIDE WITHOUT ID why not just supply it
        // we can control the verify has based on config?
        return new AtomicObjectOutputStream(path, id);
        //return new AtomicObjectOutputStream(path);

    }

    private static char[] toHexCharArray(int b) {
        final char[] dst = new char[2];
        formatHexChar(dst, 0, b);
        return dst;
    }

    private static final char[] hexchar = {'0', '1', '2', '3', '4', '5', '6',
        '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private static void formatHexChar(final char[] dst, final int p, int b) {
        int o = p + 1;
        while (o >= p && b != 0) {
            dst[o--] = hexchar[b & 0xf];
            b >>>= 4;
        }
        while (o >= p) {
            dst[o--] = '0';
        }
    }

}
