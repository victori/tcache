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


public class AsyncDBCache extends AsyncCache {
	private final static int DEFAULT_TTL = 120;

	public AsyncDBCache(final String jdbcUrl, final String userName, final String password, final String driverName,
			final String poolName,final int ttl) {
		this(jdbcUrl, userName, password, driverName, poolName,new Ehcache(poolName),ttl);
	}

	public AsyncDBCache(final String jdbcUrl, final String userName, final String password, final String driverName,
			final String poolName) {
		this(jdbcUrl, userName, password, driverName, poolName,new Ehcache(poolName),DEFAULT_TTL);
	}

	public AsyncDBCache(final String jdbcUrl, final String userName, final String password, final String driverName,
			final String poolName, final ICache cache) {
		this(jdbcUrl, userName, password, driverName, poolName,cache,DEFAULT_TTL);
	}

	public AsyncDBCache(final String jdbcUrl, final String userName, final String password, final String driverName,
			final String poolName, final ICache cache,final int ttl) {
		super(new DBCache(jdbcUrl,userName,password,driverName,poolName),cache,ttl);
	}
}
