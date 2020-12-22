/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import org.springframework.beans.factory.*;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实现 SingletonBeanRegistry 接口的所有函数
 * <p>
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 * @since 2.0
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/**
	 * 要保留的最大抑制异常数量
	 * Maximum number of suppressed exceptions to preserve.
	 */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/**
	 * 单例对象缓存，存储 bean名称——bean实例映射
	 * Cache of singleton objects: bean name to bean instance.
	 */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/**
	 * 单例对象工厂缓存，存储 bean名称——对象工厂映射
	 * ObjectFactory：创建对象的工厂
	 * Cache of singleton factories: bean name to ObjectFactory.
	 */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/**
	 * 早期 单例对象的缓存，Bean名称——Bean实例
	 * Cache of early singleton objects: bean name to bean instance.
	 */
	private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);

	/**
	 * 已注册的单例集合，按照注册顺序，存储 Bean名称
	 * Set of registered singletons, containing the bean names in registration order.
	 */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/**
	 * 当前正在创建的 Bean名称 集合
	 * Collections.newSetFromMap(map): 对 Map 包装生成一个与 map.keySet() 对应的Set集合
	 * 传参 ConcurrentHashMap 是线程安全的，那么得到的Set也将线程安全。
	 * <p>
	 * Names of beans that are currently in creation.
	 */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * 当前 从创建检查中排除的 bean 的名称。即 实例创建时 不需要进行 创建检查的 BeanName
	 * 如果集合汇总有 BeanName，那么在调用 {@see getSingleton(String beanName, ObjectFactory < ? > singletonFactory)}
	 * 方法时，不需要检查 当前Bean 是否正在创建。
	 * Names of beans currently excluded from in creation checks.
	 */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/**
	 * 抑制的异常 集合 ，可用于关联发生异常的原因
	 * Collection of suppressed Exceptions, available for associating related causes.
	 */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/**
	 * 标志，当前我们是否在 destroySingletons 中。即 实例销毁状态
	 * 如果是true，代表单例已经执行了自身的 destroy销毁方法，或者有异常的时候执行了destroySingleton方法等情况
	 * <p>
	 * Flag that indicates whether we're currently within destroySingletons.
	 */
	private boolean singletonsCurrentlyInDestruction = false;

	/**
	 * 需要被销毁 bean实例：Bean名称——Bean实例
	 * Disposable bean instances: bean name to disposable instance.
	 */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/**
	 * 依赖关系映射，外部类 Bean —— 内嵌 Bean。即Bean——Bean属性中的Bean。
	 * 假如：订单类中，包含订单详情类、商品信息类。那么key=Order类,value={OrderDetail类、Product类 等}。
	 * <p>
	 * Map between containing bean names: bean name to Set of bean names that the bean contains.
	 */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/**
	 * bean 依赖关系的缓存： 当前BeanName——当前Bean属性中内嵌的其他Bean的Name
	 * key——当前Bean依赖的Bean，value——当前Bean属性中内嵌的其他Bean集合。
	 * 例如： Human类中包含 Hands类、Feet类、Legs类，那么 key=Human，value={Hands,Feet,Legs}
	 * <p>
	 * Map between dependent bean names: bean name to Set of dependent bean names.
	 */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/**
	 * bean 依赖关系的缓存：内嵌 Bean 的 Name——外部 Bean 的 Name
	 * 例如：Cat类中包含 Hands类，Dog类中包含Hands类，Wolf中包含Hands类，那么 key=Hands，value={Cat,Dog,Wolf等}
	 * <p>
	 * Map between depending bean names: bean name to Set of bean names for the bean's dependencies.
	 */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	/**
	 * 单例对象注册到 singletonObjects单例对象池中、registeredSingletons已注册单例对象池中。
	 *
	 * @param beanName        the name of the bean Bean名称
	 * @param singletonObject the existing singleton object 单例对象
	 * @throws IllegalStateException 异常
	 */
	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				// 判断 在容器中， BeanName 是否 已经有绑定的 单例对象
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * 注册 BeanName 和 单例对象 到容器中；被饿汉式单例对象调用
	 * <p>
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 *
	 * @param beanName        the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		// 对单例对象池加锁
		synchronized (this.singletonObjects) {
			// 添加到 单例对象的缓存容器 中
			this.singletonObjects.put(beanName, singletonObject);
			// 将 同名的单例工厂对象  从单例对象工厂池 移除
			this.singletonFactories.remove(beanName);
			// 从早期的 单例对象池 中将同名的单例对象移除
			this.earlySingletonObjects.remove(beanName);
			// 单例对象在registeredSingletons集合里进行登记.
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * 注册 单例对象创建工厂。对象工厂也是一个单例对象
	 * 需要注册到 单例对象池 registeredSingletons 以及 singletonFactories单例对象工厂池 中
	 * <p>
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 *
	 * @param beanName         the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		// 对单例对象池加锁
		synchronized (this.singletonObjects) {
			// 判断 当前单例对象池中 不包含 给定的 BeanName
			if (!this.singletonObjects.containsKey(beanName)) {
				// 将 BeanName和对应的单例创建工厂 注册到 单例工厂池中
				this.singletonFactories.put(beanName, singletonFactory);
				// 从早期的 单例对象池 中将同名的 单例工厂对象 移除
				this.earlySingletonObjects.remove(beanName);
				// 单例工厂对象 在registeredSingletons集合里进行登记.（单例对象创建工厂 也是一个单例对象）
				this.registeredSingletons.add(beanName);
			}
		}
	}

	/**
	 * 根据 BeanName 获取单例对象
	 * <p>
	 * 检查 缓存中 或者 实例工厂 中 是否有 对应的 单例 实例对象。
	 * 为什么首先使用这段代码呢？因为在创建单例 Bean 的时候会存在依赖注入的情况，而在创建依赖时为了避免循环依赖，Spring 创建
	 * bean 的原则是不等 Bean 创建完成就会将创建 Bean的 ObjectFactory 提前曝光，也就是将 ObjectFactory 加入到缓存中，
	 * 一旦下一个 Bean 创建时需要依赖上个 Bean，则直接使用 缓存中的 ObjectFactory。
	 *
	 * @param beanName the name of the bean to look for
	 * @return 例对象
	 */
	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		// 参数 true，设置标识为 允许早期依赖
		return getSingleton(beanName, true);
	}

	/**
	 * 根据给定 BeanName，从 单例对象池 singletonObjects 中返回单例对象实例
	 * <p>
	 * 检查 缓存中 或者 实例工厂 中 是否有 对应的 单例 实例对象。
	 * 为什么首先使用这段代码呢？因为在创建单例 Bean 的时候会存在依赖注入的情况，而在创建依赖时为了避免循环依赖，Spring 创建
	 * bean 的原则是不等 Bean 创建完成就会将创建 Bean的 ObjectFactory 提前曝光，也就是将 ObjectFactory 加入到缓存中，
	 * 一旦下一个 Bean 创建时需要依赖上个 Bean，则直接使用 缓存中的 ObjectFactory。
	 * <p>
	 * 首先尝试从 singletonObjects 里获取实例，获取不到时再从 earlySingletonObjects 中获取；如果仍然获取不到，
	 * 再尝试从 singletonFactories 中获取 beanName 对应的 ObjectFactory，再利用 {@link ObjectFactory#getObject()} 方法创建bean，
	 * 并放到 earlySingletonObjects 容器中，再从 singletonFactories 容器里 remove 掉这个 ObjectFactory。
	 * 而对于这两个容器的内存操作，都是为了循环依赖检测的时候使用，也就是在 allowEarlyReference 为true的情况下才会使用。
	 * <p>
	 * {@link #singletonObjects}:保存 BeanName 和 bean实例 之间的映射关系 bean name——>bean instance；
	 * {@link #singletonFactories}:保存 BeanName 和 bean工厂 之间的映射关系 bean name——>ObjectFactory；
	 * {@link #earlySingletonObjects}:保存 BeanName 和 bean实例 之间的映射关系；与 singletonObjects 的不同之处在于，
	 * 若单例 bean 被放到该容器中，表示该bean 尚在创建过程中，可以通过 getBean 方法获取到， 目的是用来检测 循环引用。
	 * {@link #registeredSingletons}:保存当前 所有 已经注册过的 bean 的 beanName（按照注册的顺序）
	 * <p>
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 *
	 * @param beanName            the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * @return the registered singleton object, or {@code null} if none found
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// Quick check for existing instance without full singleton lock
		// 检查缓存中是否存在实例，若存在则直接返回
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 单例对象池中不存在，但是 BeanName对应的单例对象 当前正在创建中，那么从 早期单例对象池 中获取单例对象 并 直接返回
			// 若 此bean 正在加载，则不进行处理，即 从 早起单例对象池 中获取到的单例对象 不为 null
			singletonObject = this.earlySingletonObjects.get(beanName);
			if (singletonObject == null && allowEarlyReference) {
				// 早期单例对象池 中也没有，但是 allowEarlyReference=true，即 允许创建早期对象实例
				// 首先，拿到单例对象锁，如果 singletonObject为null，锁定全局变量并进行处理
				synchronized (this.singletonObjects) {
					// 再次从单例对象池中取对象，因为当前正在创建的对象可能已经创建好了。如果仍然拿不到，说明还没有创建好
					// 此时synchronized会锁住当前单例对象，占住资源不被 创建单例对象的线程使用。如果拿到了，则 直接返回
					// Consistent creation of early reference within full singleton lock
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						// 从 早期单例对象池中 取出单例对象
						singletonObject = this.earlySingletonObjects.get(beanName);
						if (singletonObject == null) {
							/*
							 某些方法需要提前初始化时，会调用 addSingletonFactory 方法将对应的 ObjectFactory
							 初始化策略存储在 singletonFactories 中
							 */
							// 早期单例池中 也不存在单例对象，那么从单例对象工厂池中获取单例对象创建工厂对象
							ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
							if (singletonFactory != null) {
								// 调用预先设定的 getObject 方法
								/*
								使用 单例对象工厂 构造对象，然后将对象 添加到 早期对象缓存池中，
								并将单例对象工厂从工厂容器中移除,然后 返回刚创建好的 单例对象
								*/
								singletonObject = singletonFactory.getObject();
								// 记录到 缓存中，earlySingletonObjects 容器与 singletonFactories 容器互斥
								this.earlySingletonObjects.put(beanName, singletonObject);
								this.singletonFactories.remove(beanName);
							}
						}
					}
				}
			}
		}
		// 若单例对象池中存在，则直接返回；弱不存在，则尝试 查找和处理后 再返回
		return singletonObject;
	}

	/**
	 * 根据给定 BeanName，从 单例对象池 singletonObjects 中返回单例对象实例
	 * <p>
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 *
	 * @param beanName         the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 *                         with, if necessary
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		// 对 单例对象缓存池 加锁
		synchronized (this.singletonObjects) {
			// 从缓存池中 获取指定 BeanName 的单例对象
			Object singletonObject = this.singletonObjects.get(beanName);
			// 取出的单例对象为null，目前，在 单例对象缓存池 中不存在该单例对象
			if (singletonObject == null) {
				if (this.singletonsCurrentlyInDestruction) {
					// 单例对象正在销毁
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
									"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 创建 BeanName 实例前调用，
				beforeSingletonCreation(beanName);
				boolean newSingleton = false;
				// 异常集合是否为null，true-为null，则初始化 suppressedExceptions。
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					// 从给定的 对象工厂中生成单例对象实例
					singletonObject = singletonFactory.getObject();
					newSingleton = true;
				} catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					// 对象工厂中抛出 IllegalStateException 异常，从 单例对象池中获取单例对象，仍然获取不到，抛出异常
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {
						throw ex;
					}
				} catch (BeanCreationException ex) {
					if (recordSuppressedExceptions) {
						// suppressedExceptions 是空的，刚刚才初始化。将出现的异常，放入 suppressedExceptions 容器中
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				} finally {
					// 若 recordSuppressedExceptions 之前为空，那么重新置为空
					if (recordSuppressedExceptions) {
						this.suppressedExceptions = null;
					}
					// 单例对象创建后调用，若需要检查，那么从 当前正在创建的单例对象 缓存池中移除对应的BeanName
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					// 新对象创建成功，注册 BeanName 和 对象实例 到 Bean容器中
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * 在创建单例对象抛出异常时，将异常信息对象 放到 suppressedExceptions 容器中。
	 * 容器中可以存放 {@see SUPPRESSED_EXCEPTIONS_LIMIT} 个异常
	 * <p>
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 *
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * 给定的 BeanName，单例对象池、单例对象工厂池、早期单例对象池、已注册的单例对象池中移除 实例；
	 * <p>
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 *
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	/**
	 * 判断 单例对象池 中是否存在 BeanName 对应的实例
	 *
	 * @param beanName the name of the bean to look for
	 * @return 结果
	 */
	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	/**
	 * 返回所有 已注册的单例对象的 BeanName 数组
	 *
	 * @return 所有BeanName数组
	 */
	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	/**
	 * 返回所有 已注册的单例对象的 BeanName 个数
	 *
	 * @return 所有已注册的 BeanName 个数
	 */
	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}

	/**
	 * 设置 当前 BeanName 在创建时 是否需要检查
	 * false：将BeanName加入到 创建对象检查排除集合 中，表示创建时不需要检查；
	 * true：从 创建对象检查排除集合 中移除，表示创建时需要检查
	 *
	 * @param beanName   BeanName
	 * @param inCreation true——需要检查，false——不需要检查
	 */
	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		} else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	/**
	 * 如果需要创建检查的话，判断  BeanName 当前 是否 正在创建集合 中
	 *
	 * @param beanName BeanName
	 * @return 结果
	 */
	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * 判断当前 BeanName 是否正在创建中： 是否在 singletonsCurrentlyInCreation 容器中。
	 * <p>
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 *
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * 在单例对象创建前 调用。
	 * 若 创建检查中排除的BeanName集合中不存在（说明需要检查），
	 * 且 当前正在创建的BeanName集合 添加 BeanName 失败，则抛出异常。
	 * <p>
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 *
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * 单例对象创建后 调用。
	 * 在单例对象创建后，若 创建检查中排除的BeanName集合中不存在（说明需要检查），
	 * 且 当前正在创建的 BeanName 集合移除 BeanName失败，那么抛出异常。
	 * <p>
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 *
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * 将给定的 BeanName 和 对象实例  添加到 需要销毁的Bean集合中。
	 * <p>
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * 注册两个 Bean 的依赖关系。如内嵌 Bean 和 它的外部 Bean。
	 * <p>
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 *
	 * @param containedBeanName  the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			// 根据 BeanName，从 依赖关系集合中，取出当前Bean属性中内嵌的其他 Bean的BeanName，若不存在则返回一个初始化的 LinkedHashSet。
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			// 将 给定的注册关系 ，添加到 依赖关系集合中。结束方法。
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		// 同时 将 依赖关系 存储到  dependentBeanMap 容器 和 dependenciesForBeanMap 容器中。
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * 注册 依赖关系
	 * <p>
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 *
	 * @param beanName          the name of the bean
	 * @param dependentBeanName the name of the dependent bean
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		// 从 别名 容器中 获取最终的 BeanName
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			// 在 beanName 的内嵌依赖容器中，加入 内嵌依赖 dependentBeanName.
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			// 在 内嵌 Bean依赖容器中，加入 引用它的 外部依赖 BeanName
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * 判断 给定的 BeanName 和 dependentBeanName 主键是否存在依赖关系（包括内嵌类的内类等的所有关系）
	 * <p>
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 *
	 * @param beanName          the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			return false;
		}
		// 先根据 BeanName 获取 规范实例名
		String canonicalName = canonicalName(beanName);
		// 指定 BeanName作为外部类，获取其所有的内嵌类 BeanName 集合
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			// 内嵌类为空，肯定没有依赖关系
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			// 内嵌类包含 指定的 dependentBeanName，存在依赖关系
			return true;
		}
		for (String transitiveDependency : dependentBeans) {
			// 遍历 内嵌类集合，添加到 alreadySeen 集合中
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);
			// 递归判断 给定外部类BeanName 的 内嵌类的内嵌类中，是否包含 dependentBeanName
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 判断 BeanName 是否存在关系依赖（内嵌类）
	 * Determine whether a dependent bean has been registered for the given name.
	 *
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * 根据 BeanName，获取 其所有的 内嵌类 BeanName 的字符串数组
	 * <p>
	 * Return the names of all beans which depend on the specified bean, if any.
	 *
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * 根据 BeanName，获取 其所有的 外部类 BeanName 的字符串数组
	 * <p>
	 * Return the names of all beans that the specified bean depends on, if any.
	 *
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	/**
	 * 销毁 单例对象实例
	 */
	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			// 对单例对象池加锁，修改销毁标识 为true，表示当前处于 实例销毁状态
			this.singletonsCurrentlyInDestruction = true;
		}

		// 获取所有 待销毁的 BeanName，放入数组中
		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		// 清空 所有的依赖关系
		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		// 最终，清空 所有 单例对象的 容器
		clearSingletonCache();
	}

	/**
	 * 清空所有容器中的单例对象
	 * <p>
	 * Clear all cached singleton instances in this registry.
	 *
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * 将 给定的待销毁的 BeanName 的实例 进行 销毁
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 *
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		// 首先，从 所有 单例对象缓存池中 移除BeanName对应的实例
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		// 从 待销毁的对象池中，移除Bean，已经销毁了
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		// 最后，销毁 BeanName的所有内嵌依赖、销毁BeanName的实例、从外部类的依赖关系集合中清除BeanName的依赖。结束。
		destroyBean(beanName, disposableBean);
	}

	/**
	 * 消除 给定的 BeanName 和 即将销毁的 Bean 实例（先删除内嵌依赖，再销毁实例）
	 * 先销毁所有 内嵌Bean的依赖关系以及实例；再销毁 Bean实例；再销毁 BeanName 和 外部Bean的依赖关系。
	 * <p>
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 *
	 * @param beanName the name of the bean
	 * @param bean     the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// 首先 处理内嵌依赖，删除内嵌依赖的依赖关系，并从容器中清除内嵌依赖，以及内嵌依赖的内嵌依赖
		// 清除的 是 key=beanName 对应的所有 Value的值（第一次清除 dependencies 容器）
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// 1. 首先删除 BeanName 与 所有内嵌依赖 的 映射关系（只是清除了 映射关系）
			// Within full synchronization in order to guarantee a disconnected Set
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				// 遍历所有内嵌依赖，从缓存池中删除，并且会删除内嵌依赖的内嵌依赖
				destroySingleton(dependentBeanName);
			}
		}

		// 2. 然后再 调用 Bean实例 的销毁方法
		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			} catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// 3. 还需要清理 containedBeanMap 容器中的依赖关系，以及 依赖对象实例
		// containedBeanMap 容器中 的内容和  dependentBeanMap 容器中 的内容 我感觉是一样的。存储了相同的依赖关系
		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// 4. 再次 处理 dependentBeanMap 容器中的依赖关系。
		// 遍历容器中的 所有元素，清理的是容器中，Value集合中 的 beanName（第二次清理 dependentBeanMap容器）。
		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext(); ) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					// 如果value集合清理后，value的Set集合为null，那么也从dependentBeanMap容器中移除该 key。
					it.remove();
				}
			}
		}

		// 5. 最后 dependenciesForBeanMap 移除 BeanName
		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * 返回单例对象缓存池； final修饰表示：可以被子类继承，但不允许子类重写。
	 * <p>
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 */
	@Override
	public final Object getSingletonMutex() {
		return this.singletonObjects;
	}

}
