package com.jinsangstop;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 애플리케이션 컨텍스트와 Flyway 마이그레이션이 문제없이 올라오는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
class JinsangStopBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
