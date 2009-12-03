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

public class CacheLayer {
	private static transient Logger logger = LoggerFactory.getLogger(CacheLayer.class);

	public synchronized static Object addOrReplace(final ICache cache, final String key, final IFetch fetch, final int ttl) {
		// if cache is null, just return the result.
		if (cache == null) {
			return fetch.getObject();
		}

		try {
			Object o = cache.get(key);
			if (o != null) {
				return o;
			} else {
				Object val = fetch.getObject();
				cache.put(key, val, ttl);
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

	public synchronized static Object get(final ICache cache, final String key) {
		return cache.get(key);
	}

	public synchronized static void add(final ICache cache, final String key, final Object value, final int ttl) {
		cache.put(key, value, ttl);
	}

	public synchronized static void add(final ICache cache, final String key, final Object value) {
		cache.put(key, value, 0);
	}

	public synchronized static void flush(final ICache cache) {
		cache.clear();
	}

	public synchronized static void remove(final ICache cache, final String key) {
		cache.remove(key);
	}

}