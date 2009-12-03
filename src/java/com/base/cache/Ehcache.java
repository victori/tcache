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

import java.util.List;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheException;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;
import net.sf.ehcache.Status;
import net.sf.ehcache.extension.CacheExtension;

public class Ehcache implements ICache, ICacheStat {
	private CacheManager cacheManager;
	private String cacheName;

	protected static final String DEFAULT_NAME = "defaultCache";

	public Ehcache() {
		this(DEFAULT_NAME);
	}

	public Ehcache(final String cacheName) {
		// 30 minutes
		this(cacheName,1800,10000,false,60*30);
	}

	public Ehcache(final String cacheName,final int ttl,final int elSize,final boolean disk,final int expireThreadSeconds) {
		this.cacheName = cacheName;
		cacheManager = CacheManager.getInstance();
		if (cacheManager.getCache(cacheName) == null) {
			Cache cache = new Cache(cacheName, elSize, disk, false, ttl, 0);
			if(expireThreadSeconds > 0) {
				cache.registerCacheExtension(new EhCacheExtension(cacheName,expireThreadSeconds));
			}
			cacheManager.addCache(cache);
		}
	}

	public static class EhCacheExtension implements CacheExtension {
		private String cacheName;
		private EvictionThread evictThread;
		private Thread thread;
		private int seconds;

		public EhCacheExtension(final String cacheName,final int seconds) {
			this.seconds = seconds;
			this.cacheName = cacheName;
		}

		public CacheExtension clone(final net.sf.ehcache.Ehcache arg0) throws CloneNotSupportedException {
			return new EhCacheExtension(arg0.getName(),seconds);
		}

		public void dispose() throws CacheException {
			evictThread.kill();
			thread.interrupt();
			evictThread.kill();
		}

		public Status getStatus() {
			return Status.STATUS_ALIVE;
		}

		public void init() {
			evictThread = new EvictionThread(cacheName,seconds);
			thread = new Thread(evictThread);
			thread.setName(cacheName+"-expire");
			thread.start();
		}

	}

	public static class EvictionThread implements Runnable {
		private String cacheName;
		private int seconds;
		private boolean run = true;

		public EvictionThread(final String cacheName,final int seconds) {
			this.cacheName = cacheName;
			this.seconds = seconds;
		}

		public void kill() {
			run = false;
		}

		public void run() {
			while(run) {
				Cache cache = CacheManager.getInstance().getCache(cacheName);
				if(cache != null) {
					cache.evictExpiredElements();
				}
				try {
					Thread.sleep(1000*seconds);
				} catch(Exception e) {
				}
			}
		}
	}

	public Object get(final String key) {
		Element el = getCache().get(key);
		if (el != null) {
			return el.getObjectValue();
			//return el.getValue();
		} else {
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	public List<String> getKeys() {
		return getCache().getKeys();
	}

	public Cache getCache() {
		return cacheManager.getCache(cacheName);
	}

	public void clear() {
		getCache().removeAll();
	}

	public void remove(final String key) {
		getCache().remove(key);
	}

	public void put(final String key, final Object value) {
		Element el = new Element(key, value);
		//el.setEternal(true);
		//el.setTimeToLive(ttl);
		getCache().put(el);
	}

	public void put(final String key, final Object value, final int ttl) {
		Element el = new Element(key, value);
		if (ttl != 0) {
			el.setTimeToLive(ttl);
		}
		getCache().put(el);
	}

	public long getCacheBytes() {
		return getCache().calculateInMemorySize();
	}

	public long getCacheEvictions() {
		return getCache().getStatistics().getEvictionCount();
	}

	public long getCacheElements() {
		return getCache().getSize();
	}

	public long getCacheHits() {
		return getCache().getStatistics().getCacheHits();
	}

	public long getCacheMisses() {
		return getCache().getStatistics().getCacheMisses();
	}

	public boolean keyExists(final String key) {
		return getCache().isKeyInCache(key);
	}

}
