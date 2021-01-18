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

package org.springframework.jdbc.core;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;

import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.lang.Nullable;

/**
 * Simple adapter for {@link PreparedStatementSetter} that applies
 * given arrays of arguments and JDBC argument types.
 *
 * @author Juergen Hoeller
 * @since 3.2.3
 */
public class ArgumentTypePreparedStatementSetter implements PreparedStatementSetter, ParameterDisposer {

	@Nullable
	private final Object[] args;

	@Nullable
	private final int[] argTypes;


	/**
	 * Create a new ArgTypePreparedStatementSetter for the given arguments.
	 *
	 * @param args     the arguments to set
	 * @param argTypes the corresponding SQL types of the arguments
	 */
	public ArgumentTypePreparedStatementSetter(@Nullable Object[] args, @Nullable int[] argTypes) {
		if ((args != null && argTypes == null) || (args == null && argTypes != null) ||
				(args != null && args.length != argTypes.length)) {
			throw new InvalidDataAccessApiUsageException("args and argTypes parameters must match");
		}
		this.args = args;
		this.argTypes = argTypes;
	}


	@Override
	public void setValues(PreparedStatement ps) throws SQLException {
		int parameterPosition = 1;
		if (this.args != null && this.argTypes != null) {
			// 遍历 每个参数 来做 类型匹配与转换。
			for (int i = 0; i < this.args.length; i++) {
				Object arg = this.args[i];
				// 如果 参数 是一个 集合类，那么需要 进入集合类 内部 递归解析 集合内部的属性值。
				if (arg instanceof Collection && this.argTypes[i] != Types.ARRAY) {
					Collection<?> entries = (Collection<?>) arg;
					for (Object entry : entries) {
						// 集合类 的 属性值 是一个 对象数组。
						if (entry instanceof Object[]) {
							Object[] valueArray = ((Object[]) entry);
							for (Object argValue : valueArray) {
								doSetValue(ps, parameterPosition, this.argTypes[i], argValue);
								parameterPosition++;
							}
						} else {
							// 对单个参数 及 类型 的匹配处理
							doSetValue(ps, parameterPosition, this.argTypes[i], entry);
							parameterPosition++;
						}
					}
				} else {
					// 参数 不是一个集合类，直接 解析当前属性值。对单个参数 及 类型 的匹配处理。
					doSetValue(ps, parameterPosition, this.argTypes[i], arg);
					parameterPosition++;
				}
			}
		}
	}

	/**
	 * 对单个参数 及 类型 的匹配处理
	 * <p>
	 * 使用 传入的值和类型 为 准备语句 的 指定参数位置 设置值。如果需要，可以用子类重写此方法。
	 * <p>
	 * Set the value for the prepared statement's specified parameter position using the passed in
	 * value and type. This method can be overridden by sub-classes if needed.
	 *
	 * @param ps                the PreparedStatement
	 * @param parameterPosition index of the parameter position
	 * @param argType           the argument type
	 * @param argValue          the argument value
	 * @throws SQLException if thrown by PreparedStatement methods
	 */
	protected void doSetValue(PreparedStatement ps, int parameterPosition, int argType, Object argValue)
			throws SQLException {

		StatementCreatorUtils.setParameterValue(ps, parameterPosition, argType, argValue);
	}

	@Override
	public void cleanupParameters() {
		StatementCreatorUtils.cleanupParameters(this.args);
	}

}
