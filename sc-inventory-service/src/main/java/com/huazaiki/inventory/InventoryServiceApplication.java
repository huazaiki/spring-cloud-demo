package com.huazaiki.inventory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({"com.huazaiki.inventory.mapper", "com.huazaiki.inventory.outbox"})
@SpringBootApplication(scanBasePackages = {"com.huazaiki.inventory", "com.huazaiki.common.audit", "com.huazaiki.common.security"})
@EnableScheduling
@EnableKafka
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}