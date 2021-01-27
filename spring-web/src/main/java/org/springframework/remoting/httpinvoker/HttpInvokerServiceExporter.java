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

package org.springframework.remoting.httpinvoker;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.remoting.rmi.RemoteInvocationSerializingExporter;
import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationResult;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.util.NestedServletException;

/**
 * Http Invoker 服务端的实现。
 * (Http Invoker 客户端的实现{@link HttpInvokerProxyFactoryBean})
 * <p>
 * 基于 Http 请求的 Servlet API 处理程序，将指定的 服务实例 导出为 可以通过 Http 协议调用的服务端点。
 *
 * <p>反序列化远程调用对象 并 序列化远程调用结果对象。与 RMI 一样使用 Java 序列化，
 * 但提供了与 Caucho 的基于 HTTP 的 Hessian 协议相同的易设置性。
 *
 * <p> HTTP invoker是 Java 远程调用的推荐协议。它比 Hessian 更强大、更可扩展，但必须是 Java 之间的调用。
 * 不过，它和 Hessian 一样容易建立，这是它与 RMI 相比，主要优势。
 *
 * <p>警告：请注意由于不安全的 Java 反序列化而导致的漏洞：在反序列化步骤中，被操纵的输入流可能会导致在服务器上执行不需要的代码。
 * 因此，不要将 HTTP 调用程序端点暴露给不受信任的客户端，而只在您自己的服务之间公开。
 * 通常，我们强烈建议使用其他消息格式（例如 JSON）。
 *
 * <p>
 * Servlet-API-based HTTP request handler that exports the specified service bean
 * as HTTP invoker service endpoint, accessible via an HTTP invoker proxy.
 *
 * <p>Deserializes remote invocation objects and serializes remote invocation
 * result objects. Uses Java serialization just like RMI, but provides the
 * same ease of setup as Caucho's HTTP-based Hessian protocol.
 *
 * <p><b>HTTP invoker is the recommended protocol for Java-to-Java remoting.</b>
 * It is more powerful and more extensible than Hessian, at the expense of
 * being tied to Java. Nevertheless, it is as easy to set up as Hessian,
 * which is its main advantage compared to RMI.
 *
 * <p><b>WARNING: Be aware of vulnerabilities due to unsafe Java deserialization:
 * Manipulated input streams could lead to unwanted code execution on the server
 * during the deserialization step. As a consequence, do not expose HTTP invoker
 * endpoints to untrusted clients but rather just between your own services.</b>
 * In general, we strongly recommend any other message format (e.g. JSON) instead.
 *
 * @author Juergen Hoeller
 * @see HttpInvokerClientInterceptor
 * @see HttpInvokerProxyFactoryBean
 * @see org.springframework.remoting.rmi.RmiServiceExporter
 * @see org.springframework.remoting.caucho.HessianServiceExporter
 * @see #afterPropertiesSet() 自定义初始化执行任务：创建代理
 * @see #handleRequest(HttpServletRequest, HttpServletResponse) 处理来自客户端的 request
 * @since 1.1
 */
public class HttpInvokerServiceExporter extends RemoteInvocationSerializingExporter implements HttpRequestHandler {

	/**
	 * 处理来自客户端的 request
	 * <p>
	 * Reads a remote invocation from the request, executes it,
	 * and writes the remote invocation result to the response.
	 *
	 * @see #readRemoteInvocation(HttpServletRequest) 读取 序列化的 对象
	 * @see #invokeAndCreateResult(org.springframework.remoting.support.RemoteInvocation, Object) 执行 调用
	 * @see #writeRemoteInvocationResult(HttpServletRequest, HttpServletResponse, RemoteInvocationResult) 将结果 序列化对象 写入输出流
	 */
	@Override
	public void handleRequest(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		try {
			// 从 request 中读取 序列化的 对象
			RemoteInvocation invocation = readRemoteInvocation(request);
			// 执行 调用
			RemoteInvocationResult result = invokeAndCreateResult(invocation, getProxy());
			// 将结果的 序列化对象 写入输出流
			writeRemoteInvocationResult(request, response, result);
		} catch (ClassNotFoundException ex) {
			throw new NestedServletException("Class not found during deserialization", ex);
		}
	}

	/**
	 * Read a RemoteInvocation from the given HTTP request.
	 * <p>Delegates to {@link #readRemoteInvocation(HttpServletRequest, InputStream)} with
	 * the {@link HttpServletRequest#getInputStream() servlet request's input stream}.
	 *
	 * @param request current HTTP request
	 * @return the RemoteInvocation object
	 * @throws IOException            in case of I/O failure
	 * @throws ClassNotFoundException if thrown by deserialization
	 */
	protected RemoteInvocation readRemoteInvocation(HttpServletRequest request)
			throws IOException, ClassNotFoundException {

		return readRemoteInvocation(request, request.getInputStream());
	}

	/**
	 * Deserialize a RemoteInvocation object from the given InputStream.
	 * <p>Gives {@link #decorateInputStream} a chance to decorate the stream
	 * first (for example, for custom encryption or compression). Creates a
	 * {@link org.springframework.remoting.rmi.CodebaseAwareObjectInputStream}
	 * and calls {@link #doReadRemoteInvocation} to actually read the object.
	 * <p>Can be overridden for custom serialization of the invocation.
	 *
	 * @param request current HTTP request
	 * @param is      the InputStream to read from
	 * @return the RemoteInvocation object
	 * @throws IOException            in case of I/O failure
	 * @throws ClassNotFoundException if thrown during deserialization
	 */
	protected RemoteInvocation readRemoteInvocation(HttpServletRequest request, InputStream is)
			throws IOException, ClassNotFoundException {

		// 创建对象 输入流
		ObjectInputStream ois = createObjectInputStream(decorateInputStream(request, is));
		try {
			// 从输入流中 读取 序列化的对象
			return doReadRemoteInvocation(ois);
		} finally {
			ois.close();
		}
	}

	/**
	 * Return the InputStream to use for reading remote invocations,
	 * potentially decorating the given original InputStream.
	 * <p>The default implementation returns the given stream as-is.
	 * Can be overridden, for example, for custom encryption or compression.
	 *
	 * @param request current HTTP request
	 * @param is      the original InputStream
	 * @return the potentially decorated InputStream
	 * @throws IOException in case of I/O failure
	 */
	protected InputStream decorateInputStream(HttpServletRequest request, InputStream is) throws IOException {
		return is;
	}

	/**
	 * Write the given RemoteInvocationResult to the given HTTP response.
	 *
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @param result   the RemoteInvocationResult object
	 * @throws IOException in case of I/O failure
	 */
	protected void writeRemoteInvocationResult(
			HttpServletRequest request, HttpServletResponse response, RemoteInvocationResult result)
			throws IOException {

		response.setContentType(getContentType());
		writeRemoteInvocationResult(request, response, result, response.getOutputStream());
	}

	/**
	 * Serialize the given RemoteInvocation to the given OutputStream.
	 * <p>The default implementation gives {@link #decorateOutputStream} a chance
	 * to decorate the stream first (for example, for custom encryption or compression).
	 * Creates an {@link java.io.ObjectOutputStream} for the final stream and calls
	 * {@link #doWriteRemoteInvocationResult} to actually write the object.
	 * <p>Can be overridden for custom serialization of the invocation.
	 *
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @param result   the RemoteInvocationResult object
	 * @param os       the OutputStream to write to
	 * @throws IOException in case of I/O failure
	 * @see #decorateOutputStream
	 * @see #doWriteRemoteInvocationResult
	 */
	protected void writeRemoteInvocationResult(
			HttpServletRequest request, HttpServletResponse response, RemoteInvocationResult result, OutputStream os)
			throws IOException {


		// 获取输出流
		ObjectOutputStream oos =
				createObjectOutputStream(new FlushGuardedOutputStream(decorateOutputStream(request, response, os)));
		try {
			// 将结果 写入输出流
			doWriteRemoteInvocationResult(result, oos);
		} finally {
			oos.close();
		}
	}

	/**
	 * Return the OutputStream to use for writing remote invocation results,
	 * potentially decorating the given original OutputStream.
	 * <p>The default implementation returns the given stream as-is.
	 * Can be overridden, for example, for custom encryption or compression.
	 *
	 * @param request  current HTTP request
	 * @param response current HTTP response
	 * @param os       the original OutputStream
	 * @return the potentially decorated OutputStream
	 * @throws IOException in case of I/O failure
	 */
	protected OutputStream decorateOutputStream(
			HttpServletRequest request, HttpServletResponse response, OutputStream os) throws IOException {

		return os;
	}


	/**
	 * Decorate an {@code OutputStream} to guard against {@code flush()} calls,
	 * which are turned into no-ops.
	 * <p>Because {@link ObjectOutputStream#close()} will in fact flush/drain
	 * the underlying stream twice, this {@link FilterOutputStream} will
	 * guard against individual flush calls. Multiple flush calls can lead
	 * to performance issues, since writes aren't gathered as they should be.
	 *
	 * @see <a href="https://jira.spring.io/browse/SPR-14040">SPR-14040</a>
	 */
	private static class FlushGuardedOutputStream extends FilterOutputStream {

		public FlushGuardedOutputStream(OutputStream out) {
			super(out);
		}

		@Override
		public void flush() throws IOException {
			// Do nothing on flush
		}
	}

}
