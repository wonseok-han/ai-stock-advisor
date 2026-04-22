package com.aistockadvisor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 테스트 전체에서 공유되는 Testcontainers 설정.
 *
 * <p>static singleton 으로 컨테이너를 보관해 여러 {@code @SpringBootTest} context 간
 * 단일 Postgres/Redis 인스턴스를 재사용한다. 컨테이너는 JVM 종료 시까지 살아 있다가
 * Ryuk 에 의해 정리된다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@SuppressWarnings("resource")
	private static final PostgreSQLContainer<?> POSTGRES =
			new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
					// Supabase 전용 객체(auth.users, role anon/authenticated/service_role)를
					// Flyway 실행 전에 stub 으로 생성해 프로덕션 migration 을 그대로 재사용한다.
					.withInitScript("init-supabase-compat.sql");

	@SuppressWarnings("resource")
	private static final GenericContainer<?> REDIS =
			new GenericContainer<>(DockerImageName.parse("redis:latest")).withExposedPorts(6379);

	static {
		POSTGRES.start();
		REDIS.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return POSTGRES;
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return REDIS;
	}

}
