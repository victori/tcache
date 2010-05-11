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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CacheLayer {
	private static transient Logger logger = LoggerFactory.getLogger(CacheLayer.class);
	private static String DOGPILE_PREFIX = "dp-";
	private static String DOGPILE_FETCH_PROGRESS_PREFIX = "fdp-";
	private static transient ThreadPoolExecutor exec = new ThreadPoolExecutor(5, 50, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

	public static Object addOrReplace(final ICache cache, final String key, final IFetch fetch, final int ttl) {
		return addOrReplace(cache,key,fetch,ttl,0);
	}

    protected static String getDpKey(String key) {
        return DOGPILE_PREFIX+key;
    }
    protected static String getDpProgressKey(String key) {
        return DOGPILE_FETCH_PROGRESS_PREFIX + key;
    }

	public static Object addOrReplace(final ICache cache, final String key, final IFetch fetch, final int ttl,final int dogPileMultiplier) {
		// if cache is null, just return the result.
		if (cache == null) {
			return fetch.getObject();
		}

		try {
			Object o = cache.get(key);

			if (o != null) {

				if(dogPileMultiplier != 0) {
                    if(logger.isDebugEnabled()) {
					    logger.debug("Stale cache check. for key "+ getDpKey(key));
                    }
					Object ret = cache.get(getDpKey(key));
					if(ret == null && cache.get(getDpProgressKey(key)) == null) {
						// give a 2 minute timeout for fetch purposes.
						cache.put(getDpProgressKey(key), true, 120);
						logger.debug("Cache stale, fetching new data.");
						exec.execute(new CacheFetchWorker(cache, fetch, key, ttl, dogPileMultiplier));
					}
                    if(logger.isDebugEnabled()) {
					    logger.debug(ret == null ? "Returning stale cache." : "Returning fresh cache." );
                    }
				}

				return o;
			} else {
				Object val = fetch.getObject();

				if(dogPileMultiplier != 0) {
					cache.put(key, val, ttl*dogPileMultiplier);
					cache.put(getDpKey(key),true,ttl);
					// If there is any.
					cache.remove(getDpProgressKey(key));
				} else {
					cache.put(key, val, ttl);
				}

				return val;
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (logger.isDebugEnabled()) {
				logger.debug(e.getMessage());
			}
			return fetch.getObject();
		}
	}

	public static Object get(final ICache cache, final String key) {
		return cache.get(key);
	}

	public static void add(final ICache cache, final String key, final Object value, final int ttl) {
		cache.put(key, value, ttl);
	}

	public static void add(final ICache cache, final String key, final Object value) {
		cache.put(key, value, 0);
	}

	public static void flush(final ICache cache) {
		cache.clear();
	}

	public static void remove(final ICache cache, final String key) {
		cache.remove(key);
	}

	public static class CacheFetchWorker implements Runnable {
		private IFetch fetch;
		private String key;
		private ICache cache;
		private int ttl;
		private int dogPileMultiplier;

		public CacheFetchWorker(final ICache cache,final IFetch fetch,final String key,final int ttl,final int dogPileMultiplier) {
			this.fetch = fetch;
			this.key = key;
			this.cache = cache;
			this.ttl = ttl;
			this.dogPileMultiplier = dogPileMultiplier;
		}

		public void run() {
			Object val = null;
			if(fetch instanceof IAsyncFetch) {
				try {
					val = ((IAsyncFetch)fetch).getObjectAsync();
				} catch (Exception e) {
					// sometimes we fail ;-(
				}
			} else {
				val = fetch.getObject();
			}
			if(val == null) {
				logger.debug("Failed to get new data, clearing stale cache.");
				cache.remove(key);
				cache.remove(getDpKey(key));
				cache.remove(getDpProgressKey(key));
			} else {
				cache.put(key, val, ttl*dogPileMultiplier);
				cache.put(getDpKey(key),true,ttl);
				cache.remove(getDpProgressKey(key));
				logger.debug("Cache primed.");
			}
		}
	}

}