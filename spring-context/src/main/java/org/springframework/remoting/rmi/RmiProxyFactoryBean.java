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

package org.springframework.remoting.rmi;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.remoting.support.UrlBasedRemoteAccessor;
import org.springframework.util.Assert;

/**
 * RMI 远程服务调用 客户端 入口类 。
 * RMI 服务端实现 参见{@link RmiServiceExporter}
 * <p>
 * {@link FactoryBean} for RMI proxies, supporting both conventional RMI services
 * and RMI invokers. Exposes the proxied service for use as a bean reference,
 * using the specified service interface. Proxies will throw Spring's unchecked
 * RemoteAccessException on remote invocation failure instead of RMI's RemoteException.
 *
 * <p>The service URL must be a valid RMI URL like "rmi://localhost:1099/myservice".
 * RMI invokers work at the RmiInvocationHandler level, using the same invoker stub
 * for any service. Service interfaces do not have to extend {@code java.rmi.Remote}
 * or throw {@code java.rmi.RemoteException}. Of course, in and out parameters
 * have to be serializable.
 *
 * <p>With conventional RMI services, this proxy factory is typically used with the
 * RMI service interface. Alternatively, this factory can also proxy a remote RMI
 * service with a matching non-RMI business interface, i.e. an interface that mirrors
 * the RMI service methods but does not declare RemoteExceptions. In the latter case,
 * RemoteExceptions thrown by the RMI stub will automatically get converted to
 * Spring's unchecked RemoteAccessException.
 *
 * <p>The major advantage of RMI, compared to Hessian, is serialization.
 * Effectively, any serializable Java object can be transported without hassle.
 * Hessian has its own (de-)serialization mechanisms, but is HTTP-based and thus
 * much easier to setup than RMI. Alternatively, consider Spring's HTTP invoker
 * to combine Java serialization with HTTP-based transport.
 *
 * @author Juergen Hoeller
 * @see #setServiceInterface
 * @see #setServiceUrl
 * @see RmiClientInterceptor
 * @see RmiServiceExporter
 * @see java.rmi.Remote
 * @see java.rmi.RemoteException
 * @see org.springframework.remoting.RemoteAccessException
 * @see org.springframework.remoting.caucho.HessianProxyFactoryBean
 * @see org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean
 * @see #afterPropertiesSet() 自定义初始化方法。实现了 {@link InitializingBean} 接口
 * @see #getObject() 由于实现了 {@link FactoryBean}接口，在获取 bean 时，并不是直接获取 bean 实例，而是调用 getObject 方法。
 * @see #invoke(MethodInvocation)
 * @since 13.05.2003
 */
public class RmiProxyFactoryBean extends RmiClientInterceptor implements FactoryBean<Object>, BeanClassLoaderAware {

	private Object serviceProxy;


	/**
	 * 自定义初始化过程，创建 代理类，并使用当前类作为增强方法。
	 * <p>
	 * 自初始化时，创建了代理并将本身作为增强器加入了代理中（RmiProxyFactoryBean 间接实现了 MethodInterceptor 接口）
	 * 如此，但客户端调用代理类的接口中的某个方法时，会首先执行 {@link RmiProxyFactoryBean#invoke(MethodInvocation)}方法进行增强
	 *
	 * @see RmiClientInterceptor#afterPropertiesSet()
	 * @see #invoke(MethodInvocation)
	 */
	@Override
	public void afterPropertiesSet() {
		super.afterPropertiesSet();
		Class<?> ifc = getServiceInterface();
		Assert.notNull(ifc, "Property 'serviceInterface' is required");
		// 根据设置的接口 创建代理，并使用当前类 this 作为增强器
		this.serviceProxy = new ProxyFactory(ifc, this).getProxy(getBeanClassLoader());
	}


	/**
	 * 在获取本类的实例时，实际上返回的是一个 代理类。代理类会使用当前 bean 作为增强器进行增强。
	 * 也就是说调用 RmiProxyFactoryBean 的父类 {@link RmiClientInterceptor#invoke(MethodInvocation)}方法。
	 *
	 * @return 代理类
	 */
	@Override
	public Object getObject() {
		return this.serviceProxy;
	}

	@Override
	public Class<?> getObjectType() {
		return getServiceInterface();
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
