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

package org.springframework.beans.factory;

/**
 * 如果 bean 想知道它自己在 Spring 容器中的 bean name 的话，那么可以实现此接口，
 * 通过实现 {@link #setBeanName(String)} 方法获取到。
 * <p>
 * 实现此接口方法的 bean 对象，在这个方法中，可以从 spring 容器中 获取该 bean 在 spring 容器中的名字。
 * 不建议在开发环境中实现本类，意义不大
 * <p>
 * 注意，通常不建议对象依赖于其 bean name，因为这表示对外部配置的潜在脆弱依赖，以及对 Spring API 可能有不必要的依赖。
 * <p>
 * Interface to be implemented by beans that want to be aware of their
 * bean name in a bean factory. Note that it is not usually recommended
 * that an object depends on its bean name, as this represents a potentially
 * brittle dependence on external configuration, as well as a possibly
 * unnecessary dependence on a Spring API.
 *
 * <p>For a list of all bean lifecycle methods, see the
 * {@link BeanFactory BeanFactory javadocs}.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see BeanClassLoaderAware
 * @see BeanFactoryAware
 * @see InitializingBean
 * @since 01.11.2003
 */
public interface BeanNameAware extends Aware {

	/**
	 * 在创建这个 bean 的 bean factory 中设置 bean 的名称。
	 * <p>
	 * Set the name of the bean in the bean factory that created this bean.
	 * <p>Invoked after population of normal bean properties but before an
	 * init callback such as {@link InitializingBean#afterPropertiesSet()}
	 * or a custom init-method.
	 *
	 * @param name the name of the bean in the factory.
	 *             Note that this name is the actual bean name used in the factory, which may
	 *             differ from the originally specified name: in particular for inner bean
	 *             names, the actual bean name might have been made unique through appending
	 *             "#..." suffixes. Use the {@link BeanFactoryUtils#originalBeanName(String)}
	 *             method to extract the original bean name (without suffix), if desired.
	 */
	void setBeanName(String name);

}
