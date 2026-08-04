package com.huazaiki.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({"com.huazaiki.auth.mapper"})
@SpringBootApplication(scanBasePackages = {"com.huazaiki.auth", "com.huazaiki.common.audit", "com.huazaiki.common.security"})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
