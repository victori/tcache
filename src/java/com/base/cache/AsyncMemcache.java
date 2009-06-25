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

public class AsyncMemcache extends AsyncCache {
	private final static int DEFAULT_TTL = 120;

	public AsyncMemcache(final List<String> list, final String poolName) {
		this(new Memcache2(list,poolName));
	}

	public AsyncMemcache(final List<String> list, final String poolName,final int ttl) {
		this(new Memcache2(list,poolName),true,ttl);
	}

	public AsyncMemcache(final List<String> list, final String poolName,final boolean async,final int ttl) {
		this(new Memcache2(list,poolName),async,ttl);
	}

	public AsyncMemcache(final IDistributedCache primaryCache) {
		this(primaryCache, new Ehcache(primaryCache.getPoolName()), DEFAULT_TTL);
	}

	public AsyncMemcache(final IDistributedCache primaryCache, final int ttl) {
		this(primaryCache,new Ehcache(primaryCache.getPoolName()), ttl);
	}

	public AsyncMemcache(final IDistributedCache primaryCache,final boolean async, final int ttl) {
		this(primaryCache, new Ehcache(primaryCache.getPoolName()),async, ttl);
	}

	public AsyncMemcache(final ICache primaryCache, final ICache secondaryCache, final int ttl) {
		this(primaryCache, secondaryCache,true, ttl);
	}

	public AsyncMemcache(final ICache primaryCache, final ICache secondaryCache,final boolean async,final int ttl) {
		super(primaryCache, secondaryCache,async,ttl);
	}

}
