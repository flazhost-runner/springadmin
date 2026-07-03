package com.nodeadmin.bdd;

import com.nodeadmin.config.TestJwtConfig;
import com.nodeadmin.config.TestProfileResolver;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Bridges Cucumber and the Spring Boot test context.
 *
 * <p>Must be exactly one class annotated with both {@code @CucumberContextConfiguration}
 * and {@code @SpringBootTest} on the Cucumber glue path. All step-definition classes
 * in the same package share this context automatically via cucumber-spring.
 */
@CucumberContextConfiguration
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfileResolver.class)
@Import(TestJwtConfig.class)
public class CucumberSpringConfig {

    /**
     * Stub StringRedisTemplate — SecurityConfig requires it but tests don't use Redis.
     */
    @MockitoBean
    StringRedisTemplate stringRedisTemplate;
}
