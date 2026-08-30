package com.sajo.market_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sajo")
public class MarketServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketServiceApplication.class, args);
	}

}
