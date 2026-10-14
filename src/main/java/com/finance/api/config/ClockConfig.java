package com.finance.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	// Bean para os testes trocarem por um relógio controlado e testarem expiração sem sleep
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
