/*
 * Copyright 2002-2019 the original author or authors.
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

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

import java.beans.PropertyEditor;
import java.security.AccessControlContext;

/**
 * 提供 配置 BeanFactory 的各种方法（除了 BeanFactory 接口以外的其他方法）
 * 继承的两个接口，表明同时继承了 分层和单例类注册的功能。
 * <p>
 * 接口中定义了许多API，如类加载器、类型转换器、属性编辑器、BeanPostProcessor、作用域
 * Bean自定义、处理Bean依赖关系、合并其他 ConfigurableBeanFactory、Bean如何销毁。
 * <p>
 * 涉及到的 Bean生命周期有：Bean的销毁{@see destroyBean}{@see destroyScopedBean}{@see destroySingletons}
 * 添加 Bean 初始化方法相关：{@see addBeanPostProcessor}{@see getBeanPostProcessorCount}
 * <p>
 * Configuration interface to be implemented by most bean factories. Provides
 * facilities to configure a bean factory, in addition to the bean factory
 * client methods in the {@link org.springframework.beans.factory.BeanFactory}
 * interface.
 *
 * <p>This bean factory interface is not meant to be used in normal application
 * code: Stick to {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * needs. This extended interface is just meant to allow for framework-internal
 * plug'n'play and for special access to bean factory configuration methods.
 *
 * @author Juergen Hoeller
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.ListableBeanFactory
 * @see ConfigurableListableBeanFactory
 * @since 03.11.2003
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

	/**
	 * 定义了两个作用域: 单例 和 原型.可以通过{@see registerScope}来添加.
	 * 单例类 静态变量
	 * Scope identifier for the standard singleton scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 *
	 * @see #registerScope
	 */
	String SCOPE_SINGLETON = "singleton";

	/**
	 * 原型类 静态变量
	 * Scope identifier for the standard prototype scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 *
	 * @see #registerScope
	 */
	String SCOPE_PROTOTYPE = "prototype";


	/**
	 * 设置父工厂（{@see HierarchicalBeanFactory#getParentBeanFactory}接口中只有获取父工厂的方法）
	 * 设置后不允许修改。
	 * <p>
	 * Set the parent of this bean factory.
	 * <p>Note that the parent cannot be changed: It should only be set outside
	 * a constructor if it isn't available at the time of factory instantiation.
	 *
	 * @param parentBeanFactory the parent BeanFactory
	 * @throws IllegalStateException if this factory is already associated with
	 *                               a parent BeanFactory
	 * @see #getParentBeanFactory()
	 */
	void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

	/**
	 * 类加载器设置与获取.
	 * 默认使用当前线程中的类加载器
	 * <p>
	 * Set the class loader to use for loading bean classes.
	 * Default is the thread context class loader.
	 * <p>Note that this class loader will only apply to bean definitions
	 * that do not carry a resolved bean class yet. This is the case as of
	 * Spring 2.0 by default: Bean definitions only carry bean class names,
	 * to be resolved once the factory processes the bean definition.
	 *
	 * @param beanClassLoader the class loader to use,
	 *                        or {@code null} to suggest the default class loader
	 */
	void setBeanClassLoader(@Nullable ClassLoader beanClassLoader);

	/**
	 * 获取类加载器
	 * <p>
	 * Return this factory's class loader for loading bean classes
	 * (only {@code null} if even the system ClassLoader isn't accessible).
	 *
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getBeanClassLoader();

	/**
	 * 设置临时类加载器.
	 * 为了类型匹配,搞个临时类加载器.好在一般情况为null,使用上面定义的标准加载器
	 * <p>
	 * Specify a temporary ClassLoader to use for type matching purposes.
	 * Default is none, simply using the standard bean ClassLoader.
	 * <p>A temporary ClassLoader is usually just specified if
	 * <i>load-time weaving</i> is involved, to make sure that actual bean
	 * classes are loaded as lazily as possible. The temporary loader is
	 * then removed once the BeanFactory completes its bootstrap phase.
	 *
	 * @since 2.5
	 */
	void setTempClassLoader(@Nullable ClassLoader tempClassLoader);

	/**
	 * 获取临时类加载器
	 * <p>
	 * Return the temporary ClassLoader to use for type matching purposes,
	 * if any.
	 *
	 * @since 2.5
	 */
	@Nullable
	ClassLoader getTempClassLoader();

	/**
	 * 设置 是否缓存 元数据（bean definition 对象 是否开启热加载）
	 * <p>
	 * 是否需要缓存bean metadata,比如bean definition 和 解析好的classes.默认开启缓存
	 * <p>
	 * Set whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes. Default is on.
	 * <p>Turn this flag off to enable hot-refreshing of bean definition objects
	 * and in particular bean classes. If this flag is off, any creation of a bean
	 * instance will re-query the bean class loader for newly resolved classes.
	 */
	void setCacheBeanMetadata(boolean cacheBeanMetadata);

	/**
	 * 查看 是否缓存了 元数据
	 * <p>
	 * Return whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes.
	 */
	boolean isCacheBeanMetadata();

	/**
	 * 定义 用于解析 bean definition 的表达式解析器
	 * <p>
	 * Specify the resolution strategy for expressions in bean definition values.
	 * <p>There is no expression support active in a BeanFactory by default.
	 * An ApplicationContext will typically set a standard expression strategy
	 * here, supporting "#{...}" expressions in a Unified EL compatible style.
	 *
	 * @since 3.0
	 */
	void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver);

	/**
	 * 获取 用于解析 bean definition 的表达式解析器
	 * <p>
	 * Return the resolution strategy for expressions in bean definition values.
	 *
	 * @since 3.0
	 */
	@Nullable
	BeanExpressionResolver getBeanExpressionResolver();

	/**
	 * 类型转化器
	 * 设置 转换服务，作为 JavaBeans属性编辑器的替代品， 用于转换属性值。
	 * <p>
	 * Specify a Spring 3.0 ConversionService to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 *
	 * @since 3.0
	 */
	void setConversionService(@Nullable ConversionService conversionService);

	/**
	 * 获取 类型转化器
	 * <p>
	 * Return the associated ConversionService, if any.
	 *
	 * @since 3.0
	 */
	@Nullable
	ConversionService getConversionService();

	/**
	 * 属性编辑器——设置属性编辑登记员。
	 * 给所有 Bean 创建过程，设置 一个 PropertyEditorRegistrar（属性编辑器登记员）
	 * <p>
	 * PropertyEditorRegistrar 可以创建 属性编辑器实例，然后注册到 给定的 属性编辑器注册类 中，
	 * 每次创建 Bean实例 时，都是新的。避免了自定义编辑器的的同步需求。
	 * 因此最好使用这个方法，代替registerCustomEditor()方法。
	 * <p>
	 * Add a PropertyEditorRegistrar to be applied to all bean creation processes.
	 * <p>Such a registrar creates new PropertyEditor instances and registers them
	 * on the given registry, fresh for each bean creation attempt. This avoids
	 * the need for synchronization on custom editors; hence, it is generally
	 * preferable to use this method instead of {@link #registerCustomEditor}.
	 *
	 * @param registrar the PropertyEditorRegistrar to register
	 */
	void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

	/**
	 * 注册常用属性编辑器。
	 * 给所有指定的 类型，注册自定义属性编辑器。在工厂配置期间被调用。
	 * <p>
	 * 这个方法会注册一个共享的自定义编辑器实例，为了线程安全，对自定义属性编辑器实例
	 * 的访问会被同步
	 * <p>
	 * Register the given custom property editor for all properties of the
	 * given type. To be invoked during factory configuration.
	 * <p>Note that this method will register a shared custom editor instance;
	 * access to that instance will be synchronized for thread-safety. It is
	 * generally preferable to use {@link #addPropertyEditorRegistrar} instead
	 * of this method, to avoid for the need for synchronization on custom editors.
	 *
	 * @param requiredType        type of the property
	 * @param propertyEditorClass the {@link PropertyEditor} class to register
	 */
	void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

	/**
	 * 用工厂中注册的通用的编辑器初始化指定的属性编辑注册器。
	 * 使用已经注册到 BeanFactory的自定义属性编辑器，初始化 给定的 属性编辑注册器
	 * <p>
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 *
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

	/**
	 * 设置 类型转换器
	 * <p>
	 * Set a custom type converter that this BeanFactory should use for converting
	 * bean property values, constructor argument values, etc.
	 * <p>This will override the default PropertyEditor mechanism and hence make
	 * any custom editors or custom editor registrars irrelevant.
	 *
	 * @see #addPropertyEditorRegistrar
	 * @see #registerCustomEditor
	 * @since 2.5
	 */
	void setTypeConverter(TypeConverter typeConverter);

	/**
	 * 获取 类型转换器
	 * <p>
	 * BeanFactory用来转换bean属性值或者参数值的自定义转换器
	 * <p>
	 * Obtain a type converter as used by this BeanFactory. This may be a fresh
	 * instance for each call, since TypeConverters are usually <i>not</i> thread-safe.
	 * <p>If the default PropertyEditor mechanism is active, the returned
	 * TypeConverter will be aware of all custom editors that have been registered.
	 *
	 * @since 2.5
	 */
	TypeConverter getTypeConverter();

	/**
	 * 添加 嵌入式字符串处理器
	 * <p>
	 * Add a String resolver for embedded values such as annotation attributes.
	 *
	 * @param valueResolver the String resolver to apply to embedded values
	 * @since 3.0
	 */
	void addEmbeddedValueResolver(StringValueResolver valueResolver);

	/**
	 * 判断是否 有 嵌入式字符串处理器
	 * <p>
	 * Determine whether an embedded value resolver has been registered with this
	 * bean factory, to be applied through {@link #resolveEmbeddedValue(String)}.
	 *
	 * @since 4.3
	 */
	boolean hasEmbeddedValueResolver();

	/**
	 * 处理 嵌入式Value，例如一个 注释属性。
	 * 分析指定的嵌入式的String值。
	 * <p>
	 * Resolve the given embedded value, e.g. an annotation attribute.
	 *
	 * @param value the value to resolve
	 * @return the resolved value (may be the original value as-is)
	 * @since 3.0
	 */
	@Nullable
	String resolveEmbeddedValue(String value);

	/**
	 * 增强 bean 初始化功能。—— 用于 Bean实例的 初始化。
	 * 添加 Bean 后置处理器
	 * <p>
	 * Add a new BeanPostProcessor that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * <p>Note: Post-processors submitted here will be applied in the order of
	 * registration; any ordering semantics expressed through implementing the
	 * {@link org.springframework.core.Ordered} interface will be ignored. Note
	 * that autodetected post-processors (e.g. as beans in an ApplicationContext)
	 * will always be applied after programmatically registered ones.
	 *
	 * @param beanPostProcessor the post-processor to register
	 */
	void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

	/**
	 * 获取 Bean后置处理器 的个数
	 * <p>
	 * Return the current number of registered BeanPostProcessors, if any.
	 */
	int getBeanPostProcessorCount();

	/**
	 * 注册 Bean 范围
	 * Register the given scope, backed by the given Scope implementation.
	 *
	 * @param scopeName the scope identifier
	 * @param scope     the backing Scope implementation
	 */
	void registerScope(String scopeName, Scope scope);

	/**
	 * 返回注册的范围名
	 * <p>
	 * Return the names of all currently registered scopes.
	 * <p>This will only return the names of explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 *
	 * @return the array of scope names, or an empty array if none
	 * @see #registerScope
	 */
	String[] getRegisteredScopeNames();

	/**
	 * 返回指定的范围
	 * <p>
	 * Return the Scope implementation for the given scope name, if any.
	 * <p>This will only return explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 *
	 * @param scopeName the name of the scope
	 * @return the registered Scope implementation, or {@code null} if none
	 * @see #registerScope
	 */
	@Nullable
	Scope getRegisteredScope(String scopeName);

	/**
	 * 访问权限控制
	 * 返回本工厂的一个安全访问上下文
	 * <p>
	 * Provides a security access control context relevant to this factory.
	 *
	 * @return the applicable AccessControlContext (never {@code null})
	 * @since 3.0
	 */
	AccessControlContext getAccessControlContext();

	/**
	 * 从其他的工厂复制相关的所有配置
	 * <p>
	 * 复制 给定 ConfigurableBeanFactory类 的所有配置方法 到 本配置中。
	 * <p>
	 * Copy all relevant configuration from the given other factory.
	 * <p>Should include all standard configuration settings as well as
	 * BeanPostProcessors, Scopes, and factory-specific internal settings.
	 * Should not include any metadata of actual bean definitions,
	 * such as BeanDefinition objects and bean name aliases.
	 *
	 * @param otherFactory the other BeanFactory to copy from
	 */
	void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

	/**
	 * 注册 Bean 的别名
	 * <p>
	 * Given a bean name, create an alias. We typically use this method to
	 * support names that are illegal within XML ids (used for bean names).
	 * <p>Typically invoked during factory configuration, but can also be
	 * used for runtime registration of aliases. Therefore, a factory
	 * implementation should synchronize alias access.
	 *
	 * @param beanName the canonical name of the target bean
	 * @param alias    the alias to be registered for the bean
	 * @throws BeanDefinitionStoreException if the alias is already in use
	 */
	void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

	/**
	 * 使用 字符串值处理器 处理 Bean 的别名
	 * <p>
	 * 使用 指定的 StringValueResolver 处理所有的别名
	 * <p>
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 *
	 * @param valueResolver the StringValueResolver to apply
	 * @since 2.5
	 */
	void resolveAliases(StringValueResolver valueResolver);

	/**
	 * 根据给定的 BeanName，获取 一个 合并的 BeanDefinition。
	 * 如果有需要，父类Bean定义会合并其子类的Bean定义——合并bean定义,包括父容器的。
	 * <p>
	 * Return a merged BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * Considers bean definitions in ancestor factories as well.
	 *
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) BeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @since 2.5
	 */
	BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * 判断给定的 Bean 是否是一个 工厂Bean。
	 * <p>
	 * Determine whether the bean with the given name is a FactoryBean.
	 *
	 * @param name the name of the bean to check
	 * @return whether the bean is a FactoryBean
	 * ({@code false} means the bean exists but is not a FactoryBean)
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.5
	 */
	boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

	/**
	 * 设置一个Bean是否正在创建
	 * <p>
	 * 明确的控制 当前创建中 状态的 BeanName。这个方法仅供容器内部使用。
	 * <p>
	 * Explicitly control the current in-creation status of the specified bean.
	 * For container-internal use only.
	 *
	 * @param beanName   the name of the bean
	 * @param inCreation whether the bean is currently in creation
	 * @since 3.1
	 */
	void setCurrentlyInCreation(String beanName, boolean inCreation);

	/**
	 * bean创建状态控制.在解决循环依赖时有使用
	 * <p>
	 * 返回指定 Bean 是否已经成功创建
	 * <p>
	 * Determine whether the specified bean is currently in creation.
	 *
	 * @param beanName the name of the bean
	 * @return whether the bean is currently in creation
	 * @since 2.5
	 */
	boolean isCurrentlyInCreation(String beanName);

	/**
	 * 注册 BeanName 的依赖 Bean，销毁时依赖 Bean 实例 会先被销毁，然后销毁 BeanName 实例
	 * <p>
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 *
	 * @param beanName          the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 * @since 2.5
	 */
	void registerDependentBean(String beanName, String dependentBeanName);

	/**
	 * 获取 依赖于 BeanName 的所有 Bean
	 * <p>
	 * Return the names of all beans which depend on the specified bean, if any.
	 *
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 * @since 2.5
	 */
	String[] getDependentBeans(String beanName);

	/**
	 * 获取 BeanName 实例 依赖的 所有 Bean
	 * <p>
	 * Return the names of all beans that the specified bean depends on, if any.
	 *
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 * @since 2.5
	 */
	String[] getDependenciesForBean(String beanName);

	/**
	 * bean生命周期管理--销毁bean：销毁指定的Bean
	 * <p>
	 * 销毁 给定的 Bean实例，通常是 从本工厂类获取的 原型类实例
	 * <p>
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to its bean definition.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 *
	 * @param beanName     the name of the bean definition
	 * @param beanInstance the bean instance to destroy
	 */
	void destroyBean(String beanName, Object beanInstance);

	/**
	 * bean生命周期管理--销毁bean：销毁指定的范围Bean
	 * <p>
	 * 销毁区域范围内，给定 BeanName 的 Bean实例
	 * <p>
	 * Destroy the specified scoped bean in the current target scope, if any.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 *
	 * @param beanName the name of the scoped bean
	 */
	void destroyScopedBean(String beanName);

	/**
	 * bean生命周期管理-- 销毁bean：销毁所有的单例类
	 * <p>
	 * 销毁 BeanFactory中的所有 Bean
	 * <p>
	 * Destroy all singleton beans in this factory, including inner beans that have
	 * been registered as disposable. To be called on shutdown of a factory.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 */
	void destroySingletons();

}
