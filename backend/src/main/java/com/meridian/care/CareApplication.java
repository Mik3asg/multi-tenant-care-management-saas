package com.meridian.care;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement(order = 0)
public class CareApplication {
    public static void main(String[] args) {
        SpringApplication.run(CareApplication.class, args);
    }
}
// CI retest 2026-07-25T16:08:49Z
