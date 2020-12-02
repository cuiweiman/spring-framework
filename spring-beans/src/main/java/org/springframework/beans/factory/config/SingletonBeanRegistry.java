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

package org.springframework.beans.factory.config;

import org.springframework.lang.Nullable;

/**
 * 定义对单例的注册和获取
 * <p>
 * Interface that defines a registry for shared bean instances.
 * Can be implemented by {@link org.springframework.beans.factory.BeanFactory}
 * implementations in order to expose their singleton management facility
 * in a uniform manner.
 *
 * <p>The {@link ConfigurableBeanFactory} interface extends this interface.
 *
 * @author Juergen Hoeller
 * @see ConfigurableBeanFactory
 * @see org.springframework.beans.factory.support.DefaultSingletonBeanRegistry
 * @see org.springframework.beans.factory.support.AbstractBeanFactory
 * @since 2.0
 */
public interface SingletonBeanRegistry {

	/**
	 * 使用给定的名称，将对象注册为单例对象
	 * <p>
	 * 以指定的名字将给定Object注册到BeanFactory中。
	 * 给定的Object必须是被完全初始化了的，此注册接口不会提供任何用以初始化的回调函数，
	 * 需要特意提及的一点是InitializingBean的afterPropertiesSet方法也不会被此注册接口调用，
	 * 并且，指定实例也不会收到destroy的信息.
	 * 如果此接口的实现类是一个BeanFactory，最好将你的类注册成Bean Definition而不是直接使用对象注册，
	 * 前一种注册方式的好处在于，它可以使你定义的Bean收到initialization和destruction回调。
	 * <p>此方法通常在配置和注册Bean期间调用，但是也不排斥在运行时动态的向其中注册单例对象。
	 * 因此实现类应该同步代码对单例Bean的访问方法
	 * <p>
	 * Register the given existing object as singleton in the bean registry,
	 * under the given bean name.
	 * <p>The given instance is supposed to be fully initialized; the registry
	 * will not perform any initialization callbacks (in particular, it won't
	 * call InitializingBean's {@code afterPropertiesSet} method).
	 * The given instance will not receive any destruction callbacks
	 * (like DisposableBean's {@code destroy} method) either.
	 * <p>When running within a full BeanFactory: <b>Register a bean definition
	 * instead of an existing instance if your bean is supposed to receive
	 * initialization and/or destruction callbacks.</b>
	 * <p>Typically invoked during registry configuration, but can also be used
	 * for runtime registration of singletons. As a consequence, a registry
	 * implementation should synchronize singleton access; it will have to do
	 * this anyway if it supports a BeanFactory's lazy initialization of singletons.
	 *
	 * @param beanName        the name of the bean
	 * @param singletonObject the existing singleton object
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.DisposableBean#destroy
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#registerBeanDefinition
	 */
	void registerSingleton(String beanName, Object singletonObject);

	/**
	 * 根据给定名称，获取注册过的单例对象实例
	 * <p>
	 * 以Object的形式返回指定名字的Bean。
	 * 仅仅返回已经初始化完成的Bean，对于还没有初始化的Bean Definition不予以考虑.
	 * 此方法的主要目的是提供一种手动获取已注册单例Bean的方式，
	 * 同样的对于通过Bean Definition注册的Bean也可以通过此方式获得.
	 * 但是要注意，此方法并不支持使用别名对Bean进行查找，
	 * 如果只有别名的话，要先通过BeanFactory的接口获取到Bean对应的全限定名称
	 * <p>
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Only checks already instantiated singletons; does not return an Object
	 * for singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to access manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to access a singleton
	 * defined by a bean definition that already been created, in a raw fashion.
	 * <p><b>NOTE:</b> This lookup method is not aware of FactoryBean prefixes or aliases.
	 * You need to resolve the canonical bean name first before obtaining the singleton instance.
	 *
	 * @param beanName the name of the bean to look for
	 * @return the registered singleton object, or {@code null} if none found
	 * @see ConfigurableListableBeanFactory#getBeanDefinition
	 */
	@Nullable
	Object getSingleton(String beanName);

	/**
	 * 判断注册容器中，是否包含给定名称的单例对象
	 * <p>
	 * 检查此实例是否包含指定的名字并且已经初始化完成的单例Bean。
	 * 此方法的主要目标是提供一种手动检测Bean是否初始化完成的手段。
	 * 也可用于检测通过BeanDefinition定义的Bean是否创建完成。
	 * 如果要检测BeanFactory中是否包含指定name的BeanDefinition（不管是否初始化完毕），可使用BeanFactory
	 * 的containsBeanDefinition。同时判断containsBeanDefinition和本方法，可以判断BeanFactory是否包含已经初始化完毕的Bean
	 * 而BeanFactory的containsBean方法是用于通用检测一个BeanFactory或者其父上下文是否有
	 * 一个指定名字的Bean的手段。不支持别名查找
	 * <p>
	 * Check if this registry contains a singleton instance with the given name.
	 * <p>Only checks already instantiated singletons; does not return {@code true}
	 * for singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to check whether a
	 * singleton defined by a bean definition has already been created.
	 * <p>To check whether a bean factory contains a bean definition with a given name,
	 * use ListableBeanFactory's {@code containsBeanDefinition}. Calling both
	 * {@code containsBeanDefinition} and {@code containsSingleton} answers
	 * whether a specific bean factory contains a local bean instance with the given name.
	 * <p>Use BeanFactory's {@code containsBean} for general checks whether the
	 * factory knows about a bean with a given name (whether manually registered singleton
	 * instance or created by bean definition), also checking ancestor factories.
	 * <p><b>NOTE:</b> This lookup method is not aware of FactoryBean prefixes or aliases.
	 * You need to resolve the canonical bean name first before checking the singleton status.
	 *
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a singleton instance with the given name
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 * @see org.springframework.beans.factory.BeanFactory#containsBean
	 */
	boolean containsSingleton(String beanName);

	/**
	 * 获取注册容器中所有单例对象的名称
	 * <p>
	 * Return the names of singleton beans registered in this registry.
	 * <p>Only checks already instantiated singletons; does not return names
	 * for singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to check which singletons
	 * defined by a bean definition have already been created.
	 *
	 * @return the list of names as a String array (never {@code null})
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionNames
	 * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionNames
	 */
	String[] getSingletonNames();

	/**
	 * 获取单例注册池中，所有单例对象的总数
	 * <p>
	 * Return the number of singleton beans registered in this registry.
	 * <p>Only checks already instantiated singletons; does not count
	 * singleton bean definitions which have not been instantiated yet.
	 * <p>The main purpose of this method is to check manually registered singletons
	 * (see {@link #registerSingleton}). Can also be used to count the number of
	 * singletons defined by a bean definition that have already been created.
	 *
	 * @return the number of singleton beans
	 * @see #registerSingleton
	 * @see org.springframework.beans.factory.support.BeanDefinitionRegistry#getBeanDefinitionCount
	 * @see org.springframework.beans.factory.ListableBeanFactory#getBeanDefinitionCount
	 */
	int getSingletonCount();

	/**
	 * Return the singleton mutex used by this registry (for external collaborators).
	 *
	 * @return the mutex object (never {@code null})
	 * @since 4.2
	 */
	Object getSingletonMutex();

}
