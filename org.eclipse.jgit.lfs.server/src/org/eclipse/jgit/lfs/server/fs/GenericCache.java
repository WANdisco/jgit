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

package org.eclipse.jgit.lfs.server.fs;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A Generic caching / mapping structure of name to value, templated to allow for different object key and value support.
 * The implementation is a thread safe, hash map allowing for key to value mappings of any object type.
 * e.g. Key=String, Value=String
 * or Key=Long, Value=Object(class)
 * @param <K>
 * @param <V>
 */
public class GenericCache<K, V>
{
  private ConcurrentHashMap<K, V> map;

  /**
   * Public default constructor, which initializes an empty map.
   */
  public GenericCache()
  {
    map = new ConcurrentHashMap<K, V>();
  }


  /**
   * Returns true or false indicating if the key specified exists in the cache.
   * @param key Key to be checked.
   * @return true, if the specified key exists.
   */
  public boolean containsKey(K key) { return map.containsKey(key); }

  /**
   * Returns true or false depending upon whether this value exists for any given key, note this is not as efficient
   * as name -> value lookup.
   *
   * @param value The value to be checked for.
   * @return True if the value exists in the cache otherwise FALSE.
   */
  public boolean containsValue(V value) { return map.containsValue(value); }

  /**
   * Place value into map given this key.
   * Note: This will replace existing key if present.
   * @param key
   * @param value
   */
  public void put(K key, V value)
  {
    map.put(key, value);
  }

  /**
   * Place the specified value into the map assocatiated with given key, but only if an existing mapping doesn't exist.
   *
   * @param key
   * @param value
   */
  public void putIfAbsent(K key, V value)
  {
    map.putIfAbsent(key, value);
  }

  /**
   * Return value associated with specified key, if it exists or null.
   * @param key
   * @return Value associated with the key.
   */
  public V get(K key)
  {
    return map.get(key);
  }

  /**
   * Remove value associated with this key, and the key entry if it exists.
   * @param key
   */
  public void remove(K key){
    map.remove(key);
  }

  /**
   * Clears the entire mapping structure of all entries.
   */
  public void clearCache(){
    map.clear();
  }

  /**
   *
   * @return The size or count of mapping keys in the cache.
   */
  public int size(){
    return map.size();
  }
}
