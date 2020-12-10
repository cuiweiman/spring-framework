/*
 * Copyright 2002-2017 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;

/**
 * Java中，将不同来源的资源抽象成 URL，通过注册不同的 handler （URLStreamHandler）处理不同来源的资源
 * 的读取逻辑。一般 handler 的类型使用不同前缀（协议，protocol）来识别，如 "file:","http:","jar:"等。
 * 然而 URL 没有默认定义 相对 Classpath 或  ServletContext 等资源的 handler。虽然可以注册自己的 URLStreamHandler
 * 来解析特定的 URL前缀，如"classpath:"，但是这需要了解 URL的实现机制，而URL也没有提供基本的方法，如检查当前
 * 资源是否存在、当前资源是否可读等方法。因而 Spring 对其内部使用到的资源实现了自己的抽象接口：
 * {@link Resource}, {@link InputStreamSource}。
 * <p>
 * InputStreamSource 封装任何能返回 InputStream 的类，比如File、Classpath下的资源和ByteArray等。它只有一个方法定义：
 * {@code InputStreamSource#getInputStream}
 * <p>
 * Simple interface for objects that are sources for an {@link InputStream}.
 *
 * <p>This is the base interface for Spring's more extensive {@link Resource} interface.
 *
 * <p>For single-use streams, {@link InputStreamResource} can be used for any
 * given {@code InputStream}. Spring's {@link ByteArrayResource} or any
 * file-based {@code Resource} implementation can be used as a concrete
 * instance, allowing one to read the underlying content stream multiple times.
 * This makes this interface useful as an abstract content source for mail
 * attachments, for example.
 *
 * @author Juergen Hoeller
 * @see java.io.InputStream
 * @see Resource
 * @see InputStreamResource
 * @see ByteArrayResource
 * @since 20.01.2004
 */
public interface InputStreamSource {

	/**
	 * Return an {@link InputStream} for the content of an underlying resource.
	 * <p>It is expected that each call creates a <i>fresh</i> stream.
	 * <p>This requirement is particularly important when you consider an API such
	 * as JavaMail, which needs to be able to read the stream multiple times when
	 * creating mail attachments. For such a use case, it is <i>required</i>
	 * that each {@code getInputStream()} call returns a fresh stream.
	 *
	 * @return the input stream for the underlying resource (must not be {@code null})
	 * @throws java.io.FileNotFoundException if the underlying resource doesn't exist
	 * @throws IOException                   if the content stream could not be opened
	 */
	InputStream getInputStream() throws IOException;

}
