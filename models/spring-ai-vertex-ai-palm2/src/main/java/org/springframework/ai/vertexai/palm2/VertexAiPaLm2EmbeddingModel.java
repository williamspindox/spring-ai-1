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
package org.springframework.ai.vertexai.palm2;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vertexai.palm2.api.VertexAiPaLm2Api;

/**
 * @author Christian Tzolov
 */
public class VertexAiPaLm2EmbeddingModel extends AbstractEmbeddingModel {

	private final VertexAiPaLm2Api vertexAiApi;

	public VertexAiPaLm2EmbeddingModel(VertexAiPaLm2Api vertexAiApi) {
		this.vertexAiApi = vertexAiApi;
	}

	@Override
	public float[] embed(Document document) {
		return embed(document.getContent());
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {
		List<VertexAiPaLm2Api.Embedding> vertexEmbeddings = this.vertexAiApi.batchEmbedText(request.getInstructions());
		AtomicInteger indexCounter = new AtomicInteger(0);
		List<Embedding> embeddings = vertexEmbeddings.stream()
			.map(vm -> new Embedding(vm.value(), indexCounter.getAndIncrement()))
			.toList();
		return new EmbeddingResponse(embeddings);

	}

}
