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
package org.springframework.ai.autoconfigure.azure.openai;

import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.ClientOptions;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.policy.HttpLogDetailLevel;
import com.azure.core.http.policy.HttpLogOptions;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.AzureOpenAiEmbeddingModel;
import org.springframework.ai.azure.openai.AzureOpenAiImageModel;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.model.function.FunctionCallbackContext;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

@AutoConfiguration
@ConditionalOnClass({ OpenAIClientBuilder.class, AzureOpenAiChatModel.class })
@EnableConfigurationProperties({ AzureOpenAiChatProperties.class, AzureOpenAiEmbeddingProperties.class,
		AzureOpenAiConnectionProperties.class, AzureOpenAiImageOptionsProperties.class })
public class AzureOpenAiAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public OpenAIClient openAIClient(AzureOpenAiConnectionProperties connectionProperties) {
		/*
		 * https://learn.microsoft.com/en-us/azure/databricks/dev-tools/service-prin-aad-
		 * token
		 */
		if ("ms-entra-id".equals(connectionProperties.getAuthType())) {
			Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");
			Assert.hasText(connectionProperties.getClientId(), "Client ID must not be empty");
			Assert.hasText(connectionProperties.getClientSecret(), "Client Secret must not be empty");
			Assert.hasText(connectionProperties.getTenantId(), "Tenant ID must not be empty");

			AzureProfile azureProfile = new AzureProfile(AzureEnvironment.AZURE);
			TokenCredential tokenCredential = new ClientSecretCredentialBuilder()
				.clientId(connectionProperties.getClientId())
				.clientSecret(connectionProperties.getClientSecret())
				.tenantId(connectionProperties.getTenantId())
				.authorityHost(azureProfile.getEnvironment().getActiveDirectoryEndpoint())
				.build();

			HttpLogOptions options = new HttpLogOptions();
			if (connectionProperties.getEnableLog()) {
				HttpLogDetailLevel level = HttpLogDetailLevel.BODY_AND_HEADERS;
				options.setLogLevel(level);
				options.setPrettyPrintBody(true);
			}

			return new OpenAIClientBuilder().httpLogOptions(options)
				.endpoint(connectionProperties.getEndpoint())
				.credential(tokenCredential)
				.clientOptions(new ClientOptions().setApplicationId("spring-ai"))
				.buildClient();
		}
		else {
			Assert.hasText(connectionProperties.getApiKey(), "API key must not be empty");
			Assert.hasText(connectionProperties.getEndpoint(), "Endpoint must not be empty");

			return new OpenAIClientBuilder().endpoint(connectionProperties.getEndpoint())
				.credential(new AzureKeyCredential(connectionProperties.getApiKey()))
				.clientOptions(new ClientOptions().setApplicationId("spring-ai"))
				.buildClient();
		}
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiChatProperties.CONFIG_PREFIX, name = "enabled", havingValue = "true",
			matchIfMissing = true)
	public AzureOpenAiChatModel azureOpenAiChatModel(OpenAIClient openAIClient,
			AzureOpenAiChatProperties chatProperties, List<FunctionCallback> toolFunctionCallbacks,
			FunctionCallbackContext functionCallbackContext) {

		if (!CollectionUtils.isEmpty(toolFunctionCallbacks)) {
			chatProperties.getOptions().getFunctionCallbacks().addAll(toolFunctionCallbacks);
		}

		AzureOpenAiChatModel azureOpenAiChatModel = new AzureOpenAiChatModel(openAIClient, chatProperties.getOptions(),
				functionCallbackContext);

		return azureOpenAiChatModel;
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiEmbeddingProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public AzureOpenAiEmbeddingModel azureOpenAiEmbeddingModel(OpenAIClient openAIClient,
			AzureOpenAiEmbeddingProperties embeddingProperties) {
		return new AzureOpenAiEmbeddingModel(openAIClient, embeddingProperties.getMetadataMode(),
				embeddingProperties.getOptions());
	}

	@Bean
	@ConditionalOnMissingBean
	public FunctionCallbackContext springAiFunctionManager(ApplicationContext context) {
		FunctionCallbackContext manager = new FunctionCallbackContext();
		manager.setApplicationContext(context);
		return manager;
	}

	@Bean
	@ConditionalOnProperty(prefix = AzureOpenAiImageOptionsProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public AzureOpenAiImageModel azureOpenAiImageClient(OpenAIClient openAIClient,
			AzureOpenAiImageOptionsProperties imageProperties) {

		return new AzureOpenAiImageModel(openAIClient, imageProperties.getOptions());
	}

}
