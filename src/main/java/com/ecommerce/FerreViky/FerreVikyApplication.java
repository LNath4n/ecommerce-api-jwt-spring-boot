package com.ecommerce.FerreViky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching

public class FerreVikyApplication {
	//Este archivo es el que corre la aplicacion nada mas
	public static void main(String[] args) {
		SpringApplication.run(FerreVikyApplication.class, args);
	}

}
