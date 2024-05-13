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

public class GenericCache<K, V>
{
  private ConcurrentHashMap<K, V> map;

  public GenericCache()
  {
    map = new ConcurrentHashMap<K, V>();
  }

  public void put(K key, V value)
  {
    map.put(key, value);
  }

  public boolean containsKey(K key) { return map.containsKey(key); }

  public boolean containsValue(V value) { return map.containsValue(value); }

  public void putIfAbsent(K key, V value)
  {
    map.putIfAbsent(key, value);
  }

  public V get(K key)
  {
    return map.get(key);
  }

  public void remove(K key){
    map.remove(key);
  }

  public void clearCache(){
    map.clear();
  }

  public int size(){
    return map.size();
  }
}
