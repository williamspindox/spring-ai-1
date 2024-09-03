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
package org.springframework.ai.vertexai.gemini;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.model.Media;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
class VertexAiGeminiChatModelIT {

	@Autowired
	private VertexAiGeminiChatModel chatModel;

	@Value("classpath:/prompts/system-message.st")
	private Resource systemResource;

	@Test
	void roleTest() {
		Prompt prompt = createPrompt(VertexAiGeminiChatOptions.builder().build());
		ChatResponse response = chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@Test
	void googleSearchTool() {
		Prompt prompt = createPrompt(VertexAiGeminiChatOptions.builder().withGoogleSearchRetrieval(true).build());
		ChatResponse response = chatModel.call(prompt);
		assertThat(response.getResult().getOutput().getContent()).containsAnyOf("Blackbeard", "Bartholomew");
	}

	@NotNull
	private Prompt createPrompt(VertexAiGeminiChatOptions chatOptions) {
		String request = "Tell me about 3 famous pirates from the Golden Age of Piracy and why they did.";
		String name = "Bob";
		String voice = "pirate";
		UserMessage userMessage = new UserMessage(request);
		SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(systemResource);
		Message systemMessage = systemPromptTemplate.createMessage(Map.of("name", name, "voice", voice));
		Prompt prompt = new Prompt(List.of(userMessage, systemMessage), chatOptions);
		return prompt;
	}

	@Test
	void listOutputConverter() {
		DefaultConversionService conversionService = new DefaultConversionService();
		ListOutputConverter outputParser = new ListOutputConverter(conversionService);

		String format = outputParser.getFormat();
		String template = """
				List five {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "ice cream flavors.", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = this.chatModel.call(prompt).getResult();

		List<String> list = outputParser.convert(generation.getOutput().getContent());
		assertThat(list).hasSize(5);
	}

	@Test
	void mapOutputConverter() {
		MapOutputConverter outputConverter = new MapOutputConverter();

		String format = outputConverter.getFormat();
		String template = """
				Provide me a List of {subject}
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template,
				Map.of("subject", "an array of numbers from 1 to 9 under they key name 'numbers'", "format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatModel.call(prompt).getResult();

		Map<String, Object> result = outputConverter.convert(generation.getOutput().getContent());
		assertThat(result.get("numbers")).isEqualTo(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));

	}

	record ActorsFilmsRecord(String actor, List<String> movies) {
	}

	@Test
	void beanOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConvert = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConvert.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());
		Generation generation = chatModel.call(prompt).getResult();

		ActorsFilmsRecord actorsFilms = outputConvert.convert(generation.getOutput().getContent());
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void textStream() {

		String generationTextFromStream = chatModel.stream(new Prompt("Explain Bulgaria? Answer in 10 paragraphs."))
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		// logger.info("" + actorsFilms);
		assertThat(generationTextFromStream).isNotEmpty();
	}

	@Test
	void beanStreamOutputConverterRecords() {

		BeanOutputConverter<ActorsFilmsRecord> outputConverter = new BeanOutputConverter<>(ActorsFilmsRecord.class);

		String format = outputConverter.getFormat();
		String template = """
				Generate the filmography of 5 movies for Tom Hanks.
				Remove the ```json outer brackets.
				{format}
				""";
		PromptTemplate promptTemplate = new PromptTemplate(template, Map.of("format", format));
		Prompt prompt = new Prompt(promptTemplate.createMessage());

		String generationTextFromStream = chatModel.stream(prompt)
			.collectList()
			.block()
			.stream()
			.map(ChatResponse::getResults)
			.flatMap(List::stream)
			.map(Generation::getOutput)
			.map(AssistantMessage::getContent)
			.collect(Collectors.joining());

		ActorsFilmsRecord actorsFilms = outputConverter.convert(generationTextFromStream);
		// logger.info("" + actorsFilms);
		assertThat(actorsFilms.actor()).isEqualTo("Tom Hanks");
		assertThat(actorsFilms.movies()).hasSize(5);
	}

	@Test
	void multiModalityTest() throws IOException {

		var data = new ClassPathResource("/vertex.test.png");

		var userMessage = new UserMessage("Explain what do you see o this picture?",
				List.of(new Media(MimeTypeUtils.IMAGE_PNG, data)));

		var response = chatModel.call(new Prompt(List.of(userMessage)));

		// Response should contain something like:
		// I see a bunch of bananas in a golden basket. The bananas are ripe and yellow.
		// There are also some red apples in the basket. The basket is sitting on a table.
		// The background is a blurred light blue color.'
		assertThat(response.getResult().getOutput().getContent()).contains("bananas", "apple", "basket");

		// Error with image from URL:
		// com.google.api.gax.rpc.InvalidArgumentException:
		// io.grpc.StatusRuntimeException: INVALID_ARGUMENT: Only GCS URIs are supported
		// in file_uri and please make sure that the path is a valid GCS path.

		// String imageUrl =
		// "https://storage.googleapis.com/github-repo/img/gemini/multimodality_usecases_overview/banana-apple.jpg";

		// userMessage = new UserMessage("Explain what do you see o this picture?",
		// List.of(new Media(MimeTypeDetector.getMimeType(imageUrl), imageUrl)));
		// response = client.call(new Prompt(List.of(userMessage)));

		// assertThat(response.getResult().getOutput().getContent()).contains("bananas",
		// "apple", "basket");

		// https://github.com/GoogleCloudPlatform/generative-ai/blob/main/gemini/use-cases/intro_multimodal_use_cases.ipynb
	}

	@SpringBootConfiguration
	public static class TestConfiguration {

		@Bean
		public VertexAI vertexAiApi() {
			String projectId = System.getenv("VERTEX_AI_GEMINI_PROJECT_ID");
			String location = System.getenv("VERTEX_AI_GEMINI_LOCATION");
			return new VertexAI.Builder().setProjectId(projectId)
				.setLocation(location)
				.setTransport(Transport.REST)
				.build();
		}

		@Bean
		public VertexAiGeminiChatModel vertexAiEmbedding(VertexAI vertexAi) {
			return new VertexAiGeminiChatModel(vertexAi,
					VertexAiGeminiChatOptions.builder()
						.withModel(VertexAiGeminiChatModel.ChatModel.GEMINI_1_5_PRO)
						.build());
		}

	}

}
