/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.endpoint.invoker.cache;

import java.security.Principal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.SecurityContext;
import org.springframework.boot.actuate.endpoint.invoke.MissingParametersException;
import org.springframework.boot.actuate.endpoint.invoke.OperationInvoker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link CachingOperationInvoker}.
 *
 * @author Stephane Nicoll
 * @author Christoph Dreis
 * @author Phillip Webb
 */
public class CachingOperationInvokerTests {

	private static final long CACHE_TTL = Duration.ofHours(1).toMillis();

	@Test
	public void createInstanceWithTtlSetToZero() {
		assertThatIllegalArgumentException()
				.isThrownBy(() -> new CachingOperationInvoker(mock(OperationInvoker.class), 0))
				.withMessageContaining("TimeToLive");
	}

	@Test
	public void cacheInTtlRangeWithNoParameter() {
		assertCacheIsUsed(Collections.emptyMap());
	}

	@Test
	public void cacheInTtlWithNullParameters() {
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("first", null);
		parameters.put("second", null);
		assertCacheIsUsed(parameters);
	}

	@Test
	public void cacheInTtlWithMonoResponse() {
		MonoOperationInvoker.invocations = new AtomicInteger();
		MonoOperationInvoker target = new MonoOperationInvoker();
		InvocationContext context = new InvocationContext(mock(SecurityContext.class), Collections.emptyMap());
		CachingOperationInvoker invoker = new CachingOperationInvoker(target, CACHE_TTL);
		Object response = ((Mono<?>) invoker.invoke(context)).block();
		Object cachedResponse = ((Mono<?>) invoker.invoke(context)).block();
		assertThat(MonoOperationInvoker.invocations).hasValue(1);
		assertThat(response).isSameAs(cachedResponse);
	}

	@Test
	public void cacheInTtlWithFluxResponse() {
		FluxOperationInvoker.invocations = new AtomicInteger();
		FluxOperationInvoker target = new FluxOperationInvoker();
		InvocationContext context = new InvocationContext(mock(SecurityContext.class), Collections.emptyMap());
		CachingOperationInvoker invoker = new CachingOperationInvoker(target, CACHE_TTL);
		Object response = ((Flux<?>) invoker.invoke(context)).blockLast();
		Object cachedResponse = ((Flux<?>) invoker.invoke(context)).blockLast();
		assertThat(FluxOperationInvoker.invocations).hasValue(1);
		assertThat(response).isSameAs(cachedResponse);
	}

	private void assertCacheIsUsed(Map<String, Object> parameters) {
		OperationInvoker target = mock(OperationInvoker.class);
		Object expected = new Object();
		InvocationContext context = new InvocationContext(mock(SecurityContext.class), parameters);
		given(target.invoke(context)).willReturn(expected);
		CachingOperationInvoker invoker = new CachingOperationInvoker(target, CACHE_TTL);
		Object response = invoker.invoke(context);
		assertThat(response).isSameAs(expected);
		verify(target, times(1)).invoke(context);
		Object cachedResponse = invoker.invoke(context);
		assertThat(cachedResponse).isSameAs(response);
		verifyNoMoreInteractions(target);
	}

	@Test
	public void targetAlwaysInvokedWithParameters() {
		OperationInvoker target = mock(OperationInvoker.class);
		Map<String, Object> parameters = new HashMap<>();
		parameters.put("test", "value");
		parameters.put("something", null);
		InvocationContext context = new InvocationContext(mock(SecurityContext.class), parameters);
		given(target.invoke(context)).willReturn(new Object());
		CachingOperationInvoker invoker = new CachingOperationInvoker(target, CACHE_TTL);
		invoker.invoke(context);
		invoker.invoke(context);
		invoker.invoke(context);
		verify(target, times(3)).invoke(context);
	}

	@Test
	public void targetAlwaysInvokedWithPrincipal() {
		OperationInvoker target = mock(OperationInvoker.class);
		Map<String, Object> parameters = new HashMap<>();
		SecurityContext securityContext = mock(SecurityContext.class);
		given(securityContext.getPrincipal()).willReturn(mock(Principal.class));
		InvocationContext context = new InvocationContext(securityContext, parameters);
		given(target.invoke(context)).willReturn(new Object());
		CachingOperationInvoker invoker = new CachingOperationInvoker(target, CACHE_TTL);
		invoker.invoke(context);
		invoker.invoke(context);
		invoker.invoke(context);
		verify(target, times(3)).invoke(context);
	}

	@Test
	public void targetInvokedWhenCacheExpires() throws InterruptedException {
		OperationInvoker target = mock(OperationInvoker.class);
		Map<String, Object> parameters = new HashMap<>();
		InvocationContext context = new InvocationContext(mock(SecurityContext.class), parameters);
		given(target.invoke(context)).willReturn(new Object());
		CachingOperationInvoker invoker = new CachingOperationInvoker(target, 50L);
		invoker.invoke(context);
		Thread.sleep(55);
		invoker.invoke(context);
		verify(target, times(2)).invoke(context);
	}

	private static class MonoOperationInvoker implements OperationInvoker {

		static AtomicInteger invocations = new AtomicInteger();

		@Override
		public Mono<String> invoke(InvocationContext context) throws MissingParametersException {
			return Mono.fromCallable(() -> {
				invocations.incrementAndGet();
				return "test";
			});
		}

	}

	private static class FluxOperationInvoker implements OperationInvoker {

		static AtomicInteger invocations = new AtomicInteger();

		@Override
		public Flux<String> invoke(InvocationContext context) throws MissingParametersException {
			return Flux.just("spring", "boot").hide().doFirst(invocations::incrementAndGet);
		}

	}

}