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

package org.springframework.transaction.config;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.config.AopNamespaceUtils;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.interceptor.AbstractFallbackTransactionAttributeSource;
import org.springframework.transaction.interceptor.BeanFactoryTransactionAttributeSourceAdvisor;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.ClassUtils;
import org.w3c.dom.Element;

/**
 * <tx:annotation-driven> 标签解析器
 * <p>
 * 可以参考博客：https://blog.csdn.net/baidu_39322753/article/details/100073169
 * https://www.cnblogs.com/chihirotan/p/6739748.html，阅读更佳。
 * <p>
 * 实现了 {@link BeanDefinitionParser}接口，允许用户轻松地使用 annotation-driven 方式配置事务管理。
 * <p>
 * 默认 代理都是 JDK代理。因此当你注入的是 具体对象，而不是 接口时，将会有问题（JDK动态代理适用于实现接口的类，CGLIB动态代理适用于单独的类）。
 * 可以通过设置属性 proxy-target-class=true，来解决这个问题限制（这将 强制使用 CGLIB 来创建动态代理）。
 * <p>
 * 提取 事务 标签的方法入口：{@link AbstractFallbackTransactionAttributeSource#computeTransactionAttribute(java.lang.reflect.Method, java.lang.Class)}
 * <p>
 * <p>
 * {@link org.springframework.beans.factory.xml.BeanDefinitionParser
 * BeanDefinitionParser} implementation that allows users to easily configure
 * all the infrastructure beans required to enable annotation-driven transaction
 * demarcation.
 *
 * <p>By default, all proxies are created as JDK proxies. This may cause some
 * problems if you are injecting objects as concrete classes rather than
 * interfaces. To overcome this restriction you can set the
 * '{@code proxy-target-class}' attribute to '{@code true}', which
 * will result in class-based proxies being created.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Chris Beams
 * @author Stephane Nicoll
 * @since 2.0
 */
class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {

	/**
	 * Parses the {@code <tx:annotation-driven/>} tag. Will
	 * {@link AopNamespaceUtils#registerAutoProxyCreatorIfNecessary register an AutoProxyCreator}
	 * with the container as necessary.
	 * <p>
	 * <pre class="code">
	 *     <!-- xml 配置事务，mode属性值可以是 proxy（默认）/aspectj -->
	 * 		<tx:annotation-driven transaction-manager="transactionManager" mode="aspectj"/>
	 * </pre>
	 * 当 mode 属性是 proxy（默认值） 时，容器注入的是 {@link InfrastructureAdvisorAutoProxyCreator}；
	 * 当 mode 属性是 aspectj 时，使用 AspectJ 的方式进行事务切入。东西就多了……
	 */
	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		registerTransactionalEventListenerFactory(parserContext);
		String mode = element.getAttribute("mode");
		// 判断 mode 属性
		if ("aspectj".equals(mode)) {
			// mode="aspectj"，AspectJ 的方式进行事务切入
			registerTransactionAspect(element, parserContext);
			if (ClassUtils.isPresent("javax.transaction.Transactional", getClass().getClassLoader())) {
				registerJtaTransactionAspect(element, parserContext);
			}
		} else {
			// mode="proxy"，默认配置模式；注册 InfrastructureAdvisorAutoProxyCreator
			AopAutoProxyConfigurer.configureAutoProxyCreator(element, parserContext);
		}
		return null;
	}

	private void registerTransactionAspect(Element element, ParserContext parserContext) {
		String txAspectBeanName = TransactionManagementConfigUtils.TRANSACTION_ASPECT_BEAN_NAME;
		String txAspectClassName = TransactionManagementConfigUtils.TRANSACTION_ASPECT_CLASS_NAME;
		if (!parserContext.getRegistry().containsBeanDefinition(txAspectBeanName)) {
			RootBeanDefinition def = new RootBeanDefinition();
			def.setBeanClassName(txAspectClassName);
			def.setFactoryMethodName("aspectOf");
			registerTransactionManager(element, def);
			parserContext.registerBeanComponent(new BeanComponentDefinition(def, txAspectBeanName));
		}
	}

	private void registerJtaTransactionAspect(Element element, ParserContext parserContext) {
		String txAspectBeanName = TransactionManagementConfigUtils.JTA_TRANSACTION_ASPECT_BEAN_NAME;
		String txAspectClassName = TransactionManagementConfigUtils.JTA_TRANSACTION_ASPECT_CLASS_NAME;
		if (!parserContext.getRegistry().containsBeanDefinition(txAspectBeanName)) {
			RootBeanDefinition def = new RootBeanDefinition();
			def.setBeanClassName(txAspectClassName);
			def.setFactoryMethodName("aspectOf");
			registerTransactionManager(element, def);
			parserContext.registerBeanComponent(new BeanComponentDefinition(def, txAspectBeanName));
		}
	}

	private static void registerTransactionManager(Element element, BeanDefinition def) {
		def.getPropertyValues().add("transactionManagerBeanName",
				TxNamespaceHandler.getTransactionManagerName(element));
	}

	private void registerTransactionalEventListenerFactory(ParserContext parserContext) {
		RootBeanDefinition def = new RootBeanDefinition();
		def.setBeanClass(TransactionalEventListenerFactory.class);
		parserContext.registerBeanComponent(new BeanComponentDefinition(def,
				TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME));
	}


	/**
	 * 这里的方法，向 parserContext 中注册了代理类以及三个 bean，这3个 bean 支撑了整个的事务功能。
	 * 其中两个 bean（{@link AnnotationTransactionAttributeSource}和 {@link TransactionInterceptor}）
	 * 被注册到了 advisorDef 中，advisorDef 使用 {@link BeanFactoryTransactionAttributeSourceAdvisor}作为其
	 * class属性。即 BeanFactoryTransactionAttributeSourceAdvisor 代表当前 bean。
	 * <p>
	 * 当判断某个 Bean 是否适用于事务增强时，即是判断是否适用于 增强器 {@link BeanFactoryTransactionAttributeSourceAdvisor}，它
	 * 作为 Advisor 的实现类，尊村 Advisor 的处理方式，当代理被调用时会调用这个类的增强方法，即此 bean 的 Advise。又因为在解析事务标签时，
	 * 我们把 TransactionInterceptor 类型的 bean 注入到了  BeanFactoryTransactionAttributeSourceAdvisor 中，所以调用事务增强器
	 * 增强的代理类 时，会首先执行 TransactionInterceptor 进行增强，同时在{@link TransactionInterceptor#invoke(MethodInvocation)}
	 * 方法中完成了整个事务的逻辑。
	 * <p>
	 * Inner class to just introduce an AOP framework dependency when actually in proxy mode.
	 */
	private static class AopAutoProxyConfigurer {

		public static void configureAutoProxyCreator(Element element, ParserContext parserContext) {
			// ★★ 主要注册 {@link InfrastructureAdvisorAutoProxyCreator} 类型的 bean。
			AopNamespaceUtils.registerAutoProxyCreatorIfNecessary(parserContext, element);

			// TRANSACTION_ADVISOR_BEAN_NAME = "org.springframework.transaction.config.internalTransactionAdvisor"
			String txAdvisorBeanName = TransactionManagementConfigUtils.TRANSACTION_ADVISOR_BEAN_NAME;
			if (!parserContext.getRegistry().containsBeanDefinition(txAdvisorBeanName)) {
				Object eleSource = parserContext.extractSource(element);

				// Create the TransactionAttributeSource definition.
				// ★★ 创建 TransactionAttributeSource 的 bean。（AnnotationTransactionAttributeSource 是其子类）
				RootBeanDefinition sourceDef = new RootBeanDefinition(
						"org.springframework.transaction.annotation.AnnotationTransactionAttributeSource");
				sourceDef.setSource(eleSource);
				sourceDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				// 注册 bean，并使用 Spring 中的定义规则 生成 beanName。
				String sourceName = parserContext.getReaderContext().registerWithGeneratedName(sourceDef);

				// Create the TransactionInterceptor definition.
				// ★★ 创建 TransactionInterceptor 的 bean。
				RootBeanDefinition interceptorDef = new RootBeanDefinition(TransactionInterceptor.class);
				interceptorDef.setSource(eleSource);
				interceptorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				registerTransactionManager(element, interceptorDef);
				interceptorDef.getPropertyValues().add("transactionAttributeSource", new RuntimeBeanReference(sourceName));
				// 注册 Bean，并使用 Spring 中的定义规则 生成 beanName
				String interceptorName = parserContext.getReaderContext().registerWithGeneratedName(interceptorDef);

				// Create the TransactionAttributeSourceAdvisor definition.
				// ★★ 创建 TransactionAttributeSourceAdvisor 的 bean
				RootBeanDefinition advisorDef = new RootBeanDefinition(BeanFactoryTransactionAttributeSourceAdvisor.class);
				advisorDef.setSource(eleSource);
				advisorDef.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
				// 将 sourceName 的 bean 注入到 advisorDef 的 transactionAttributeSource 属性中
				advisorDef.getPropertyValues().add("transactionAttributeSource", new RuntimeBeanReference(sourceName));
				// 将 interceptorName 的 bean 注入到 advisorDef 的 adviceBeanName 属性中
				advisorDef.getPropertyValues().add("adviceBeanName", interceptorName);
				if (element.hasAttribute("order")) {
					// 如果配置了 Order 属性，则 注入到 advisorDef 的 order 属性中
					advisorDef.getPropertyValues().add("order", element.getAttribute("order"));
				}
				parserContext.getRegistry().registerBeanDefinition(txAdvisorBeanName, advisorDef);

				// 创建 CompositeComponentDefinition
				CompositeComponentDefinition compositeDef = new CompositeComponentDefinition(element.getTagName(), eleSource);
				compositeDef.addNestedComponent(new BeanComponentDefinition(sourceDef, sourceName));
				compositeDef.addNestedComponent(new BeanComponentDefinition(interceptorDef, interceptorName));
				compositeDef.addNestedComponent(new BeanComponentDefinition(advisorDef, txAdvisorBeanName));
				parserContext.registerComponent(compositeDef);
			}
		}
	}

}
