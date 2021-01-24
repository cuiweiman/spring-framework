/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.web.context;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * 由于实现了 ServletContextListener 接口，因此在 ServletContext 启动后，会自动执行 {@link #contextInitialized(ServletContextEvent)}方法。
 * <p>
 * Spring MVC 功能的实现 辅助功能（真正的逻辑实现是 {@link org.springframework.web.servlet.DispatcherServlet}），web.xml文件中重要配置的功能。本类的作用是，
 * 在启动 Web 容器时，自动装配 ApplicationContext 的配置信息。因为它实现了 ServletContextListener 接口，在web.xml
 * 配置这个监听器，那么启动容器时，就会默认执行它实现的方法。使用 ServletContextListener 接口，开发者能够在为客户端请求
 * 提供服务之前，向ServletContext 中添加任意对象。这个对象在ServletContext 启动时就被初始化，然后在 ServletContext 的
 * 整个运行期间都是可见的。
 * <p>
 * 每个 Web 应用都有一个 ServletContext 与之关联。ServletContext 对象在应用启动时被创建，在应用关闭时被销毁。ServletContext
 * 在全局范围内有效，类似于应用中的全局变量。在 ServletContextListener 中的核心逻辑便是初始化 WebApplicationContext 实例，并
 * 存放至 ServletContext 中。
 * <p>
 * <pre class="code">
 * 	<context-param>
 * 		<param-name>contextConfigLocation</param-name>
 * 		<param-name>classpath:applicationContext.xml</param-name>
 * </context-param>
 * </pre>
 * <p>
 * Spring MVC分离了控制器、模型对象、分派器以及处理程序对象的角色，这种分离使得它们更容易被定制。
 * Spring的MVC是基于Servlet功能实现的，通过Servlet接口的 DispatcherServlet 来封装其核心功能实现，
 * 通过将请求分派给处理程序，同时带有可配置的处理程序映射、视图解析、本地语言、主题解析以及上载文件支持。
 * 默认的处理程序是非常简单的Controller接口，只有一个方法 ModelAndView handleRequest(request,response)。
 * Spring提供了一个控制器层次结构，可以派生子类。如果应用程序需要处理用户输入表单，那么可以继承 AbstractFormController。
 * 如果需要把多页输入处理到一个表单，那么可以继承 AbstractWizardFormController。
 * 对SpringMVC或者其他成熟的MVC框架而言，解决的问题都是以下几点：
 * 1. 将 web 页面的请求传给服务器；
 * 2. 根据不同的请求，处理不同的逻辑单元；
 * 3. 返回处理结果数据，并跳转至相应的页面。
 * <p>
 * SpringMVC的实现原理是通过servlet拦截所有的 URL 来达到控制目的，因此必需配置 web.xml 来初始化配置信息，
 * 比如：welcome 页面、servlet、servlet-mapping、filter、listener、启动加载级别等。配置中最关键的两处是：
 * 1. contextConfigLocation：使 Web 与 Spring 的配置文件相结合。使用 ContextLoaderListener 配置，
 * 用于指定 Spring 的配置信息；
 * 2. DispatcherServlet：包含了 SpringMVC 的请求逻辑，Spring 使用此类拦截 Web 请求并进行相应的逻辑处理。
 * <p>
 * SpringMVC的每个请求最后返回 ModelAndView 类型，包含了视图以及视图显示的模型数据。
 * <p>
 * 启动和关闭 Spring root WebApplicationContext 的引导监听器。只需委托给 ContextLoader 和 ContextCleanupListener。
 * <p>
 * Bootstrap listener to start up and shut down Spring's root {@link WebApplicationContext}.
 * Simply delegates to {@link ContextLoader} as well as to {@link ContextCleanupListener}.
 *
 * <p>As of Spring 3.1, {@code ContextLoaderListener} supports injecting the root web
 * application context via the {@link #ContextLoaderListener(WebApplicationContext)}
 * constructor, allowing for programmatic configuration in Servlet 3.0+ environments.
 * See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see #setContextInitializers
 * @see org.springframework.web.WebApplicationInitializer
 * @see ServletContextListener：在系统启动时，添加自定义的属性，以便在全局范围内调用（系统启动时，回调用{@link ServletContextListener#contextInitialized(ServletContextEvent)}方法）。
 * @since 17.02.2003
 */
public class ContextLoaderListener extends ContextLoader implements ServletContextListener {

	/**
	 * Create a new {@code ContextLoaderListener} that will create a web application
	 * context based on the "contextClass" and "contextConfigLocation" servlet
	 * context-params. See {@link ContextLoader} superclass documentation for details on
	 * default values for each.
	 * <p>This constructor is typically used when declaring {@code ContextLoaderListener}
	 * as a {@code <listener>} within {@code web.xml}, where a no-arg constructor is
	 * required.
	 * <p>The created application context will be registered into the ServletContext under
	 * the attribute name {@link WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE}
	 * and the Spring application context will be closed when the {@link #contextDestroyed}
	 * lifecycle method is invoked on this listener.
	 *
	 * @see ContextLoader
	 * @see #ContextLoaderListener(WebApplicationContext)
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener() {
	}

	/**
	 * Create a new {@code ContextLoaderListener} with the given application context. This
	 * constructor is useful in Servlet 3.0+ environments where instance-based
	 * registration of listeners is possible through the {@link javax.servlet.ServletContext#addListener}
	 * API.
	 * <p>The context may or may not yet be {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#refresh() refreshed}. If it
	 * (a) is an implementation of {@link ConfigurableWebApplicationContext} and
	 * (b) has <strong>not</strong> already been refreshed (the recommended approach),
	 * then the following will occur:
	 * <ul>
	 * <li>If the given context has not already been assigned an {@linkplain
	 * org.springframework.context.ConfigurableApplicationContext#setId id}, one will be assigned to it</li>
	 * <li>{@code ServletContext} and {@code ServletConfig} objects will be delegated to
	 * the application context</li>
	 * <li>{@link #customizeContext} will be called</li>
	 * <li>Any {@link org.springframework.context.ApplicationContextInitializer ApplicationContextInitializer org.springframework.context.ApplicationContextInitializer ApplicationContextInitializers}
	 * specified through the "contextInitializerClasses" init-param will be applied.</li>
	 * <li>{@link org.springframework.context.ConfigurableApplicationContext#refresh refresh()} will be called</li>
	 * </ul>
	 * If the context has already been refreshed or does not implement
	 * {@code ConfigurableWebApplicationContext}, none of the above will occur under the
	 * assumption that the user has performed these actions (or not) per his or her
	 * specific needs.
	 * <p>See {@link org.springframework.web.WebApplicationInitializer} for usage examples.
	 * <p>In any case, the given application context will be registered into the
	 * ServletContext under the attribute name {@link
	 * WebApplicationContext#ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE} and the Spring
	 * application context will be closed when the {@link #contextDestroyed} lifecycle
	 * method is invoked on this listener.
	 *
	 * @param context the application context to manage
	 * @see #contextInitialized(ServletContextEvent)
	 * @see #contextDestroyed(ServletContextEvent)
	 */
	public ContextLoaderListener(WebApplicationContext context) {
		super(context);
	}


	/**
	 * 初始化 WebApplicationContext 。
	 * 在Web 应用中，我们会用到 WebApplicationContext，继承自 ApplicationContext，并且在其基础上又追加了一些特定于
	 * Web 的操作和属性。类似于 ClassPathXmlApplicationContext。
	 * <p>
	 * Initialize the root web application context.
	 *
	 * @see #initWebApplicationContext(ServletContext)
	 */
	@Override
	public void contextInitialized(ServletContextEvent event) {
		// 初始化 WebApplicationContext 。
		initWebApplicationContext(event.getServletContext());
	}


	/**
	 * Close the root web application context.
	 */
	@Override
	public void contextDestroyed(ServletContextEvent event) {
		closeWebApplicationContext(event.getServletContext());
		ContextCleanupListener.cleanupAttributes(event.getServletContext());
	}

}
