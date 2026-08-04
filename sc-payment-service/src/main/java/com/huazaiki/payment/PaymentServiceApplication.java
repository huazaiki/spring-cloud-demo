package com.huazaiki.payment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan({"com.huazaiki.payment.mapper", "com.huazaiki.payment.outbox"})
@SpringBootApplication(scanBasePackages = {"com.huazaiki.payment", "com.huazaiki.common.audit", "com.huazaiki.common.security"})
@EnableFeignClients
@EnableScheduling
@EnableKafka
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}