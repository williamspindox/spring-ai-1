/*
 * Copyright 2024 the original author or authors.
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
package org.springframework.ai.ollama;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.tck.TestObservationRegistry;
import io.micrometer.observation.tck.TestObservationRegistryAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.HighCardinalityKeyNames;
import static org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation.LowCardinalityKeyNames;

/**
 * Integration tests for observation instrumentation in {@link OllamaEmbeddingModel}.
 *
 * @author Thomas Vitale
 */
@SpringBootTest(classes = OllamaEmbeddingModelObservationIT.Config.class)
@DisabledIf("isDisabled")
public class OllamaEmbeddingModelObservationIT extends BaseOllamaIT {

	@Autowired
	TestObservationRegistry observationRegistry;

	@Autowired
	OllamaEmbeddingModel embeddingModel;

	@Test
	void observationForEmbeddingOperation() {
		var options = OllamaOptions.builder().withModel(OllamaModel.NOMIC_EMBED_TEXT.getName()).build();

		EmbeddingRequest embeddingRequest = new EmbeddingRequest(List.of("Here comes the sun"), options);

		EmbeddingResponse embeddingResponse = embeddingModel.call(embeddingRequest);
		assertThat(embeddingResponse.getResults()).isNotEmpty();

		EmbeddingResponseMetadata responseMetadata = embeddingResponse.getMetadata();
		assertThat(responseMetadata).isNotNull();

		TestObservationRegistryAssert.assertThat(observationRegistry)
			.doesNotHaveAnyRemainingCurrentObservation()
			.hasObservationWithNameEqualTo(DefaultEmbeddingModelObservationConvention.DEFAULT_NAME)
			.that()
			.hasContextualNameEqualTo("embedding " + OllamaModel.NOMIC_EMBED_TEXT.getName())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_OPERATION_TYPE.asString(),
					AiOperationType.EMBEDDING.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.AI_PROVIDER.asString(), AiProvider.OLLAMA.value())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.REQUEST_MODEL.asString(),
					OllamaModel.NOMIC_EMBED_TEXT.getName())
			.hasLowCardinalityKeyValue(LowCardinalityKeyNames.RESPONSE_MODEL.asString(), responseMetadata.getModel())
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.REQUEST_EMBEDDING_DIMENSIONS.asString(),
					KeyValue.NONE_VALUE)
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_INPUT_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getPromptTokens()))
			.hasHighCardinalityKeyValue(HighCardinalityKeyNames.USAGE_TOTAL_TOKENS.asString(),
					String.valueOf(responseMetadata.getUsage().getTotalTokens()))
			.hasBeenStarted()
			.hasBeenStopped();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public TestObservationRegistry observationRegistry() {
			return TestObservationRegistry.create();
		}

		@Bean
		public OllamaApi openAiApi() {
			return new OllamaApi();
		}

		@Bean
		public OllamaEmbeddingModel openAiEmbeddingModel(OllamaApi openAiApi,
				TestObservationRegistry observationRegistry) {
			return new OllamaEmbeddingModel(openAiApi, OllamaOptions.builder().build(), observationRegistry);
		}

	}

}
