/*
 * Copyright 2012-2018 the original author or authors.
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

package org.springframework.boot.web.servlet.support;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.ParentContextApplicationContextInitializer;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ConfigurableWebEnvironment;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.*;
import java.util.Collections;

/**
 * An opinionated {@link WebApplicationInitializer} to run a {@link SpringApplication}
 * from a traditional WAR deployment. Binds {@link Servlet}, {@link Filter} and
 * {@link ServletContextInitializer} beans from the application context to the server.
 * <p>
 * To configure the application either override the
 * {@link #configure(SpringApplicationBuilder)} method (calling
 * {@link SpringApplicationBuilder#sources(Class...)}) or make the initializer itself a
 * {@code @Configuration}. If you are using {@link SpringBootServletInitializer} in
 * combination with other {@link WebApplicationInitializer WebApplicationInitializers} you
 * might also want to add an {@code @Ordered} annotation to configure a specific startup
 * order.
 * <p>
 * Note that a WebApplicationInitializer is only needed if you are building a war file and
 * deploying it. If you prefer to run an embedded web server then you won't need this at
 * all.
 *
 * @author Dave Syer
 * @author Phillip Webb
 * @author Andy Wilkinson
 * @since 2.0.0
 * @see #configure(SpringApplicationBuilder)
 *
 * tomcat启动的时候
 * 当前应用会扫描每个jar包下的META-INF/JAVAX.SERVLET.Servlet.servletContainerInitializer指定的实现类，并且运行它
 * 然后在servletContainerInitializer头上就有一个注解@HandlesTypes(WebApplicationInitializer.class)
 * 我们需要手动去实现 SpringBootServletInitializer传入相关类
 *
 * 	@SpringBootApplication
	public class Application extends SpringBootServletInitializer {
		@Override
		protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
			return application.sources(Application.class);
		}
		public static void main(String[] args) throws Exception {
			SpringApplication.run(Application.class, args);
		}
	}

 	启动的时候会调用当前类的onStartup()方法
 */
public abstract class SpringBootServletInitializer implements WebApplicationInitializer {

	protected Log logger; // Don't initialize early

	private boolean registerErrorPageFilter = true;

	/**
	 * Set if the {@link ErrorPageFilter} should be registered. Set to {@code false} if
	 * error page mappings should be handled via the server and not Spring Boot.
	 * @param registerErrorPageFilter if the {@link ErrorPageFilter} should be registered.
	 */
	protected final void setRegisterErrorPageFilter(boolean registerErrorPageFilter) {
		this.registerErrorPageFilter = registerErrorPageFilter;
	}




	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		// Logger initialization is deferred in case an ordered
		// LogServletContextInitializer is being used
		this.logger = LogFactory.getLog(getClass());
		/**
		 * 创建web应用容器springioc容器
		 */
		WebApplicationContext rootAppContext = createRootApplicationContext(servletContext);
		if (rootAppContext != null) {
			servletContext.addListener(new ContextLoaderListener(rootAppContext) {
				@Override
				public void contextInitialized(ServletContextEvent event) {
					// no-op because the application context is already initialized
					// 什么也不做，因为application context 已经被实例化了，不然就会用这个监听器来实例化springioc容器
					// 参见spring-5注解版（这里对于注解版来说很重要）
				}
			});
		}
		else {
			this.logger.debug("No ContextLoaderListener registered, as "
					+ "createRootApplicationContext() did not "
					+ "return an application context");
		}
	}

	protected WebApplicationContext createRootApplicationContext(
			ServletContext servletContext) {
		// 1、创建SpringApplicationBuilder
		SpringApplicationBuilder builder = createSpringApplicationBuilder();
		// 3. 设置启动类为当前类
		builder.main(getClass());
		//准备环境
		ApplicationContext parent = getExistingRootWebApplicationContext(servletContext);
		if (parent != null) {
			this.logger.info("Root context already created (using as parent).");
			servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, null);
			builder.initializers(new ParentContextApplicationContextInitializer(parent));
		}
		//初始化
		builder.initializers(new ServletContextApplicationContextInitializer(servletContext));
		builder.contextClass(AnnotationConfigServletWebServerApplicationContext.class);
		// 4、调用configure方法，子类重写了这个方法，将SpringBoot的主程序类传入了进来,个性化配置
		builder = configure(builder);
		builder.listeners(new WebEnvironmentPropertySourceInitializer(servletContext));
		SpringApplication application = builder.build();
		//如果sources 为空,并且启动类有@Configuration 注解,则添加当前类到sources中
		if (application.getAllSources().isEmpty() && AnnotationUtils.findAnnotation(getClass(), Configuration.class) != null) {
			application.addPrimarySources(Collections.singleton(getClass()));
		}
		Assert.state(!application.getAllSources().isEmpty(),
				"No SpringApplication sources have been defined. Either override the "
						+ "configure method or add an @Configuration annotation");
		// Ensure error pages are registered
		if (this.registerErrorPageFilter) {
			application.addPrimarySources(Collections.singleton(ErrorPageFilterConfiguration.class));
		}
		//启动应用
		return run(application);
	}

	/**
	 * Returns the {@code SpringApplicationBuilder} that is used to configure and create
	 * the {@link SpringApplication}. The default implementation returns a new
	 * {@code SpringApplicationBuilder} in its default state.
	 * @return the {@code SpringApplicationBuilder}.
	 * @since 1.3.0
	 */
	protected SpringApplicationBuilder createSpringApplicationBuilder() {
		return new SpringApplicationBuilder();
	}

	/**
	 * Called to run a fully configured {@link SpringApplication}.
	 * @param application the application to run
	 * @return the {@link WebApplicationContext}
	 *
	 *
	 * 外部tomcat启动流程
	 */
	protected WebApplicationContext run(SpringApplication application) {
		return (WebApplicationContext) application.run();
	}

	private ApplicationContext getExistingRootWebApplicationContext(
			ServletContext servletContext) {
		Object context = servletContext.getAttribute(
				WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE);
		if (context instanceof ApplicationContext) {
			return (ApplicationContext) context;
		}
		return null;
	}

	/**
	 * Configure the application. Normally all you would need to do is to add sources
	 * (e.g. config classes) because other settings have sensible defaults. You might
	 * choose (for instance) to add default command line arguments, or set an active
	 * Spring profile.
	 * @param builder a builder for the application context
	 * @return the application builder
	 * @see SpringApplicationBuilder
	 */
	protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
		return builder;
	}

	private static final class WebEnvironmentPropertySourceInitializer
			implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, Ordered {

		private final ServletContext servletContext;

		private WebEnvironmentPropertySourceInitializer(ServletContext servletContext) {
			this.servletContext = servletContext;
		}

		@Override
		public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
			ConfigurableEnvironment environment = event.getEnvironment();
			if (environment instanceof ConfigurableWebEnvironment) {
				((ConfigurableWebEnvironment) environment)
						.initPropertySources(this.servletContext, null);
			}
		}

		@Override
		public int getOrder() {
			return Ordered.HIGHEST_PRECEDENCE;
		}

	}

}
