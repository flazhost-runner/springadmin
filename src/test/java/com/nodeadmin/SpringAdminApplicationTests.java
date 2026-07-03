package com.nodeadmin;

import com.nodeadmin.config.TestProfileResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(resolver = TestProfileResolver.class)
class SpringAdminApplicationTests {

	/**
	 * Redis is not available in the test environment.
	 * Mock the template so the context can wire JwtService without a real Redis connection.
	 */
	@MockBean
	StringRedisTemplate stringRedisTemplate;

	@Test
	void contextLoads() {
	}

}
