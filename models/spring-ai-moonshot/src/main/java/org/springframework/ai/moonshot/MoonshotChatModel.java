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
package org.springframework.ai.moonshot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.RateLimit;
import org.springframework.ai.chat.model.AbstractToolCallSupport;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.ai.moonshot.api.MoonshotApi;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletion;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletion.Choice;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionChunk;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionFinishReason;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage.ChatCompletionFunction;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionMessage.ToolCall;
import org.springframework.ai.moonshot.api.MoonshotApi.ChatCompletionRequest;
import org.springframework.ai.moonshot.api.MoonshotApi.FunctionTool;
import org.springframework.ai.moonshot.metadata.MoonshotUsage;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Geng Rong
 */
public class MoonshotChatModel extends AbstractToolCallSupport implements ChatModel, StreamingChatModel {

	private static final Logger logger = LoggerFactory.getLogger(MoonshotChatModel.class);

	/**
	 * The default options used for the chat completion requests.
	 */
	private final MoonshotChatOptions defaultOptions;

	/**
	 * Low-level access to the Moonshot API.
	 */
	private final MoonshotApi moonshotApi;

	private final RetryTemplate retryTemplate;

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi) {
		this(moonshotApi, MoonshotChatOptions.builder().withModel(MoonshotApi.DEFAULT_CHAT_MODEL).build());
	}

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 * @param options The MoonshotChatOptions to configure the chat client.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi, MoonshotChatOptions options) {
		this(moonshotApi, options, null, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 * @param options The MoonshotChatOptions to configure the chat client.
	 * @param functionCallbackContext The function callback context.
	 * @param retryTemplate The retry template.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi, MoonshotChatOptions options,
			FunctionCallbackContext functionCallbackContext, RetryTemplate retryTemplate) {
		this(moonshotApi, options, functionCallbackContext, List.of(), retryTemplate);
	}

	/**
	 * Initializes a new instance of the MoonshotChatModel.
	 * @param moonshotApi The Moonshot instance to be used for interacting with the
	 * Moonshot Chat API.
	 * @param options The MoonshotChatOptions to configure the chat client.
	 * @param functionCallbackContext The function callback context.
	 * @param toolFunctionCallbacks The tool function callbacks.
	 * @param retryTemplate The retry template.
	 */
	public MoonshotChatModel(MoonshotApi moonshotApi, MoonshotChatOptions options,
			FunctionCallbackContext functionCallbackContext, List<FunctionCallback> toolFunctionCallbacks,
			RetryTemplate retryTemplate) {
		super(functionCallbackContext, options, toolFunctionCallbacks);
		Assert.notNull(moonshotApi, "MoonshotApi must not be null");
		Assert.notNull(options, "Options must not be null");
		Assert.notNull(retryTemplate, "RetryTemplate must not be null");
		Assert.isTrue(CollectionUtils.isEmpty(options.getFunctionCallbacks()),
				"The default function callbacks must be set via the toolFunctionCallbacks constructor parameter");
		this.moonshotApi = moonshotApi;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public ChatResponse call(Prompt prompt) {
		ChatCompletionRequest request = createRequest(prompt, false);

		ResponseEntity<ChatCompletion> completionEntity = this.retryTemplate
			.execute(ctx -> this.moonshotApi.chatCompletionEntity(request));

		var chatCompletion = completionEntity.getBody();

		if (chatCompletion == null) {
			logger.warn("No chat completion returned for prompt: {}", prompt);
			return new ChatResponse(List.of());
		}

		List<Choice> choices = chatCompletion.choices();
		if (choices == null) {
			logger.warn("No choices returned for prompt: {}", prompt);
			return new ChatResponse(List.of());
		}

		List<Generation> generations = choices.stream().map(choice -> {
			// @formatter:off
			Map<String, Object> metadata = Map.of(
					"id", chatCompletion.id(),
					"role", choice.message().role() != null ? choice.message().role().name() : "",
					"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
			// @formatter:on
			return buildGeneration(choice, metadata);
		}).toList();

		ChatResponse chatResponse = new ChatResponse(generations, from(completionEntity.getBody()));

		if (isToolCall(chatResponse, Set.of(MoonshotApi.ChatCompletionFinishReason.TOOL_CALLS.name(),
				MoonshotApi.ChatCompletionFinishReason.STOP.name()))) {
			var toolCallConversation = handleToolCalls(prompt, chatResponse);
			// Recursively call the call method with the tool call message
			// conversation that contains the call responses.
			return this.call(new Prompt(toolCallConversation, prompt.getOptions()));
		}

		return chatResponse;
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return this.defaultOptions.copy();
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {
		ChatCompletionRequest request = createRequest(prompt, true);

		Flux<ChatCompletionChunk> completionChunks = this.retryTemplate
			.execute(ctx -> this.moonshotApi.chatCompletionStream(request));

		// For chunked responses, only the first chunk contains the choice role.
		// The rest of the chunks with same ID share the same role.
		ConcurrentHashMap<String, String> roleMap = new ConcurrentHashMap<>();

		// Convert the ChatCompletionChunk into a ChatCompletion to be able to reuse
		// the function call handling logic.
		Flux<ChatResponse> chatResponse = completionChunks.map(this::chunkToChatCompletion)
			.switchMap(chatCompletion -> Mono.just(chatCompletion).map(chatCompletion2 -> {
				try {
					@SuppressWarnings("null")
					String id = chatCompletion2.id();

			// @formatter:off
						List<Generation> generations = chatCompletion2.choices().stream().map(choice -> {
							if (choice.message().role() != null) {
								roleMap.putIfAbsent(id, choice.message().role().name());
							}
							Map<String, Object> metadata = Map.of(
									"id", chatCompletion2.id(),
									"role", roleMap.getOrDefault(id, ""),
									"finishReason", choice.finishReason() != null ? choice.finishReason().name() : "");
							return buildGeneration(choice, metadata);
						}).toList();
						// @formatter:on

					if (chatCompletion2.usage() != null) {
						return new ChatResponse(generations, from(chatCompletion2));
					}
					else {
						return new ChatResponse(generations);
					}
				}
				catch (Exception e) {
					logger.error("Error processing chat completion", e);
					return new ChatResponse(List.of());
				}

			}));

		return chatResponse.flatMap(response -> {

			if (isToolCall(response, Set.of(ChatCompletionFinishReason.TOOL_CALLS.name(), "stop"))) {
				var toolCallConversation = handleToolCalls(prompt, response);
				// Recursively call the stream method with the tool call message
				// conversation that contains the call responses.
				return this.stream(new Prompt(toolCallConversation, prompt.getOptions()));
			}
			else {
				return Flux.just(response);
			}
		});
	}

	private ChatResponseMetadata from(ChatCompletion result, RateLimit rateLimit) {
		Assert.notNull(result, "Moonshot ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withUsage(MoonshotUsage.from(result.usage()))
			.withModel(result.model())
			.withRateLimit(rateLimit)
			.withKeyValue("created", result.created())
			.build();
	}

	private ChatResponseMetadata from(ChatCompletion result) {
		Assert.notNull(result, "Moonshot ChatCompletionResult must not be null");
		return ChatResponseMetadata.builder()
			.withId(result.id())
			.withUsage(MoonshotUsage.from(result.usage()))
			.withModel(result.model())
			.withKeyValue("created", result.created())
			.build();
	}

	private static Generation buildGeneration(Choice choice, Map<String, Object> metadata) {
		List<AssistantMessage.ToolCall> toolCalls = choice.message().toolCalls() == null ? List.of()
				: choice.message()
					.toolCalls()
					.stream()
					.map(toolCall -> new AssistantMessage.ToolCall(toolCall.id(), "function",
							toolCall.function().name(), toolCall.function().arguments()))
					.toList();

		var assistantMessage = new AssistantMessage(choice.message().content(), metadata, toolCalls);
		String finishReason = (choice.finishReason() != null ? choice.finishReason().name() : "");
		var generationMetadata = ChatGenerationMetadata.from(finishReason, null);
		return new Generation(assistantMessage, generationMetadata);
	}

	/**
	 * Convert the ChatCompletionChunk into a ChatCompletion. The Usage is set to null.
	 * @param chunk the ChatCompletionChunk to convert
	 * @return the ChatCompletion
	 */
	private ChatCompletion chunkToChatCompletion(ChatCompletionChunk chunk) {
		List<ChatCompletion.Choice> choices = chunk.choices().stream().map(cc -> {
			ChatCompletionMessage delta = cc.delta();
			if (delta == null) {
				delta = new ChatCompletionMessage("", ChatCompletionMessage.Role.ASSISTANT);
			}
			return new ChatCompletion.Choice(cc.index(), delta, cc.finishReason());
		}).toList();

		return new ChatCompletion(chunk.id(), "chat.completion", chunk.created(), chunk.model(), choices, null);
	}

	/**
	 * Accessible for testing.
	 */
	public MoonshotApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {

		List<ChatCompletionMessage> chatCompletionMessages = prompt.getInstructions().stream().map(message -> {
			if (message.getMessageType() == MessageType.USER || message.getMessageType() == MessageType.SYSTEM) {
				Object content = message.getContent();
				return List.of(new ChatCompletionMessage(content,
						ChatCompletionMessage.Role.valueOf(message.getMessageType().name())));
			}
			else if (message.getMessageType() == MessageType.ASSISTANT) {
				var assistantMessage = (AssistantMessage) message;
				List<ToolCall> toolCalls = null;
				if (!CollectionUtils.isEmpty(assistantMessage.getToolCalls())) {
					toolCalls = assistantMessage.getToolCalls().stream().map(toolCall -> {
						var function = new ChatCompletionFunction(toolCall.name(), toolCall.arguments());
						return new ToolCall(toolCall.id(), toolCall.type(), function);
					}).toList();
				}
				return List.of(new ChatCompletionMessage(assistantMessage.getContent(),
						ChatCompletionMessage.Role.ASSISTANT, null, null, toolCalls));
			}
			else if (message.getMessageType() == MessageType.TOOL) {
				ToolResponseMessage toolMessage = (ToolResponseMessage) message;

				toolMessage.getResponses().forEach(response -> {
					Assert.isTrue(response.id() != null, "ToolResponseMessage must have an id");
					Assert.isTrue(response.name() != null, "ToolResponseMessage must have a name");
				});

				return toolMessage.getResponses()
					.stream()
					.map(tr -> new ChatCompletionMessage(tr.responseData(), ChatCompletionMessage.Role.TOOL, tr.name(),
							tr.id(), null))
					.toList();
			}
			else {
				throw new IllegalArgumentException("Unsupported message type: " + message.getMessageType());
			}
		}).flatMap(List::stream).toList();

		ChatCompletionRequest request = new ChatCompletionRequest(chatCompletionMessages, stream);

		Set<String> enabledToolsToUse = new HashSet<>();

		if (prompt.getOptions() != null) {
			MoonshotChatOptions updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(),
					ChatOptions.class, MoonshotChatOptions.class);

			enabledToolsToUse.addAll(this.runtimeFunctionCallbackConfigurations(updatedRuntimeOptions));

			request = ModelOptionsUtils.merge(updatedRuntimeOptions, request, ChatCompletionRequest.class);
		}

		if (!CollectionUtils.isEmpty(this.defaultOptions.getFunctions())) {
			enabledToolsToUse.addAll(this.defaultOptions.getFunctions());
		}

		request = ModelOptionsUtils.merge(request, this.defaultOptions, ChatCompletionRequest.class);

		// Add the enabled functions definitions to the request's tools parameter.
		if (!CollectionUtils.isEmpty(enabledToolsToUse)) {

			request = ModelOptionsUtils.merge(
					MoonshotChatOptions.builder().withTools(this.getFunctionTools(enabledToolsToUse)).build(), request,
					ChatCompletionRequest.class);
		}

		return request;
	}

	private List<FunctionTool> getFunctionTools(Set<String> functionNames) {
		return this.resolveFunctionCallbacks(functionNames).stream().map(functionCallback -> {
			var function = new FunctionTool.Function(functionCallback.getDescription(), functionCallback.getName(),
					functionCallback.getInputTypeSchema());
			return new FunctionTool(function);
		}).toList();
	}

}
