package com.commul.ailcode.config;


import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * redis 持久化存储记忆
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoreConfig {


    private String host;

    private int port;

    private String password;

    private String username;

    private long ttl;

    private int database;


    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        // @Bean 标注方法，返回对象交给Spring容器
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .password(password)
                .user(username)
                .ttl(ttl)
                .build();
    }
}
