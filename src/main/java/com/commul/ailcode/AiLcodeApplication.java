package com.commul.ailcode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.commul.ailcode.mapper")
public class AiLcodeApplication {
	public static void main(String[] args) {
		SpringApplication.run(AiLcodeApplication.class, args);
	}

}
