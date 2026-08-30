package com.sajo.trading_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.sajo")
public class TradingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradingServiceApplication.class, args);
	}

}
