package com.aegispulse.engine;

import org.springframework.ai.autoconfigure.chat.client.ChatClientAutoConfiguration;
import org.springframework.ai.autoconfigure.vectorstore.pgvector.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {ChatClientAutoConfiguration.class, PgVectorStoreAutoConfiguration.class})
public class AegisPulseEngineApplication {

	public static void main(String[] args) {
        SpringApplication.run(AegisPulseEngineApplication.class, args);
	}

}
