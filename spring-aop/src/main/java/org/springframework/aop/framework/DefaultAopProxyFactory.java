/*
 * Copyright 2002-2015 the original author or authors.
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

import java.io.Serializable;
import java.lang.reflect.Proxy;

import org.springframework.aop.SpringProxy;

/**
 * Default {@link AopProxyFactory} implementation, creating either a CGLIB proxy
 * or a JDK dynamic proxy.
 *
 * <p>Creates a CGLIB proxy if one the following is true for a given
 * {@link AdvisedSupport} instance:
 * <ul>
 * <li>the {@code optimize} flag is set
 * <li>the {@code proxyTargetClass} flag is set
 * <li>no proxy interfaces have been specified
 * </ul>
 *
 * <p>In general, specify {@code proxyTargetClass} to enforce a CGLIB proxy,
 * or specify one or more interfaces to use a JDK dynamic proxy.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see AdvisedSupport#setOptimize
 * @see AdvisedSupport#setProxyTargetClass
 * @see AdvisedSupport#setInterfaces
 * @since 12.03.2004
 */
@SuppressWarnings("serial")
public class DefaultAopProxyFactory implements AopProxyFactory, Serializable {

	/**
	 * 创建代理
	 * JDK代理 AOP功能的简单实现：{@link https://github.com/cuiweiman/wang-wen-jun.git#com.wang.think.aop.jdkproxy}
	 * <p>
	 * 在这里将会完成 代理的创建。Spring 创建代理时，创建的是 JDKProxy 还是 CglibProxy，都在本函数的 if-else 语句中进行判断。
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
	 *
	 * @param config the AOP configuration in the form of an
	 *               AdvisedSupport object
	 * @return AOP 代理
	 * @throws AopConfigException 异常
	 */
	@Override
	public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
		if (config.isOptimize() || config.isProxyTargetClass() || hasNoUserSuppliedProxyInterfaces(config)) {
			Class<?> targetClass = config.getTargetClass();
			if (targetClass == null) {
				throw new AopConfigException("TargetSource cannot determine target class: " +
						"Either an interface or a target is required for proxy creation.");
			}
			if (targetClass.isInterface() || Proxy.isProxyClass(targetClass)) {
				return new JdkDynamicAopProxy(config);
			}
			return new ObjenesisCglibAopProxy(config);
		} else {
			return new JdkDynamicAopProxy(config);
		}
	}

	/**
	 * Determine whether the supplied {@link AdvisedSupport} has only the
	 * {@link org.springframework.aop.SpringProxy} interface specified
	 * (or no proxy interfaces specified at all).
	 */
	private boolean hasNoUserSuppliedProxyInterfaces(AdvisedSupport config) {
		Class<?>[] ifcs = config.getProxiedInterfaces();
		return (ifcs.length == 0 || (ifcs.length == 1 && SpringProxy.class.isAssignableFrom(ifcs[0])));
	}

}
