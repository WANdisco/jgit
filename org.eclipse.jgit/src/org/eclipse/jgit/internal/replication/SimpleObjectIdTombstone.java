/********************************************************************************
 * Copyright (c) 2014-2024 WANdisco
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 ********************************************************************************/

package org.eclipse.jgit.internal.replication;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.wandisco.gerrit.gitms.shared.util.LRUSimpleCache;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.lib.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

/**
 * Simple LRU cache, of the ObjectIDs that have been deleted in this session / process run.
 * this is not persisted or reloaded on startup, and as such is just a simple protection against queued
 * deletes in the current session for now.
 */
public class SimpleObjectIdTombstone {

    private final Logger logger = LoggerFactory.getLogger(SimpleObjectIdTombstone.class);

    /**
     * Public env / property naming to load a tombstone comma seperate list of Objectid strings on startup.
     * e.g. -DDELETED_OBJECTID_TOMBSTONES=8601b85108efb2ed6b376e92bc73ebf64c654257,d31ec68e3d871288304407930838b5e47487a981"
     */
    public static final String DELETED_OBJECTID_TOMBSTONES = "DELETED_OBJECTID_TOMBSTONES";
    private final LRUSimpleCache<ObjectId> cache;

    /**
     * Create simple tombstone with a fixed size capacity
     *
     * @param capacity Fixed capacity of cache.
     */
    public SimpleObjectIdTombstone(final int capacity) {
        // default size is 20000 items for us.
        cache = new LRUSimpleCache(capacity);

        loadTombstonesOnStartup(null);
    }

    /**
     * Visible for testing - this allows the tombstone list of previous values to be specified uniquely
     * for overlapping parallel tests, without using the env or system property information.
     * @param capacity Fixed capacity of cache.
     * @param overrideTombstoneStringListForTesting Comma separated List of overridden object-ids for testing.
     */
    @VisibleForTesting
    public SimpleObjectIdTombstone(final int capacity, final String overrideTombstoneStringListForTesting) {
        // default size is 20000 items for us.
        cache = new LRUSimpleCache(capacity);

        loadTombstonesOnStartup(overrideTombstoneStringListForTesting);
    }

    private void loadTombstonesOnStartup(final String overrideTombstoneStringListForTesting) {

        // get tombstone items from the system env or properties by default, although to allow
        // parallel testing code, we can pass in the tombstone list to keep env clean.
        final String tombstonesToBeLoaded =
                !Strings.isNullOrEmpty(overrideTombstoneStringListForTesting) ?
                        overrideTombstoneStringListForTesting :
                        System.getProperty(DELETED_OBJECTID_TOMBSTONES, System.getenv(DELETED_OBJECTID_TOMBSTONES));

        if (Strings.isNullOrEmpty(tombstonesToBeLoaded)) {
            return;
        }

        // comma seperated list of string objectIds.
        String[] tombstoneItems = tombstonesToBeLoaded.split(",");
        for (String item : tombstoneItems) {
            // do a little parsing, so pass ", " spaces or empty elements.
            item = item.trim();
            if (Strings.isNullOrEmpty(item)) {
                continue;
            }
            // add this item.
            try {
                cache.add(ObjectId.fromString(item));
            } catch (InvalidObjectIdException e) {
                logger.warn("Unable to load tombstone item due to error: %s", e.getMessage());
            }
        }

    }

    /**
     * Add new objectId to the cache, this will go to the HEAD.
     *
     * @param key object-id to cache.
     */
    public void add(ObjectId key) {
        cache.add(key);
    }

    /**
     * Add all new ObjectIds in this list to the cache.
     * The last item in the list will be the HEAD.
     *
     * @param keys Collection of object-ids to cache.
     */
    public void addAll(final Collection<ObjectId> keys) {
        cache.addAll(keys);
    }

    /**
     * Checks if a cache contains a key, without moving its position.
     *
     * @param key object-id to check.
     * @return true if the cache contains the requested objectId.
     */
    public boolean containsPeek(ObjectId key) {
        return cache.containsPeek(key);
    }

    /**
     * DO NOT USE THIS method in production code, as it turns cache to array to get
     * list element.  Only for testing head position!
     *
     * @return Returns objectId at head of the Cache.
     */
    @VisibleForTesting
    public ObjectId peekHead() {
        return cache.peekHead();
    }

    /**
     * If the specific item is in the cache, and if it is it will be moved
     * to the HEAD of the LRU cache.  If not it will simply return false.
     *
     * @param key object-id to check.
     * @return returns true if the cache contains the requested objectId.
     * false if the cache does not contain the object.
     */
    public boolean containsUpdateLastAccessed(ObjectId key) {
        return cache.containsUpdateLastAccessed(key);
    }
}
