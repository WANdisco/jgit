/********************************************************************************
 * Copyright (c) 2014-2018 WANdisco
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 ********************************************************************************/

package org.eclipse.jgit.lfs.server;

/**
 * Small class to hold a minimal amount of information to help with replication.
 * 
 * Repository Name 
 * Repository Identifier 
 * Replication Group Identifier 
 * Is Replica Type
 *
 * Note fields are public, so we can use this class more like a structure, or
 * interface, I DO NOT want any code living in here, hence why they are public
 * to avoid people putting code in get/set methods.
 *
 * @author trevorgetty
 */
public class ReplicationInfo {

    /**
     * Namd of the repo
     */
    public String repositoryName;

    /**
     * Id of the repo.
     */
    public String repositoryId;

    /**
     * Replication group Id of the repo.
     */
    public String replicationGroupIdentifier;

    /**
     * Indicates if this repository is a replicated repo - default is FALSE.
     */
    public boolean isReplica;

    /**
     * Public default constructor builds a repository which is not a replicated repo
     */
    public ReplicationInfo(){
        // ensure by default we indicate NOT a replicated repo
        isReplica = false;
    }
    
    /** 
     * Override constructor
     * 
     * Used to build the ReplicationInfo object with replication information.
     * At a minimum you must have some repository information, as we can work 
     * out the rest from the name, it just takes more calls.
     * 
     * @param repositoryName
     * @param repositoryId
     * @param replicationGroupId
     * @param isReplica 
     */
    public ReplicationInfo(
            final String repositoryName,
            final String repositoryId,
            final String replicationGroupId,
            final boolean isReplica
    ) {
        this.repositoryName = repositoryName;
        this.repositoryId = repositoryId;
        this.replicationGroupIdentifier = replicationGroupId;
        this.isReplica = isReplica;
    }

}
