/*
 * Copyright 2002-2016 the original author or authors.
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

package org.springframework.aop.framework;

import org.aopalliance.intercept.Interceptor;
import org.springframework.aop.TargetSource;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;

/**
 * Factory for AOP proxies for programmatic use, rather than via declarative
 * setup in a bean factory. This class provides a simple way of obtaining
 * and configuring AOP proxy instances in custom user code.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 14.03.2003
 */
@SuppressWarnings("serial")
public class ProxyFactory extends ProxyCreatorSupport {

	/**
	 * Create a new ProxyFactory.
	 */
	public ProxyFactory() {
	}

	/**
	 * Create a new ProxyFactory.
	 * <p>Will proxy all interfaces that the given target implements.
	 *
	 * @param target the target object to be proxied
	 */
	public ProxyFactory(Object target) {
		setTarget(target);
		setInterfaces(ClassUtils.getAllInterfaces(target));
	}

	/**
	 * Create a new ProxyFactory.
	 * <p>No target, only interfaces. Must add interceptors.
	 *
	 * @param proxyInterfaces the interfaces that the proxy should implement
	 */
	public ProxyFactory(Class<?>... proxyInterfaces) {
		setInterfaces(proxyInterfaces);
	}

	/**
	 * Create a new ProxyFactory for the given interface and interceptor.
	 * <p>Convenience method for creating a proxy for a single interceptor,
	 * assuming that the interceptor handles all calls itself rather than
	 * delegating to a target, like in the case of remoting proxies.
	 *
	 * @param proxyInterface the interface that the proxy should implement
	 * @param interceptor    the interceptor that the proxy should invoke
	 */
	public ProxyFactory(Class<?> proxyInterface, Interceptor interceptor) {
		addInterface(proxyInterface);
		addAdvice(interceptor);
	}

	/**
	 * Create a ProxyFactory for the specified {@code TargetSource},
	 * making the proxy implement the specified interface.
	 *
	 * @param proxyInterface the interface that the proxy should implement
	 * @param targetSource   the TargetSource that the proxy should invoke
	 */
	public ProxyFactory(Class<?> proxyInterface, TargetSource targetSource) {
		addInterface(proxyInterface);
		setTargetSource(targetSource);
	}


	/**
	 * Create a new proxy according to the settings in this factory.
	 * <p>Can be called repeatedly. Effect will vary if we've added
	 * or removed interfaces. Can add and remove interceptors.
	 * <p>Uses a default class loader: Usually, the thread context class loader
	 * (if necessary for proxy creation).
	 *
	 * @return the proxy object
	 */
	public Object getProxy() {
		return createAopProxy().getProxy();
	}

	/**
	 * 创建、获取 代理类
	 * <p>
	 * 创建代理类：{@link ProxyCreatorSupport#createAopProxy()}。
	 * 在{@link DefaultAopProxyFactory#createAopProxy(org.springframework.aop.framework.AdvisedSupport)}方法注解中，
	 * 解释了 JDK动态代理 与 CGLIB 动态代理 的区别。
	 * <p>
	 * 获取代理类：
	 * 1. JDK动态代理的获取 {@link JdkDynamicAopProxy#getProxy(java.lang.ClassLoader)}
	 * 2. CGLIB动态代理的获取 {@link CglibAopProxy#getProxy(java.lang.ClassLoader)}
	 *
	 * <p>
	 * JDK代理 AOP功能的简单实现：{@link https://github.com/cuiweiman/wang-wen-jun.git#com.wang.think.aop.jdkproxy}
	 * CGLIB代理 AOP功能的简单实现：{@link https://github.com/cuiweiman/wang-wen-jun.git#com.wang.think.aop.cglibproxy}
	 * <p>
	 * Spring 创建动态代理时，创建的是 JDKProxy 还是 CglibProxy，都在{@link org.springframework.aop.framework.DefaultAopProxyFactory#createAopProxy}的 if-else 语句中进行判断。
	 * 创建 JDK 动态代理：{@link JdkDynamicAopProxy#JdkDynamicAopProxy(org.springframework.aop.framework.AdvisedSupport)}
	 * 创建 CGLIB 动态代理：{@link ObjenesisCglibAopProxy#ObjenesisCglibAopProxy(org.springframework.aop.framework.AdvisedSupport)}
	 * <p>
	 * 1. optimize：用来控制通过 CGLIB 创建的代理是否使用激进的优化策略。除非完全了解 AOP 代理的处理优化，
	 * 否则不推荐用户使用这个配置。目前这个属性仅用于CGLIB代理。
	 * 2. proxyTargetClass：属性为 true 时，目标类本身被代理，而不是目标类的接口，并且创建的代理是 CGLIB。
	 * 设置方式为：<aop:aspectj-autoproxy proxy-target-class="true"/>
	 * 3. hasNoUserSuppliedProxyInterfaces：是否存在 代理接口。
	 * <p>
	 * JDK 与 CGLIB 小结：
	 * 1. 如果目标对象 实现了接口，那么 Spring 默认采用 JDK 动态代理实现 AOP。但是也可以 强制改用 CGLIB 实现 AOP。
	 * 2. 如果目标对象 没有实现接口，那么 必须采用 CGLIB库，Spring 会自动在 JDK动态代理 和 CGLIB 直接转换。
	 * <p>
	 * 如何强制使用 CGLIB 实现 AOP？
	 * 1. 添加 CGLIB 库，Spring_HOME/cglib/*.jar
	 * 2. 在 Spring 配置文件中加入 <aop:aspectj-autoproxy proxy-target-class="true" />
	 * <p>
	 * JDK动态代理 与 CGLIB 动态代理 字节码生成 的区别？
	 * 1. JDK 动态代理 只能对 实现了 接口的类 生成代理，而不能针对类。
	 * 2. CGLIB 是针对 类 实现代理，主要是对 指定的类生成一个子类，覆盖其中的方法，因为是继承关系，因此目标类和方法最好不要声明成 final。
	 * <p>
	 * CGLIB是一个强大的高性能的代码生成包。它广泛的被许多 AOP 框架使用，例如 Spring AOP 和 dyn aop，为他们提供方法的 Interception(拦截).
	 * 最流行的 OR Mapping 工具 Hibernate 也使用了 CGLIB 来代理单端 single-ended(多对一和一对一)的关联（对集合的延迟抓取是采用其它机制实现的）。
	 * EasyMock 和 jMock 是通过使用模范(moke) 对象来测试 Java 代码的包。他们都通过使用 CGLIB 来为那些 没有接口的类创建 模仿(mkde)对象。
	 * <p>
	 * CGLIB包的底层是通过使用一个 小而快 的字节码处理框架 ASM，来转换字节码 并生成新的类。除了 CGLIB 包，脚本语言例如 Groovy和BeanShell 也是使用
	 * ASM 来生成 Java 字节码。当然不鼓励直接使用 ASM，因为它要求使用者 必须对 JVM内部结构（包括 class 文件的格式和指令集）都很熟悉。
	 * <p>
	 * Create a new proxy according to the settings in this factory.
	 * <p>Can be called repeatedly. Effect will vary if we've added
	 * or removed interfaces. Can add and remove interceptors.
	 * <p>Uses the given class loader (if necessary for proxy creation).
	 *
	 * @param classLoader the class loader to create the proxy with
	 *                    (or {@code null} for the low-level proxy facility's default)
	 * @return the proxy object
	 */
	public Object getProxy(@Nullable ClassLoader classLoader) {
		return createAopProxy().getProxy(classLoader);
	}


	/**
	 * Create a new proxy for the given interface and interceptor.
	 * <p>Convenience method for creating a proxy for a single interceptor,
	 * assuming that the interceptor handles all calls itself rather than
	 * delegating to a target, like in the case of remoting proxies.
	 *
	 * @param proxyInterface the interface that the proxy should implement
	 * @param interceptor    the interceptor that the proxy should invoke
	 * @return the proxy object
	 * @see #ProxyFactory(Class, org.aopalliance.intercept.Interceptor)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> proxyInterface, Interceptor interceptor) {
		return (T) new ProxyFactory(proxyInterface, interceptor).getProxy();
	}

	/**
	 * Create a proxy for the specified {@code TargetSource},
	 * implementing the specified interface.
	 *
	 * @param proxyInterface the interface that the proxy should implement
	 * @param targetSource   the TargetSource that the proxy should invoke
	 * @return the proxy object
	 * @see #ProxyFactory(Class, org.springframework.aop.TargetSource)
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getProxy(Class<T> proxyInterface, TargetSource targetSource) {
		return (T) new ProxyFactory(proxyInterface, targetSource).getProxy();
	}

	/**
	 * Create a proxy for the specified {@code TargetSource} that extends
	 * the target class of the {@code TargetSource}.
	 *
	 * @param targetSource the TargetSource that the proxy should invoke
	 * @return the proxy object
	 */
	public static Object getProxy(TargetSource targetSource) {
		if (targetSource.getTargetClass() == null) {
			throw new IllegalArgumentException("Cannot create class proxy for TargetSource with null target class");
		}
		ProxyFactory proxyFactory = new ProxyFactory();
		proxyFactory.setTargetSource(targetSource);
		proxyFactory.setProxyTargetClass(true);
		return proxyFactory.getProxy();
	}

}
