package com.base.test;

import java.util.Arrays;

import org.junit.Test;

import com.base.cache.Memcache2;

public class MemcacheTest {

	@Test
	public void test1() {
		Memcache2 cache = new Memcache2(Arrays.asList("127.0.0.1:11211"), "test");
		cache.put("hi", "hello world!");
		System.out.println(cache.get("hi"));
	}
}
