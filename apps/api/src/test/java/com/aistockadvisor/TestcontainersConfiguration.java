package com.aistockadvisor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		// Supabase 전용 객체(auth.users, role anon/authenticated/service_role)를
		// Flyway 실행 전에 stub 으로 생성해 프로덕션 migration 을 그대로 재사용한다.
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
				.withInitScript("init-supabase-compat.sql");
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);
	}

}
