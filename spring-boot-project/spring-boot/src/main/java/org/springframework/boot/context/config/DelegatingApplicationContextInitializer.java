/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.context.config;

import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ApplicationContextInitializer} that delegates to other initializers that are
 * specified under a {@literal context.initializer.classes} environment property.
 *
 * @author Dave Syer
 * @author Phillip Webb
 *
 *
 * 顾名思义，这个初始化器实际上将初始化的工作委托给context.initializer.classes环境变量指定的初始化器(通过类名)，
 * 这个解释牛逼
 * 这个初始化器的优先级是Spring Boot定义的4个初始化器中优先级别最高的，因此会被第一个执行
 *
 */
public class DelegatingApplicationContextInitializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	// NOTE: Similar to org.springframework.web.context.ContextLoader

	private static final String PROPERTY_NAME = "context.initializer.classes";

	private int order = 0;

	/**
	 * 这里用到了委托模式
	 * @param context
	 */
	@Override
	public void initialize(ConfigurableApplicationContext context) {
		ConfigurableEnvironment environment = context.getEnvironment();

		List<Class<?>> initializerClasses = getInitializerClasses(environment);
		if (!initializerClasses.isEmpty()) {
			//开始调用具体ApplicationContextInitializer类中的initialize方法。
			applyInitializerClasses(context, initializerClasses);
		}
	}

	private List<Class<?>> getInitializerClasses(ConfigurableEnvironment env) {
		//PROPERTY_NAME = "context.initializer.classes";
		//通过env获取到context.initializer.classes配置的值，如果有则直接获取到具体的值并进行实例化。
		String classNames = env.getProperty(PROPERTY_NAME);
		List<Class<?>> classes = new ArrayList<>();
		if (StringUtils.hasLength(classNames)) {
			/**
			 * 多个配置用逗号隔开
			 */
			for (String className : StringUtils.tokenizeToStringArray(classNames, ",")) {
				classes.add(getInitializerClass(className));
			}
		}
		return classes;
	}

	/**
	 *
	 * @param className
	 * @return
	 * @throws LinkageError
	 */
	private Class<?> getInitializerClass(String className) throws LinkageError {
		try {
			Class<?> initializerClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
			Assert.isAssignable(ApplicationContextInitializer.class, initializerClass);
			return initializerClass;
		}
		catch (ClassNotFoundException ex) {
			throw new ApplicationContextException(
					"Failed to load context initializer class [" + className + "]", ex);
		}
	}

	private void applyInitializerClasses(ConfigurableApplicationContext context,
			List<Class<?>> initializerClasses) {
		Class<?> contextClass = context.getClass();
		List<ApplicationContextInitializer<?>> initializers = new ArrayList<>();
		for (Class<?> initializerClass : initializerClasses) {
			initializers.add(instantiateInitializer(contextClass, initializerClass));
		}
		applyInitializers(context, initializers);
	}

	private ApplicationContextInitializer<?> instantiateInitializer(Class<?> contextClass,
			Class<?> initializerClass) {
		Class<?> requireContextClass = GenericTypeResolver.resolveTypeArgument(
				initializerClass, ApplicationContextInitializer.class);
		Assert.isAssignable(requireContextClass, contextClass,
				String.format(
						"Could not add context initializer [%s]"
								+ " as its generic parameter [%s] is not assignable "
								+ "from the type of application context used by this "
								+ "context loader [%s]: ",
						initializerClass.getName(), requireContextClass.getName(),
						contextClass.getName()));
		return (ApplicationContextInitializer<?>) BeanUtils
				.instantiateClass(initializerClass);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void applyInitializers(ConfigurableApplicationContext context,
			List<ApplicationContextInitializer<?>> initializers) {
		initializers.sort(new AnnotationAwareOrderComparator());
		for (ApplicationContextInitializer initializer : initializers) {
			initializer.initialize(context);
		}
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

}
