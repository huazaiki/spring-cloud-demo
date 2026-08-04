package com.huazaiki.purchase.config;

import feign.RequestInterceptor;
import org.apache.seata.core.context.RootContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 将 Seata 全局事务 XID 通过 Feign 请求头 (TX_XID) 传播到下游服务。
 */
@Configuration
public class SeataFeignConfig {

    @Bean
    public RequestInterceptor seataXidRequestInterceptor() {
        return template -> {
            String xid = RootContext.getXID();
            if (xid != null) {
                template.header(RootContext.KEY_XID, xid);
            }
        };
    }
}
