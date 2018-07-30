/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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

    public String repositoryName;
    public String repositoryId;
    public String replicationGroupIdentifier;
    public boolean isReplica;

    
    public ReplicationInfo(){
        // default constructor can be used to indicate not a replica.
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
