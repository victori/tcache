package com.base.test;

import java.io.Serializable;
import java.util.Arrays;

import org.junit.Test;

import com.base.cache.Memcache2;

public class MemcacheTest {

	@Test
	public void test1() {
		final Memcache2 cache = new Memcache2(Arrays.asList("127.0.0.1:11211"), "test");
		cache.put("hi", new Foo("hi","fooobar"));
		for(int i = 0 ; i < 10000 ; i++ ) {
			cache.get("hi");
		}
	}

	public static class Foo implements Serializable {
		private String name;
		private String body;

		public Foo() {
		}

		public Foo(final String name, final String body) {
			super();
			this.name = name;
			this.body = body;
		}



		public String getName() {
			return name;
		}
		public void setName(final String name) {
			this.name = name;
		}
		public String getBody() {
			return body;
		}
		public void setBody(final String body) {
			this.body = body;
		}
	}
}
