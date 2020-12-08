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

package org.springframework.core.io;

import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;

/**
 * 定义资源加载器，主要应用于 根据给定的资源文件地址 返回对应的Resource。
 * <p>
 * Strategy interface for loading resources (e.. class path or file system
 * resources). An {@link org.springframework.context.ApplicationContext}
 * is required to provide this functionality, plus extended
 * {@link org.springframework.core.io.support.ResourcePatternResolver} support.
 *
 * <p>{@link DefaultResourceLoader} is a standalone implementation that is
 * usable outside an ApplicationContext, also used by {@link ResourceEditor}.
 *
 * <p>Bean properties of type Resource and Resource array can be populated
 * from Strings when running in an ApplicationContext, using the particular
 * context's resource loading strategy.
 *
 * @author Juergen Hoeller
 * @see Resource
 * @see org.springframework.core.io.support.ResourcePatternResolver
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ResourceLoaderAware
 * @since 10.03.2004
 */
public interface ResourceLoader {

	/**
	 * 从 class 路径 加载的 伪URL 前缀。
	 * Pseudo URL prefix for loading from the class path: "classpath:".
	 */
	String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;


	/**
	 * 为指定的资源路径 返回一个 资源处理器。
	 * <p>
	 * 这个处理器应该始终是 可重用 的资源描述符，允许多个 {@link Resource#getInputStream()}调用。
	 * <p><ul>
	 * <li>必须支持 全限定的 URL，例如。"文件：C:/测试数据".</li>
	 * <li>必须支持 classpath 路径的 伪URL，例如。"classpath：test.dat".</li>
	 * <li>应该支持 相对资源文件路径，例如。"WEB-INF/test.dat".</li>
	 * </ul>
	 * 这将是 完全实现的，通常由 ApplicationContext 实现提供。
	 * <p>
	 * 注意，Resource handle 并不意味着已有的资源；你需要调用 {@link Resource#exists}
	 * 去检查资源文件或路径是否存在。
	 * <p>
	 * Return a Resource handle for the specified resource location.
	 * <p>The handle should always be a reusable resource descriptor,
	 * allowing for multiple {@link Resource#getInputStream()} calls.
	 * <p><ul>
	 * <li>Must support fully qualified URLs, e.g. "file:C:/test.dat".
	 * <li>Must support classpath pseudo-URLs, e.g. "classpath:test.dat".
	 * <li>Should support relative file paths, e.g. "WEB-INF/test.dat".
	 * (This will be implementation-specific, typically provided by an
	 * ApplicationContext implementation.)
	 * </ul>
	 * <p>Note that a Resource handle does not imply an existing resource;
	 * you need to invoke {@link Resource#exists} to check for existence.
	 *
	 * @param location the resource location
	 * @return a corresponding Resource handle (never {@code null})
	 * @see #CLASSPATH_URL_PREFIX
	 * @see Resource#exists()
	 * @see Resource#getInputStream()
	 */
	Resource getResource(String location);

	/**
	 * 暴露这个 ResourceLoader 使用的  ClassLoader。
	 * <p>
	 * 需要直接访问 ClassLoader 的客户端，可以统一使用 ResourceLoader 的方式去访问，
	 * 而不是依赖于 线程上下文的 ClassLoader。
	 * <p>
	 * Expose the ClassLoader used by this ResourceLoader.
	 * <p>Clients which need to access the ClassLoader directly can do so
	 * in a uniform manner with the ResourceLoader, rather than relying
	 * on the thread context ClassLoader.
	 *
	 * @return the ClassLoader
	 * (only {@code null} if even the system ClassLoader isn't accessible)
	 * @see org.springframework.util.ClassUtils#getDefaultClassLoader()
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getClassLoader();

}
