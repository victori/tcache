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
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncCache implements IMultiTierCache,IAsyncCache {
	private final static int DEFAULT_TTL = 120;
	private ThreadPoolExecutor exec;
	private int ttl = DEFAULT_TTL;
	private int maxThreads = 100;
	private int minThreads = 5;
	private ICache primaryCache;
	private ICache secondaryCache;
	private boolean async;

	public AsyncCache(final ICache primaryCache,final ICache secondaryCache) {
		this(primaryCache,secondaryCache,true,DEFAULT_TTL);
	}

	public AsyncCache(final ICache primaryCache,final ICache secondaryCache,final int ttl) {
		this(primaryCache,secondaryCache,true,ttl);
	}

	public AsyncCache(final ICache primaryCache,final ICache secondaryCache,final boolean async,final int ttl) {
		this.primaryCache = primaryCache;
		this.secondaryCache = secondaryCache;
		exec = new ThreadPoolExecutor(getMinPoolThreads(), getMaxPoolThreads(), 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		exec.setThreadFactory(new ThreadFactory() {
			public Thread newThread(final Runnable r) {
				Thread t = new Thread(r);
				return t;
			}
		});
		this.async = async;
		this.ttl = ttl;
	}

	public void clear() {
		primaryCache.clear();
		secondaryCache.clear();
	}

	public Object get(final String key) {
		Object ret = secondaryCache.get(key);
		if (ret != null) {
			return ret;
		} else {
			ret = primaryCache.get(key);
			if (ret != null) {
				secondaryCache.put(key, ret, getSecondLevelTTL());
			}
			return ret;
		}
	}

	public List<String> getKeys() {
		return primaryCache.getKeys();
	}

	public boolean keyExists(final String key) {
		if (secondaryCache.keyExists(key)) {
			return true;
		}
		return primaryCache.keyExists(key);
	}

	protected boolean doAsyncOperation() {
		if(primaryCache instanceof ISupportAsyncOperations) {
			return false;
		}
		return async;
	}

	public void put(final String key, final Object value, final int ttl) {
		if(doAsyncOperation()) {
			exec.execute(new Runnable() {
				public void run() {
					primaryCache.put(key, value, ttl);
				}
			});
		} else {
			primaryCache.put(key, value,ttl);
		}

		if(ttl < getSecondLevelTTL()) {
			secondaryCache.put(key, value, ttl);
		} else {
			secondaryCache.put(key, value, getSecondLevelTTL());
		}
	}

	public void put(final String key, final Object value) {
		if(doAsyncOperation()) {
			exec.execute(new Runnable() {
				public void run() {
					primaryCache.put(key, value);
				}
			});
		} else {
			primaryCache.put(key, value);
		}
		secondaryCache.put(key, value, getSecondLevelTTL());
	}

	public void remove(final String key) {
		if(doAsyncOperation()) {
			exec.execute(new Runnable() {
				public void run() {
					primaryCache.remove(key);
				}
			});
		} else {
			primaryCache.remove(key);
		}
		secondaryCache.remove(key);
	}

	public void setSecondLevelTTL(final int ttl) {
		this.ttl = ttl;
	}

	public int getSecondLevelTTL() {
		return ttl;
	}

	public void setMaxThreads(final int maxThreads) {
		this.maxThreads = maxThreads;
		exec.setMaximumPoolSize(maxThreads);
	}

	public int getMaxPoolThreads() {
		return maxThreads;
	}

	public void setMaxPoolThreads(final int min) {
		this.minThreads = min;
		exec.setCorePoolSize(minThreads);
	}

	public void setMinPoolThreads(final int min) {
		this.minThreads = min;
		exec.setCorePoolSize(minThreads);
	}

	public int getMinPoolThreads() {
		return minThreads;
	}

	public void disconnect() {
		if(primaryCache instanceof IDistributedCache) {
			((IDistributedCache) primaryCache).disconnect();
		}
		if(secondaryCache instanceof IDistributedCache) {
			((IDistributedCache) secondaryCache).disconnect();
		}
	}

	public String getPoolName() {
		if(primaryCache instanceof IDistributedCache) {
			return ((IDistributedCache) primaryCache).getPoolName();
		}
		return null;
	}

	public void setPoolName(final String poolName) {
		// unused
	}

	@SuppressWarnings("unchecked")
	public Map stats() {
		if(primaryCache instanceof IDistributedCache) {
			return ((IDistributedCache) primaryCache).stats();
		}
		return null;
	}

	public void setAsync(final boolean async) {
		this.async = async;
	}

	public boolean isAsync() {
		return async;
	}


}
