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

package org.springframework.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主要使用 ConcurrentHashMap 作为 alias 的缓存，并实现接口 AliasRegistry
 * <p>
 * Simple implementation of the {@link AliasRegistry} interface.
 * <p>Serves as base class for
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
 * implementations.
 *
 * @author Juergen Hoeller
 * @author Qimiao Chen
 * @since 2.5.2
 */
public class SimpleAliasRegistry implements AliasRegistry {

	/**
	 * Logger available to subclasses.
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * Map from alias to canonical name.
	 */
	private final Map<String, String> aliasMap = new ConcurrentHashMap<>(16);

	/**
	 * 注册别名
	 * 向 ConcurrentHashMap 中添加 别名 和 规范名称 的映射关系
	 *
	 * @param name  the canonical name
	 * @param alias the alias to be registered
	 */
	@Override
	public void registerAlias(String name, String alias) {
		Assert.hasText(name, "'name' must not be empty");
		Assert.hasText(alias, "'alias' must not be empty");
		synchronized (this.aliasMap) {
			if (alias.equals(name)) {
				// 别名 == 规范名称，此时已不需要别名，从map中移除
				this.aliasMap.remove(alias);
				if (logger.isDebugEnabled()) {
					logger.debug("Alias definition '" + alias + "' ignored since it points to same name");
				}
			} else {
				String registeredName = this.aliasMap.get(alias);
				if (registeredName != null) {
					// 根据 别名 从 map 中取出规范名称
					if (registeredName.equals(name)) {
						// An existing alias - no need to re-register：别名映射关系已存在，不需要重复注册
						return;
					}
					// 规范名称 已经有一个别名了，判断是否允许多个别名，若不允许就抛出 IllegalStateException 异常
					if (!allowAliasOverriding()) {
						throw new IllegalStateException("Cannot define alias '" + alias + "' for name '" +
								name + "': It is already registered for name '" + registeredName + "'.");
					}
					if (logger.isDebugEnabled()) {
						logger.debug("Overriding alias '" + alias + "' definition for registered name '" +
								registeredName + "' with new target name '" + name + "'");
					}
				}

				checkForAliasCircle(name, alias);
				// 添加 别名-规范名称 映射关系
				this.aliasMap.put(alias, name);
				if (logger.isTraceEnabled()) {
					logger.trace("Alias definition '" + alias + "' registered for name '" + name + "'");
				}
			}
		}
	}

	/**
	 * 是否允许别名重写(即多个别名)，默认是true
	 * 在 DefaultListableBeanFactory 容器类中有实现方法，默认为true，允许修改
	 * <p>
	 * Determine whether alias overriding is allowed.
	 * <p>Default is {@code true}.
	 */
	protected boolean allowAliasOverriding() {
		return true;
	}

	/**
	 * 判断 给定的别名和规范名称是否已经注册，true-已经注册，false-未注册
	 * Determine whether the given name has the given alias registered.
	 *
	 * @param name  the name to check （别名）
	 * @param alias the alias to look for （规范名称）
	 * @since 4.2.1
	 */
	public boolean hasAlias(String name, String alias) {
		// 取出缓存中 别名对应的 规范名称
		String registeredName = this.aliasMap.get(alias);
		//判定 给定规范名称name和获取到的规范名称 registeredName 是否相等，true返回，false则继续判定
		//规范名称 registeredName 不为null，再判定以registeredName为别名在字典中是否存在规范名称name
		return ObjectUtils.nullSafeEquals(registeredName, name) || (registeredName != null
				&& hasAlias(name, registeredName));
	}

	/**
	 * 移除给定的别名
	 *
	 * @param alias the alias to remove
	 */
	@Override
	public void removeAlias(String alias) {
		synchronized (this.aliasMap) {
			String name = this.aliasMap.remove(alias);
			if (name == null) {
				throw new IllegalStateException("No alias '" + alias + "' registered");
			}
		}
	}

	/**
	 * 判断 指定名称 是否是别名，在 Map 中是否存在
	 *
	 * @param name the name to check
	 * @return true-是别名，false-不是别名
	 */
	@Override
	public boolean isAlias(String name) {
		return this.aliasMap.containsKey(name);
	}

	/**
	 * 获取 指定名称 的 所有别名数组
	 *
	 * @param name the name to check for aliases
	 * @return 别名数组
	 */
	@Override
	public String[] getAliases(String name) {
		List<String> result = new ArrayList<>();
		synchronized (this.aliasMap) {
			retrieveAliases(name, result);
		}
		return StringUtils.toStringArray(result);
	}

	/**
	 * 遍历 ConcurrentHashMap，获取 规范名称的所有别名
	 * Transitively retrieve all aliases for the given name.
	 *
	 * @param name   the target name to find aliases for 规范名称
	 * @param result the resulting aliases list 别名集合
	 */
	private void retrieveAliases(String name, List<String> result) {
		this.aliasMap.forEach((alias, registeredName) -> {
			if (registeredName.equals(name)) {
				result.add(alias);
				retrieveAliases(alias, result);
			}
		});
	}

	/**
	 * 解析此 缓存中 注册的 所有别名 和 目标名称，并对它们 应用给定的 StringValueResolver
	 * 例如，值解析器可以解析目标bean名称甚至别名中的占位符。
	 * 为了便于理解，将 注册表中未经处理的别名和值标识为 A，经过字符串解析器处理的标识为 B。
	 * <p>
	 * Resolve all alias target names and aliases registered in this
	 * registry, applying the given {@link StringValueResolver} to them.
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 *
	 * @param valueResolver the StringValueResolver to apply 字符串值解析程序
	 */
	public void resolveAliases(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		synchronized (this.aliasMap) {
			Map<String, String> aliasCopy = new HashMap<>(this.aliasMap);
			// 遍历 Map 注册表，alias-别名 A，registeredName—规范名称 A
			aliasCopy.forEach((alias, registeredName) -> {
				// 字符串解析器处理后的 别名 B、规范名称 B
				String resolvedAlias = valueResolver.resolveStringValue(alias);
				String resolvedName = valueResolver.resolveStringValue(registeredName);

				// 别名B和规范名称B解析后为null，或者相等，说明别名A不规范或者别名A==规范名称A，那么从注册缓存中移除别名A
				if (resolvedAlias == null || resolvedName == null || resolvedAlias.equals(resolvedName)) {
					this.aliasMap.remove(alias);
				} else if (!resolvedAlias.equals(alias)) {
					// 别名B != 别名A（注册表中别名 经过字符串工具解析前后不相等）
					// 使用别名B 从注册表中获取 别名B 的 规范名称 C
					String existingName = this.aliasMap.get(resolvedAlias);
					if (existingName != null) {
						// 如果，规范名称C == 规范名称B，说明别名B 在注册表中也有映射关系，
						// 并且 映射的内容 和 别名A映射的内容相同，因此注册表中移除别名A的映射关系
						if (existingName.equals(resolvedName)) {
							// Pointing to existing alias - just remove placeholder
							this.aliasMap.remove(alias);
							// 结束 本 resolveAliases() 方法
							return;
						}
						// 规范名称C != 规范名称B，并且别称A != 别称B
						throw new IllegalStateException(
								"Cannot register resolved alias '" + resolvedAlias + "' (original: '" + alias +
										"') for name '" + resolvedName + "': It is already registered for name '" +
										registeredName + "'.");
					}
					// 规范名称 C不存在，即别名B在注册表中没有映射关系。那么判断别名B和规范名称B是否已经注册。若是，则抛出异常
					checkForAliasCircle(resolvedName, resolvedAlias);
					// 移除 别名A 的映射关系
					this.aliasMap.remove(alias);
					// 添加 别名B 和 规范名称B 的映射关系。（理解为 将别名和规范名称进行字符串解析后，重新放入缓存）
					this.aliasMap.put(resolvedAlias, resolvedName);
				} else if (!registeredName.equals(resolvedName)) { // 别名A==别名B，且 规范名称A!=规范名称B
					// 将 别名A，规范名称B 的映射关系存入 注册表
					this.aliasMap.put(alias, resolvedName);
				}
			});
		}
	}

	/**
	 * 判断 给定的别名 和规范名称 是否 已经注册
	 * Check whether the given name points back to the given alias as an alias
	 * in the other direction already, catching a circular reference upfront
	 * and throwing a corresponding IllegalStateException.
	 *
	 * @param name  the candidate name 规范名称
	 * @param alias the candidate alias 别名
	 * @see #registerAlias
	 * @see #hasAlias
	 */
	protected void checkForAliasCircle(String name, String alias) {
		if (hasAlias(alias, name)) {
			throw new IllegalStateException("Cannot register alias '" + alias +
					"' for name '" + name + "': Circular reference - '" +
					name + "' is a direct or indirect alias for '" + alias + "' already");
		}
	}

	/**
	 * 确定原始名称，将别名解析为 规范名称
	 * Determine the raw name, resolving aliases to canonical names.
	 *
	 * @param name the user-specified name
	 * @return the transformed name
	 */
	public String canonicalName(String name) {
		String canonicalName = name;
		// Handle aliasing...
		String resolvedName;
		do {
			// 从 注册表中取出 value，即 resolvedName 表示 规范名称
			// canonicalName 表示别名
			resolvedName = this.aliasMap.get(canonicalName);
			if (resolvedName != null) {
				// 规范名称存在，那么使用 规范名称 作为 别名的参数，循环执行 ，直到取出的规范名称为null。
				canonicalName = resolvedName;
			}
		}
		while (resolvedName != null);
		// 返回 别名
		return canonicalName;
	}

}
