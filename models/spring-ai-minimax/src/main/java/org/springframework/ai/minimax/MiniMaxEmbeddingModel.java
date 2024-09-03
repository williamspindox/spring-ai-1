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
package org.springframework.ai.minimax;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.minimax.api.MiniMaxApi;
import org.springframework.ai.minimax.metadata.MiniMaxUsage;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * MiniMax Embedding Model implementation.
 *
 * @author Geng Rong
 * @author Thomas Vitale
 * @since 1.0.0 M1
 */
public class MiniMaxEmbeddingModel extends AbstractEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(MiniMaxEmbeddingModel.class);

	private final MiniMaxEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final MiniMaxApi miniMaxApi;

	private final MetadataMode metadataMode;

	/**
	 * Constructor for the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi) {
		this(miniMaxApi, MetadataMode.EMBED);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode) {
		this(miniMaxApi, metadataMode,
				MiniMaxEmbeddingOptions.builder().withModel(MiniMaxApi.DEFAULT_EMBEDDING_MODEL).build(),
				RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode The mode for generating metadata.
	 * @param miniMaxEmbeddingOptions The options for MiniMax embedding.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode,
			MiniMaxEmbeddingOptions miniMaxEmbeddingOptions) {
		this(miniMaxApi, metadataMode, miniMaxEmbeddingOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	/**
	 * Initializes a new instance of the MiniMaxEmbeddingModel class.
	 * @param miniMaxApi - The MiniMaxApi instance to use for making API requests.
	 * @param metadataMode - The mode for generating metadata.
	 * @param options - The options for MiniMax embedding.
	 * @param retryTemplate - The RetryTemplate for retrying failed API requests.
	 */
	public MiniMaxEmbeddingModel(MiniMaxApi miniMaxApi, MetadataMode metadataMode, MiniMaxEmbeddingOptions options,
			RetryTemplate retryTemplate) {
		Assert.notNull(miniMaxApi, "MiniMaxApi must not be null");
		Assert.notNull(metadataMode, "metadataMode must not be null");
		Assert.notNull(options, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");

		this.miniMaxApi = miniMaxApi;
		this.metadataMode = metadataMode;
		this.defaultOptions = options;
		this.retryTemplate = retryTemplate;
	}

	@Override
	public float[] embed(Document document) {
		Assert.notNull(document, "Document must not be null");
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@SuppressWarnings("unchecked")
	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		return this.retryTemplate.execute(ctx -> {

			MiniMaxApi.EmbeddingRequest apiRequest = (this.defaultOptions != null)
					? new MiniMaxApi.EmbeddingRequest(request.getInstructions(), this.defaultOptions.getModel())
					: new MiniMaxApi.EmbeddingRequest(request.getInstructions(), MiniMaxApi.DEFAULT_EMBEDDING_MODEL);

			if (request.getOptions() != null && !EmbeddingOptions.EMPTY.equals(request.getOptions())) {
				apiRequest = ModelOptionsUtils.merge(request.getOptions(), apiRequest,
						MiniMaxApi.EmbeddingRequest.class);
			}

			MiniMaxApi.EmbeddingList apiEmbeddingResponse = this.miniMaxApi.embeddings(apiRequest).getBody();

			if (apiEmbeddingResponse == null) {
				logger.warn("No embeddings returned for request: {}", request);
				return new EmbeddingResponse(List.of());
			}

			var metadata = new EmbeddingResponseMetadata(apiEmbeddingResponse.model(),
					MiniMaxUsage.from(new MiniMaxApi.Usage(0, 0, apiEmbeddingResponse.totalTokens())));

			List<Embedding> embeddings = new ArrayList<>();
			for (int i = 0; i < apiEmbeddingResponse.vectors().size(); i++) {
				float[] vector = apiEmbeddingResponse.vectors().get(i);
				embeddings.add(new Embedding(vector, i));
			}
			return new EmbeddingResponse(embeddings, metadata);
		});
	}

}
