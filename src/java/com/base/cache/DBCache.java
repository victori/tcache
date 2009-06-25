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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DBCache implements ICache, IDistributedCache {
	private String jdbcUrl;
	private String userName;
	private String password;
	private String poolName;

	public DBCache(final String jdbcUrl, final String userName, final String password, final String driverName,
			final String poolName) {

		try {
			Class.forName(driverName).newInstance();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to load the JDBC driver");
		}
		this.jdbcUrl = jdbcUrl;
		this.userName = userName;
		this.password = password;
		this.poolName = poolName;
		createTables();
	}

	public void put(final String key, final Object value, final int ttl) {
		runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				PreparedStatement ps = conn.prepareStatement("select cache_key from " + getTableStore()
						+ " where cache_key=?");
				ps.setString(1, key);
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					PreparedStatement updt = conn.prepareStatement("update " + getTableStore()
							+ " set cache_data=?,are_bytes=?,updated=now() where cache_key=?;");
					if (value instanceof byte[]) {
						updt.setBytes(1, (byte[]) value);
						updt.setBoolean(2, true);
					} else {
						updt.setBytes(1, serialize(value));
						updt.setBoolean(2, false);
					}
					updt.setString(3, key);
					updt.execute();
				} else {
					PreparedStatement insrt = conn.prepareStatement("insert into " + getTableStore()
							+ " (cache_key,cache_data,created,updated,are_bytes) values (?,?,now(),now(),?);");
					insrt.setString(1, key);
					if (value instanceof byte[]) {
						insrt.setBytes(2, (byte[]) value);
						insrt.setBoolean(3, true);
					} else {
						insrt.setBytes(2, serialize(value));
						insrt.setBoolean(3, false);
					}
					insrt.execute();
				}
				return null;
			}
		});
	}

	public void put(final String key, final Object value) {
		put(key, value, 0);
	}

	public String getTableStore() {
		return poolName + "_store";
	}

	public void clear() {
		runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				PreparedStatement ps = conn.prepareStatement("truncate " + getTableStore() + ";");
				ps.execute();
				return null;
			}
		});
	}

	// CREATE TABLE fab40r2_store (cache_key varchar(255) not null unique primary key,cache_data bytea,created timestamp without time zone,updated timestamp without time zone);
	protected void createTables() {
		runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				String ctable = null;

				if (jdbcUrl.toLowerCase().matches(".*mysql.*")) {
					ctable = "CREATE TABLE "
						+ getTableStore()
						+ " (cache_key varchar(255) not null unique primary key,cache_data mediumblob,created timestamp,"
						+ "updated timestamp,are_bytes boolean not null);";
				} else {
					ctable = "CREATE TABLE "
						+ getTableStore()
						+ " (cache_key varchar(255) not null unique primary key,cache_data bytea,created timestamp without time zone,"
						+ "updated timestamp without time zone,are_bytes boolean not null);";
				}

				PreparedStatement ps = conn.prepareStatement(ctable);
				try {
					ps.execute();
				} catch (Exception e) {
					//e.printStackTrace();
				}
				return null;
			}
		});
	}

	public Object get(final String key) {
		return runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				PreparedStatement ps = conn.prepareStatement("select cache_data,are_bytes from " + getTableStore()
						+ " where cache_key=?");
				ps.setString(1, key);
				ResultSet rs = ps.executeQuery();
				if (rs.next()) {
					if (rs.getBoolean("are_bytes")) {
						return rs.getBytes("cache_data");
					} else {
						return deserialize(rs.getBytes("cache_data"));
					}
				} else {
					return null;
				}
			}
		});
	}

	@SuppressWarnings("unchecked")
	public List<String> getKeys() {
		return (List<String>) runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				PreparedStatement ps = conn.prepareStatement("select cache_key from " + getTableStore());
				ResultSet rs = ps.executeQuery();
				List<String> ids = new ArrayList<String>();
				while (rs.next()) {
					ids.add(rs.getString("cache_key"));
				}
				return ids;
			}
		});
	}

	public boolean keyExists(final String key) {
		return (Boolean) runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				PreparedStatement ps = conn.prepareStatement("select cache_key from " + getTableStore()
						+ " where cache_key=?");
				ps.setString(1, key);
				ResultSet rs = ps.executeQuery();
				return rs.next();
			}
		});
	}

	public void remove(final String key) {
		runSQL(new ISQLExecute() {
			public Object execute(final Connection conn) throws SQLException {
				PreparedStatement ps = conn.prepareStatement("delete from " + getTableStore() + " where cache_key=?");
				ps.setString(1, key);
				ps.execute();
				return null;
			}
		});
	}

	public void disconnect() {
	}

	public String getPoolName() {
		return poolName;
	}

	public void setPoolName(final String poolName) {
		this.poolName = poolName;
	}

	public Map<String,String> stats() {
		return new HashMap<String,String>();
	}

	protected Connection getConnection() throws SQLException {
		Connection c = DriverManager.getConnection(jdbcUrl, userName, password);
		c.setAutoCommit(true);
		return c;
	}

	protected Object runSQL(final ISQLExecute exec) {
		Connection c = null;
		try {
			c = getConnection();
			return exec.execute(getConnection());
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Failed to execute SQL");
		} finally {
			if (c != null) {
				try {
					c.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	protected interface ISQLExecute {
		public Object execute(Connection conn) throws SQLException;
	}

	public Object deserialize(final byte[] bytes) {
		if (bytes == null) {
			return null;
		}
		ClassLoadingObjectInputStream ois = null;
		try {
			ByteArrayInputStream bs = new ByteArrayInputStream(bytes);
			ois = new ClassLoadingObjectInputStream(bs);
			Object o = ois.readObject();
			if (o != null) {
				return o;
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (ois != null) {
					ois.close();
				}
			} catch (Exception e) {
			}
		}
		throw new RuntimeException("failed.");
	}

	public byte[] serialize(final Object o) {
		ObjectOutputStream oos = null;
		try {
			ByteArrayOutputStream bs = new ByteArrayOutputStream();
			oos = new ObjectOutputStream(bs);
			oos.writeObject(o);
			oos.flush();
			return bs.toByteArray();
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("failed to serialize");
		} finally {
			try {
				if (oos != null) {
					oos.close();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * ClassLoadingObjectInputStream
	 * 
	 * 
	 */
	protected class ClassLoadingObjectInputStream extends ObjectInputStream {
		public ClassLoadingObjectInputStream(final java.io.InputStream in) throws IOException {
			super(in);
		}

		public ClassLoadingObjectInputStream() throws IOException {
			super();
		}

		@Override
		public Class<?> resolveClass(final java.io.ObjectStreamClass cl) throws IOException, ClassNotFoundException {
			try {
				return Class.forName(cl.getName(), false, Thread.currentThread().getContextClassLoader());
			} catch (ClassNotFoundException e) {
				return super.resolveClass(cl);
			}
		}
	}

}
