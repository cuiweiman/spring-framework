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

package org.springframework.aop.config;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;
import org.w3c.dom.Element;

/**
 * Utility class for handling registration of auto-proxy creators used internally
 * by the '{@code aop}' namespace tags.
 *
 * <p>Only a single auto-proxy creator should be registered and multiple configuration
 * elements may wish to register different concrete implementations. As such this class
 * delegates to {@link AopConfigUtils} which provides a simple escalation protocol.
 * Callers may request a particular auto-proxy creator and know that creator,
 * <i>or a more capable variant thereof</i>, will be registered as a post-processor.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @see AopConfigUtils
 * @since 2.0
 */
public abstract class AopNamespaceUtils {

	/**
	 * The {@code proxy-target-class} attribute as found on AOP-related XML tags.
	 */
	public static final String PROXY_TARGET_CLASS_ATTRIBUTE = "proxy-target-class";

	/**
	 * The {@code expose-proxy} attribute as found on AOP-related XML tags.
	 */
	private static final String EXPOSE_PROXY_ATTRIBUTE = "expose-proxy";


	public static void registerAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	public static void registerAspectJAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {

		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 * 注册 AnnotationAwareAspectJAutoProxyCreator
	 * <p>
	 * 主要完成了三个过程：
	 * 1.注册或升级 AnnotationAwareAspectJAutoProxyCreator：对于AOP 的实现，基本都是靠 AnnotationAwareAspectJAutoProxyCreator去完成。
	 * 它可以根据 @Point 注解，定义切点来自动代理匹配的 bean。但是为了配置简单，Spring 使用自定义配置来帮助我们自动注册 AnnotationAwareAspectJAutoProxyCreator，
	 * 其注册过程就是在 这里实现的{@link AopConfigUtils#registerAspectJAnnotationAutoProxyCreatorIfNecessary(BeanDefinitionRegistry, Object)}
	 * 2. 处理 proxy-target-class 以及 expose-proxy 属性。{@link #useClassProxyingIfNecessary(BeanDefinitionRegistry, Element)}。
	 * proxy-target-class：Spring AOP 部分使用 JDK 动态代理后者 CGLib 来为目标对象创建 代理（建议尽量使用JDK的动态代理）。
	 * 如果被代理的目标对象实现了至少一个接口，则会使用 JDK 动态代理；
	 * 若该目标类型实现的接口都会被代理。若该目标对象没有实现任何接口，则创建一个 CGLib 代理；
	 * 如果想强制使用CGLib代理（例如希望代理目标对象的所有方法，而不只是实现自接口的方法），那也可以。但是需要考虑以下两个问题：
	 * a. 无法通知 （advise）Final方法，因为他们不能被覆写。
	 * b. 需要将CGLib 的二进制发行包 放在classPath下面。
	 * 与之相比，JDK本身就提供了动态代理，强制使用 CGLib 代理需要将 <aop:config>的proxy-target-class属性设置为 true：<aop:config proxy-target-class="true>...</aop:config>
	 * 若需要使用CGLib 代理和 @AspectJ 自动代理支持，配置如下：<aop:aspectj-autoproxy proxy-target-class="true"/>.
	 * 2.1 JDK 动态代理：其代理对象 必须是某个接口的实现，他是通过在运行期间创建一个接口的实现类来完成对目标对象的代理。
	 * 2.2 CGLib 代理：实现原理类似 JDK，但是在运行期间生成的代理对象是针对目标类扩展的子类。CGLib 是高效的代码生成包，底层是依靠ASM操作字节码实现的，
	 * 性能比 JDK 强。
	 * <p>
	 * 3. 注册组件并通知，便于监听器做进一步处理。
	 *
	 * @param parserContext 解析上下文
	 * @param sourceElement 元素
	 */
	public static void registerAspectJAnnotationAutoProxyCreatorIfNecessary(
			ParserContext parserContext, Element sourceElement) {
		// 注册或升级 AutoProxyCreator 定义 beanName 为 org.Springframework.aop.config.internalAutoproxyCreator 的 BeanDefinition
		BeanDefinition beanDefinition = AopConfigUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(
				parserContext.getRegistry(), parserContext.extractSource(sourceElement));
		// 对于 proxy-target-class 以及 expose-proxy 属性的处理
		useClassProxyingIfNecessary(parserContext.getRegistry(), sourceElement);
		// 注册组件并通知，便于监听器做进一步处理。其中 beanDefinition 的 className 为 AnnotationAwareAspectJAutoProxyCreator。
		registerComponentIfNecessary(beanDefinition, parserContext);
	}

	/**
	 * 处理 proxy-target-class 以及 expose-proxy 属性。
	 *
	 * @param registry      注册器
	 * @param sourceElement 元素
	 */
	private static void useClassProxyingIfNecessary(BeanDefinitionRegistry registry, @Nullable Element sourceElement) {
		if (sourceElement != null) {
			// 处理 proxy-target-class 属性
			boolean proxyTargetClass = Boolean.parseBoolean(sourceElement.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE));
			if (proxyTargetClass) {
				//强制使用的过程，其实也是一个 属性设置的过程
				AopConfigUtils.forceAutoProxyCreatorToUseClassProxying(registry);
			}
			// 处理 expose-proxy 属性
			boolean exposeProxy = Boolean.parseBoolean(sourceElement.getAttribute(EXPOSE_PROXY_ATTRIBUTE));
			if (exposeProxy) {
				AopConfigUtils.forceAutoProxyCreatorToExposeProxy(registry);
			}
		}
	}

	private static void registerComponentIfNecessary(@Nullable BeanDefinition beanDefinition, ParserContext parserContext) {
		if (beanDefinition != null) {
			parserContext.registerComponent(
					new BeanComponentDefinition(beanDefinition, AopConfigUtils.AUTO_PROXY_CREATOR_BEAN_NAME));
		}
	}

}
