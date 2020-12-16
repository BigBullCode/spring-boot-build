/*
 * Copyright 2012-2019 the original author or authors.
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

package org.springframework.boot.context.event;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

/**
 * {@link SpringApplicationRunListener} to publish {@link SpringApplicationEvent}s.
 * <p>
 * Uses an internal {@link ApplicationEventMulticaster} for the events that are fired
 * before the context is actually refreshed.
 *
 * @author Phillip Webb
 * @author Stephane Nicoll
 * @author Andy Wilkinson
 * @author Artsiom Yudovin
 * @since 1.0.0
 */
public class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {

	private final SpringApplication application;

	private final String[] args;

	private final SimpleApplicationEventMulticaster initialMulticaster;

	public EventPublishingRunListener(SpringApplication application, String[] args) {
		this.application = application;
		this.args = args;
		//该对象主要作用为发布事件，用于广播事件到所有监听器
		this.initialMulticaster = new SimpleApplicationEventMulticaster();
		for (ApplicationListener<?> listener : application.getListeners()) {
			//调用的是父类的方法，把listener循环添加到父类的ListenerRetriever对象的Set集合中
			this.initialMulticaster.addApplicationListener(listener);
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

	@Override
	public void starting() {
		this.initialMulticaster.multicastEvent(new ApplicationStartingEvent(this.application, this.args));
	}

	@Override
	public void environmentPrepared(ConfigurableEnvironment environment) {
		this.initialMulticaster
				.multicastEvent(new ApplicationEnvironmentPreparedEvent(this.application, this.args, environment));
	}

	/**
	 * 	 *  这里的this.listeners只有一个，是EventPublishingRunListener类。
	 * 	 * 实际上调用了EventPublishingRunListener类的contextPrepared方法，
	 * 	 * 而这个方法里面还是空的，等于什么都没做。
	 * @param context the application context
	 */
	@Override
	public void contextPrepared(ConfigurableApplicationContext context) {
		this.initialMulticaster
				.multicastEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
	}

	/**
	 * 8.1 获取SpringApplication实例化的时候创建的10个监听器。
		 * org.springframework.boot.ClearCachesApplicationListener
		 * org.springframework.boot.builder.ParentContextCloserApplicationListener
		 * org.springframework.boot.context.FileEncodingApplicationListener
		 * org.springframework.boot.context.config.AnsiOutputApplicationListener
		 * org.springframework.boot.context.config.ConfigFileApplicationListener
		 * org.springframework.boot.context.config.DelegatingApplicationListener
		 * org.springframework.boot.liquibase.LiquibaseServiceLocatorApplicationListener
		 * org.springframework.boot.logging.ClasspathLoggingApplicationListener
		 * org.springframework.boot.logging.LoggingApplicationListener
		 * org.springframework.boot.autoconfigure.BackgroundPreinitializer
	 * 8.2 如果该监听器是ApplicationContextAware的实例，则把ApplicationContext设置到监听器中。
	 *
	 * 8.3 把SpringApplication实例化的时候创建的10个监听器Listener注册到ApplicationContext容器中。
	 *
	 * 8.4 广播出ApplicationPreparedEvent事件给相应的监听器。
	 *
	 * 这里有如下几个监听器，接受到了该事件，并且做出相应的逻辑
	 * ConfigFileApplicationListener：给ApplicationContext添加BeanFactoryPostProcessor后置处理器PropertySourceOrderingPostProcessor
	 * LoggingApplicationListener：给beanFactory注册了单例 springBootLoggingSystem
	 * BackgroundPreinitializer由于这个监听器没有监听ApplicationPreparedEvent事件，所以什么逻辑都没做
	 * DelegatingApplicationListener由于这个监听器没有监听ApplicationPreparedEvent事件，所以什么逻辑都没做
	 * @param context the application context
	 */
	@Override
	public void contextLoaded(ConfigurableApplicationContext context) {
		for (ApplicationListener<?> listener : this.application.getListeners()) {
			if (listener instanceof ApplicationContextAware) {
				((ApplicationContextAware) listener).setApplicationContext(context);
			}
			context.addApplicationListener(listener);
		}
		this.initialMulticaster.multicastEvent(new ApplicationPreparedEvent(this.application, this.args, context));
	}

	/**
	 * 在容器启动完成后会广播一个SpringApplicationEvent事件，
	 * 而SpringApplicationEvent事件是继承自ApplicationEvent事件的
	 * @param context the application context.
	 */
	@Override
	public void started(ConfigurableApplicationContext context) {
		context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context));
	}

	@Override
	public void running(ConfigurableApplicationContext context) {
		context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context));
	}

	@Override
	public void failed(ConfigurableApplicationContext context, Throwable exception) {
		ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
		if (context != null && context.isActive()) {
			// Listeners have been registered to the application context so we should
			// use it at this point if we can
			context.publishEvent(event);
		}
		else {
			// An inactive context may not have a multicaster so we use our multicaster to
			// call all of the context's listeners instead
			if (context instanceof AbstractApplicationContext) {
				for (ApplicationListener<?> listener : ((AbstractApplicationContext) context)
						.getApplicationListeners()) {
					this.initialMulticaster.addApplicationListener(listener);
				}
			}
			this.initialMulticaster.setErrorHandler(new LoggingErrorHandler());
			this.initialMulticaster.multicastEvent(event);
		}
	}

	private static class LoggingErrorHandler implements ErrorHandler {

		private static final Log logger = LogFactory.getLog(EventPublishingRunListener.class);

		@Override
		public void handleError(Throwable throwable) {
			logger.warn("Error calling ApplicationEventListener", throwable);
		}

	}

}
