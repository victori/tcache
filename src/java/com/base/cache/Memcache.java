/*
 * Copyright 2009 Victor Igumnov <victori@fabulously40.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.base.cache;

import com.meetup.memcached.Logger;
import com.meetup.memcached.MemcachedClient;
import com.meetup.memcached.SockIOPool;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public class Memcache implements ICache, ICacheStat, IDistributedCache {
	private transient MemcachedClient client;
	private String poolName;
	private Map<String,Object> keys = new ConcurrentHashMap<String,Object>();
    private boolean prefixedKeys;
    private String prefix;
    private Random rand;

    public Memcache(final List<String> servers, final String poolName) {
        this(servers,poolName,false);
    }

    public Memcache(final List<String> servers, final String poolName, boolean prefixedKeys) {
        this.keys = new ConcurrentHashMap<String, Object>();
        this.poolName = poolName;
        this.prefixedKeys = prefixedKeys;
        this.prefix = poolName + "_ns";
        this.rand = new Random(System.currentTimeMillis());
        
        SockIOPool pool = SockIOPool.getInstance(poolName);
        String[] serv = new String[servers.size()];
        for (int i = 0; i < serv.length; i++) {
            serv[i] = servers.get(i);
        }
        pool.setServers(serv);
        // pool.setNagle(false);
        pool.setInitConn(5);
        pool.setMinConn(5);
        // default to 6 hours
        pool.setMaxIdle(1000 * 60 * 60 * 6);
        pool.setHashingAlg(SockIOPool.NEW_COMPAT_HASH);
        pool.setFailover(true);

        pool.initialize();
        client = new MemcachedClient(poolName);
        client.setPrimitiveAsString(false);
        client.setCompressEnable(true);
        client.setSanitizeKeys(true);
        Logger.getLogger(MemcachedClient.class.getName()).setLevel(Logger.LEVEL_WARN);
    }

	public void put(final String key, final Object value) {
		client.set(genKey(key), value);
		if (!keys.containsKey(key)) {
			keys.put(key,"item");
		}
	}

	protected String genKey(final String key) {
        String prefix = (prefixedKeys) ? getPoolName() + "#" + getPrefixKey() : getPoolName();
        return prefix + "#" + key.replace(" ", "").replace("&", "").replace("!", "").replace(":", "#");
	}

    protected String resetPrefixKey() {
        String nsKey = String.valueOf(rand.nextInt());
        client.set(prefix, nsKey);
        return nsKey;
    }

    protected String getPrefixKey() {
        Object nsKey = client.get(prefix);
        return nsKey == null ? resetPrefixKey() : String.valueOf(nsKey);
    }

    public void setCompression(final boolean bool) {
		client.setCompressEnable(bool);
	}

	public void setCompressionThreshold(final int amount) {
		client.setCompressThreshold(amount * 1024);
	}

	public MemcachedClient getClient() {
		return client;
	}


	public Object get(final String key) {
		Object ret = client.get(genKey(key));
		if (ret != null) {
			if (!keys.containsKey(key)) {
				keys.put(key, "item");
			}
		}
		return ret;
	}

	@SuppressWarnings("unchecked")
	public Map<String,String> stats() {
		return getClient().stats();
	}

	@SuppressWarnings("unchecked")
	protected String getStatKey(final String key) {
		for (Iterator<Entry> i = getClient().stats().entrySet().iterator(); i.hasNext();) {
			Map.Entry entry = i.next();
			Map<String, String> val = (Map<String, String>) entry.getValue();
			return val.get(key);
		}
		return "";
	}

	public long getCacheMisses() {
		return Long.valueOf(getStatKey("get_misses"));
	}

	public long getCacheHits() {
		return Long.valueOf(getStatKey("get_hits"));
	}

	public long getCacheEvictions() {
		return Long.valueOf(getStatKey("evictions"));
	}

	public long getCacheElements() {
		// memcachedb does not have this
		String str = getStatKey("curr_items");
		if (str != null && !str.equals("")) {
			return Long.valueOf(str);
		} else {
			return 0;
		}
	}

	public long getCacheBytes() {
		// memcachedb does not have this
		String str = getStatKey("bytes");
		if (str != null && !str.equals("")) {
			return Long.valueOf(str);
		} else {
			return 0;
		}
	}

	public List<String> getKeys() {
		return new ArrayList<String>(keys.keySet());
	}

	public void clear() {
        if (prefixedKeys) {
            resetPrefixKey();
        } else {
            client.flushAll();
        }
		keys.clear();
	}

	public void remove(final String key) {
		client.delete(genKey(key));
		if (keys.containsKey(key)) {
			keys.remove(key);
		}
	}

	public void put(final String key, final Object value, final int ttl) {
		client.set(genKey(key), value);
		if (!keys.containsKey(key)) {
			keys.put(key, "item");
		}
	}

	public String getPoolName() {
		return poolName;
	}

	public void setPoolName(final String poolName) {
		this.poolName = poolName;
	}

	public void disconnect() {
		SockIOPool pool = SockIOPool.getInstance(getPoolName());
		if (pool != null && pool.isInitialized()) {
			SockIOPool.getInstance(getPoolName()).shutDown();
		}
	}

	public boolean keyExists(final String key) {
		return client.keyExists(genKey(key));
	}

	public long count() {
		return getCacheElements();
	}
}
