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
package org.springframework.ai.autoconfigure.vectorstore.neo4j;

import org.neo4j.driver.Driver;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.Neo4jVectorStore;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.neo4j.Neo4jAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.micrometer.observation.ObservationRegistry;

/**
 * @author Jingzhou Ou
 * @author Josh Long
 * @author Christian Tzolov
 */
@AutoConfiguration(after = Neo4jAutoConfiguration.class)
@ConditionalOnClass({ Neo4jVectorStore.class, EmbeddingModel.class, Driver.class })
@EnableConfigurationProperties({ Neo4jVectorStoreProperties.class })
public class Neo4jVectorStoreAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public Neo4jVectorStore vectorStore(Driver driver, EmbeddingModel embeddingModel,
			Neo4jVectorStoreProperties properties, ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<VectorStoreObservationConvention> customObservationConvention) {
		Neo4jVectorStore.Neo4jVectorStoreConfig config = Neo4jVectorStore.Neo4jVectorStoreConfig.builder()
			.withDatabaseName(properties.getDatabaseName())
			.withEmbeddingDimension(properties.getEmbeddingDimension())
			.withDistanceType(properties.getDistanceType())
			.withLabel(properties.getLabel())
			.withEmbeddingProperty(properties.getEmbeddingProperty())
			.withIndexName(properties.getIndexName())
			.withIdProperty(properties.getIdProperty())
			.withConstraintName(properties.getConstraintName())
			.build();

		return new Neo4jVectorStore(driver, embeddingModel, config, properties.isInitializeSchema(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP),
				customObservationConvention.getIfAvailable(() -> null));
	}

}
