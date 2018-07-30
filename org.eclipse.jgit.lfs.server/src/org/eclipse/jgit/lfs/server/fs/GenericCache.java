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
