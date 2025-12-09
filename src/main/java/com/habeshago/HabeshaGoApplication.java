package com.habeshago;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class HabeshaGoApplication {

    public static void main(String[] args) {
        SpringApplication.run(HabeshaGoApplication.class, args);
    }
}
