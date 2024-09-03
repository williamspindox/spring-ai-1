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
package org.springframework.ai.autoconfigure.chat.observation;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.observation.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatObservationAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
class ChatObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatObservationAutoConfiguration.class));

	@Test
	void meterObservationHandlerEnabled() {
		contextRunner.withBean(CompositeMeterRegistry.class).run(context -> {
			assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class);
		});
	}

	@Test
	void meterObservationHandlerDisabled() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelMeterObservationHandler.class);
		});
	}

	@Test
	void promptFilterDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationFilter.class);
		});
	}

	@Test
	void promptHandlerDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class);
		});
	}

	@Test
	void promptHandlerEnabled() {
		contextRunner
			.withBean(OtelTracer.class, OpenTelemetry.noop().getTracer("test"), new OtelCurrentTraceContext(), null)
			.withPropertyValues("spring.ai.chat.observations.include-prompt=true")
			.run(context -> {
				assertThat(context).hasSingleBean(ChatModelPromptContentObservationHandler.class);
			});
	}

	@Test
	void promptHandlerDisabled() {
		contextRunner.withPropertyValues("spring.ai.chat.observations.include-prompt=true").run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class);
		});
	}

	@Test
	void completionFilterDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelCompletionObservationFilter.class);
		});
	}

	@Test
	void completionHandlerDefault() {
		contextRunner.run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelCompletionObservationHandler.class);
		});
	}

	@Test
	void completionHandlerEnabled() {
		contextRunner
			.withBean(OtelTracer.class, OpenTelemetry.noop().getTracer("test"), new OtelCurrentTraceContext(), null)
			.withPropertyValues("spring.ai.chat.observations.include-completion=true")
			.run(context -> {
				assertThat(context).hasSingleBean(ChatModelCompletionObservationHandler.class);
			});
	}

	@Test
	void completionHandlerDisabled() {
		contextRunner.withPropertyValues("spring.ai.chat.observations.include-completion=true").run(context -> {
			assertThat(context).doesNotHaveBean(ChatModelCompletionObservationHandler.class);
		});
	}

}
