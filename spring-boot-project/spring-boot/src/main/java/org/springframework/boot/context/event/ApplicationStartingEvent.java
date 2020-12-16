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

import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/**
 * Event published as early as conceivably possible as soon as a {@link SpringApplication}
 * has been started - before the {@link Environment} or {@link ApplicationContext} is
 * available, but after the {@link ApplicationListener}s have been registered. The source
 * of the event is the {@link SpringApplication} itself, but beware of using its internal
 * state too much at this early stage since it might be modified later in the lifecycle.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 * @since 1.5.0
 */
@SuppressWarnings("serial")
public class ApplicationStartingEvent extends SpringApplicationEvent {

	/**
	 * Create a new {@link ApplicationStartingEvent} instance.
	 * @param application the current application
	 * @param args the arguments the application is running with
	 *
	 *             ApplicationStartingEvent构造方法中传递了一个SpringApplication对象和args参数。
	 *             一直传递到了父类EventObject，将SpringApplication对象存放在source变量中。
	 *            EventObject getSource()方法得到的就是SpringApplication对象。
	 */
	public ApplicationStartingEvent(SpringApplication application, String[] args) {
		super(application, args);
	}

}
