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

import net.spy.memcached.AddrUtil;
import net.spy.memcached.KetamaConnectionFactory;
import net.spy.memcached.MemcachedClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Memcache2 implements ICache, ICacheStat, IDistributedCache, ISupportAsyncOperations {
	private String poolName;
	private Set<String> keys = new HashSet<String>();
	private static transient Logger logger = LoggerFactory.getLogger(Memcache2.class);
	private List<MemcachedClient> memPool = new ArrayList<MemcachedClient>();
	private Random rand = new Random();

	protected int getPoolSize() {
		return 1;
	}

	// this was needed to get around spymemcached's job queue for high load requirements.
	protected MemcachedClient getClient() {
        if(getPoolSize() == 1) {
            return memPool.get(0);
        } else {
		    return memPool.get(rand.nextInt(memPool.size()));
        }
	}

	public Memcache2(final List<String> servers, final String poolName) {
		StringBuffer sb = new StringBuffer();
		for (String s : servers) {
			sb.append(s + " ");
		}
		this.poolName = poolName;
		try {
			for(int i = 0;i < getPoolSize() ;i++) {
				memPool.add(new MemcachedClient(new KetamaConnectionFactory(), AddrUtil.getAddresses(sb.toString())));
			}
			//client.setTranscoder(new SerializingTranscoder());
			//Logger.getLogger(SerializingTranscoder.class.getName()).setLevel(Level.WARNING);
			//client.setTranscoder(new WhalinTranscoder());
		} catch (Exception e) {
			logger.error("failed to start",e);
		}
	}

	public void put(final String key, final Object value, final int ttl) {
		retryDo(new IDo() {
			private static final long serialVersionUID = 1L;

			public Object execute() {
				if (value != null) {
					getClient().set(genKey(key), ttl, value);
					if (!keys.contains(key)) {
						keys.add(key);
					}
				}
				return null;
			}
		});
	}

	protected String genKey(final String key) {
		return getPoolName() + "#" + key.replace(" ", "").replace("&", "").replace("!", "").replace(":", "#");
	}

	public void put(final String key, final Object value) {
		put(key, value, 0);
	}

	public void clear() {
		retryDo(new IDo() {
			private static final long serialVersionUID = 1L;

			public Object execute() {
				getClient().flush();
				keys.clear();
				return null;
			}
		});
	}

	protected static interface IDo extends Serializable {
		public Object execute();
	}

	protected int getMaxRetries() {
		return 3;
	}

	protected Object retryDo(final IDo action) {
		Object ret = null;
		for (int i = 0; i < getMaxRetries(); i++) {
			try {
				ret = action.execute();
				return ret;
			} catch (Exception e) {
				logger.error("failed to execute, retrying..."+i+" time.",e);
				// we only do this due to some jvm bug with thread Future.
			}
		}
		return ret;
	}

	public Object get(final String key) {
		return retryDo(new IDo() {
			private static final long serialVersionUID = 1L;

			public Object execute() {
				try {
					Future<Object> f = getClient().asyncGet(genKey(key));
					Object ret = f.get(2000, TimeUnit.MILLISECONDS);
					//Object ret = getClient().get(genKey(key));
					if (ret != null) {
						if (!keys.contains(key)) {
							keys.add(key);
						}
					}
					return ret;
				} catch (Exception e) {
					logger.error("timed out",e);
					return null;
				}

			}
		});
	}

	public List<String> getKeys() {
		return new ArrayList<String>(keys);
	}

	public void remove(final String key) {
		retryDo(new IDo() {
			private static final long serialVersionUID = 1L;

			public Object execute() {
				getClient().delete(genKey(key));
				keys.remove(key);
				return null;
			}
		});
	}

	@SuppressWarnings("unchecked")
	public Map stats() {
		return getClient().getStats();
	}

	protected String getStatKey(final String key) {
		return (String) retryDo(new IDo() {
			private static final long serialVersionUID = 1L;

			@SuppressWarnings("unchecked")
			public Object execute() {
				for (Map.Entry element : getClient().getStats().entrySet()) {
					Map.Entry entry = element;
					Map<String, String> val = (Map<String, String>) entry.getValue();
					return val.get(key);
				}
				return "";
			}
		});
	}

	public long getCacheEvictions() {
		return Long.valueOf(getStatKey("evictions"));
	}

	public long getCacheMisses() {
		return Long.valueOf(getStatKey("get_misses"));
	}

	public long getCacheHits() {
		return Long.valueOf(getStatKey("get_hits"));
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

	public void disconnect() {
		getClient().shutdown();
	}

	public String getPoolName() {
		return poolName;
	}

	public void setPoolName(final String poolName) {
		this.poolName = poolName;
	}

	public boolean keyExists(final String key) {
		return (get(key) != null);
		//return keys.contains(key);
	}

	public long count() {
		return getCacheElements();
	}
}
