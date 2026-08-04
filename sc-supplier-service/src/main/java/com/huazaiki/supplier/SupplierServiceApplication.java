package com.huazaiki.supplier;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan({"com.huazaiki.supplier.mapper"})
@SpringBootApplication(scanBasePackages = {"com.huazaiki.supplier", "com.huazaiki.common.audit", "com.huazaiki.common.security"})
public class SupplierServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SupplierServiceApplication.class, args);
    }
}
