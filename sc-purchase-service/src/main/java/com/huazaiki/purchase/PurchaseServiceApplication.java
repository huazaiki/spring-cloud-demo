package com.huazaiki.purchase;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({"com.huazaiki.purchase.mapper", "com.huazaiki.purchase.outbox"})
@SpringBootApplication(scanBasePackages = {"com.huazaiki.purchase", "com.huazaiki.common.audit", "com.huazaiki.common.security"})
@EnableFeignClients
@EnableScheduling
@EnableKafka
public class PurchaseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PurchaseServiceApplication.class, args);
    }
}