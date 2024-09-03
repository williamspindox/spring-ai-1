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
package org.springframework.ai.ollama.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.ollama.BaseOllamaIT;
import org.springframework.ai.ollama.api.OllamaApi.ChatRequest;
import org.springframework.ai.ollama.api.OllamaApi.ChatResponse;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsRequest;
import org.springframework.ai.ollama.api.OllamaApi.EmbeddingsResponse;
import org.springframework.ai.ollama.api.OllamaApi.GenerateRequest;
import org.springframework.ai.ollama.api.OllamaApi.GenerateResponse;
import org.springframework.ai.ollama.api.OllamaApi.Message;
import org.springframework.ai.ollama.api.OllamaApi.Message.Role;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

;

/**
 * @author Christian Tzolov
 * @author Thomas Vitale
 */
@Testcontainers
@DisabledIf("isDisabled")
public class OllamaApiIT extends BaseOllamaIT {

	private static final String MODEL = OllamaModel.ORCA_MINI.getName();

	private static final Logger logger = LoggerFactory.getLogger(OllamaApiIT.class);

	static OllamaApi ollamaApi;

	static String baseUrl = "http://localhost:11434";

	@BeforeAll
	public static void beforeAll() throws IOException, InterruptedException {
		logger.info("Start pulling the '" + MODEL + " ' generative ... would take several minutes ...");
		ollamaContainer.execInContainer("ollama", "pull", MODEL);
		logger.info(MODEL + " pulling competed!");

		baseUrl = "http://" + ollamaContainer.getHost() + ":" + ollamaContainer.getMappedPort(11434);
		ollamaApi = new OllamaApi(baseUrl);
	}

	@Test
	public void generation() {

		var request = GenerateRequest
			.builder("What is the capital of Bulgaria and what is the size? What it the national anthem?")
			.withModel(MODEL)
			.withStream(false)
			.build();

		GenerateResponse response = ollamaApi.generate(request);

		System.out.println(response);

		assertThat(response).isNotNull();
		assertThat(response.model()).isEqualTo(response.model());
		assertThat(response.response()).contains("Sofia");
	}

	@Test
	public void chat() {

		var request = ChatRequest.builder(MODEL)
			.withStream(false)
			.withMessages(List.of(
					Message.builder(Role.SYSTEM)
						.withContent("You are geography teacher. You are talking to a student.")
						.build(),
					Message.builder(Role.USER)
						.withContent("What is the capital of Bulgaria and what is the size? "
								+ "What it the national anthem?")
						.build()))
			.withOptions(OllamaOptions.create().withTemperature(0.9f))
			.build();

		ChatResponse response = ollamaApi.chat(request);

		System.out.println(response);

		assertThat(response).isNotNull();
		assertThat(response.model()).isEqualTo(response.model());
		assertThat(response.done()).isTrue();
		assertThat(response.message().role()).isEqualTo(Role.ASSISTANT);
		assertThat(response.message().content()).contains("Sofia");
	}

	@Test
	public void streamingChat() {

		var request = ChatRequest.builder(MODEL)
			.withStream(true)
			.withMessages(List.of(Message.builder(Role.USER)
				.withContent("What is the capital of Bulgaria and what is the size? " + "What it the national anthem?")
				.build()))
			.withOptions(OllamaOptions.create().withTemperature(0.9f).toMap())
			.build();

		Flux<ChatResponse> response = ollamaApi.streamingChat(request);

		List<ChatResponse> responses = response.collectList().block();
		System.out.println(responses);

		assertThat(response).isNotNull();
		assertThat(responses.stream()
			.filter(r -> r.message() != null)
			.map(r -> r.message().content())
			.collect(Collectors.joining(System.lineSeparator()))).contains("Sofia");

		ChatResponse lastResponse = responses.get(responses.size() - 1);
		assertThat(lastResponse.message().content()).isEmpty();
		assertThat(lastResponse.done()).isTrue();
	}

	@Test
	public void embedText() {

		EmbeddingsRequest request = new EmbeddingsRequest(MODEL, "I like to eat apples");

		EmbeddingsResponse response = ollamaApi.embed(request);

		assertThat(response).isNotNull();
		assertThat(response.embeddings()).hasSize(1);
		assertThat(response.embeddings().get(0)).hasSize(3200);
	}

}