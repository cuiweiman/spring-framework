/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.remoting.rmi;

import org.springframework.remoting.support.RemoteInvocation;
import org.springframework.remoting.support.RemoteInvocationBasedExporter;
import org.springframework.remoting.support.RemoteInvocationTraceInterceptor;

import java.lang.reflect.InvocationTargetException;
import java.rmi.Remote;

/**
 * Convenient superclass for RMI-based remote exporters. Provides a facility
 * to automatically wrap a given plain Java service object with an
 * RmiInvocationWrapper, exposing the {@link RmiInvocationHandler} remote interface.
 *
 * <p>Using the RMI invoker mechanism, RMI communication operates at the {@link RmiInvocationHandler}
 * level, sharing a common invoker stub for any number of services. Service interfaces are <i>not</i>
 * required to extend {@code java.rmi.Remote} or declare {@code java.rmi.RemoteException}
 * on all service methods. However, in and out parameters still have to be serializable.
 *
 * @author Juergen Hoeller
 * @see RmiServiceExporter
 * @see JndiRmiServiceExporter
 * @since 1.2.5
 */
public abstract class RmiBasedExporter extends RemoteInvocationBasedExporter {

	/**
	 * 如果配置的 service 属性对应的类实现了 Remote 接口，且没有配置 serviceInterface 属性，那么直接使用 service 作为处理类；
	 * 否则使用 {@link RmiInvocationWrapper} 对 service 的代理类和当前类，也就是 {@link RmiServiceExporter} 进行封装。
	 * <p>
	 * 经过这样的封装，客户端与服务端便可以达成一致协议，当客户端检测到的是 RmiInvocationWrapper 类型时，便会直接调用其 invoke 方法，
	 * 使得调用端与服务端很好地连接在一起。而 RmiInvocationWrapper 封装了用于处理请求的代理类，在 invoke 中便会使用代理类进行进一步处理。
	 * <p>
	 * Determine the object to export: either the service object itself
	 * or a RmiInvocationWrapper in case of a non-RMI service object.
	 *
	 * @return the RMI object to export
	 * @see #setService
	 * @see #setServiceInterface
	 * @see #getProxyForService() 使用代理的方式，增加拦截器 {@link RemoteInvocationTraceInterceptor}，以便对方法调用进行打印跟踪。
	 */
	protected Remote getObjectToExport() {
		// determine remote object
		// 如果配置的 service 属性对应的类 实现了 Remote 接口，且没有配置 serviceInterface 属性
		if (getService() instanceof Remote &&
				(getServiceInterface() == null || Remote.class.isAssignableFrom(getServiceInterface()))) {
			// conventional RMI service
			return (Remote) getService();
		} else {
			// RMI invoker
			if (logger.isDebugEnabled()) {
				logger.debug("RMI service [" + getService() + "] is an RMI invoker");
			}
			// 对 service 进行封装
			return new RmiInvocationWrapper(getProxyForService(), this);
		}
	}

	/**
	 * Redefined here to be visible to RmiInvocationWrapper.
	 * Simply delegates to the corresponding superclass method.
	 *
	 * @see RemoteInvocationBasedExporter#invoke(org.springframework.remoting.support.RemoteInvocation, java.lang.Object)
	 */
	@Override
	protected Object invoke(RemoteInvocation invocation, Object targetObject)
			throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

		return super.invoke(invocation, targetObject);
	}

}
