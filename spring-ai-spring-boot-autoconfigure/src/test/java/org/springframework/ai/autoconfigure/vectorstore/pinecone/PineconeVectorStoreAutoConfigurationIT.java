/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.autoconfigure.vectorstore.pinecone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.ai.autoconfigure.vectorstore.observation.ObservationTestUtil.assertObservationRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.observation.conventions.VectorStoreProvider;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.PineconeVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationContext;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;

import io.micrometer.observation.tck.TestObservationRegistry;

/**
 * @author Christian Tzolov
 * @author Soby Chacko
 * @author Thomas Vitale
 */
@EnabledIfEnvironmentVariable(named = "PINECONE_API_KEY", matches = ".+")
public class PineconeVectorStoreAutoConfigurationIT {

	List<Document> documents = List.of(
			new Document("1", getText("classpath:/test/data/spring.ai.txt"), Map.of("spring", "great")),
			new Document("2", getText("classpath:/test/data/time.shelter.txt"), Map.of()),
			new Document("3", getText("classpath:/test/data/great.depression.txt"), Map.of("depression", "bad")));

	public static String getText(String uri) {
		var resource = new DefaultResourceLoader().getResource(uri);
		try {
			return resource.getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(PineconeVectorStoreAutoConfiguration.class))
		.withUserConfiguration(Config.class)
		.withPropertyValues("spring.ai.vectorstore.pinecone.apiKey=" + System.getenv("PINECONE_API_KEY"),
				"spring.ai.vectorstore.pinecone.environment=gcp-starter",
				"spring.ai.vectorstore.pinecone.projectId=814621f",
				"spring.ai.vectorstore.pinecone.indexName=spring-ai-test-index",
				"spring.ai.vectorstore.pinecone.contentFieldName=customContentField",
				"spring.ai.vectorstore.pinecone.distanceMetadataFieldName=customDistanceField");

	@BeforeAll
	public static void beforeAll() {
		Awaitility.setDefaultPollInterval(2, TimeUnit.SECONDS);
		Awaitility.setDefaultPollDelay(Duration.ZERO);
		Awaitility.setDefaultTimeout(Duration.ofMinutes(1));
	}

	@Test
	public void addAndSearchTest() {

		contextRunner.run(context -> {

			PineconeVectorStore vectorStore = context.getBean(PineconeVectorStore.class);
			TestObservationRegistry observationRegistry = context.getBean(TestObservationRegistry.class);

			vectorStore.add(documents);

			assertObservationRegistry(observationRegistry, VectorStoreProvider.PINECONE,
					VectorStoreObservationContext.Operation.ADD);

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			}, hasSize(1));
			observationRegistry.clear();

			List<Document> results = vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));

			assertThat(results).hasSize(1);
			Document resultDoc = results.get(0);
			assertThat(resultDoc.getId()).isEqualTo(documents.get(0).getId());
			assertThat(resultDoc.getContent()).contains(
					"Spring AI provides abstractions that serve as the foundation for developing AI applications.");
			assertThat(resultDoc.getMetadata()).hasSize(2);
			assertThat(resultDoc.getMetadata()).containsKeys("spring", "customDistanceField");

			assertObservationRegistry(observationRegistry, VectorStoreProvider.PINECONE,
					VectorStoreObservationContext.Operation.QUERY);
			observationRegistry.clear();

			// Remove all documents from the store
			vectorStore.delete(documents.stream().map(doc -> doc.getId()).toList());

			assertObservationRegistry(observationRegistry, VectorStoreProvider.PINECONE,
					VectorStoreObservationContext.Operation.DELETE);
			observationRegistry.clear();

			Awaitility.await().until(() -> {
				return vectorStore.similaritySearch(SearchRequest.query("Spring").withTopK(1));
			}, hasSize(0));
		});
	}

	@Configuration(proxyBeanMethods = false)
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
