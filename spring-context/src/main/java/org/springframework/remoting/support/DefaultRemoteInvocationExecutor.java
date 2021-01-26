/*
 * Copyright 2002-2012 the original author or authors.
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

package org.springframework.remoting.support;

import org.springframework.util.Assert;

import java.lang.reflect.InvocationTargetException;

/**
 * Default implementation of the {@link RemoteInvocationExecutor} interface.
 * Simply delegates to {@link RemoteInvocation}'s invoke method.
 *
 * @author Juergen Hoeller
 * @see RemoteInvocation#invoke
 * @since 1.1
 */
public class DefaultRemoteInvocationExecutor implements RemoteInvocationExecutor {

	/**
	 * @param invocation   the RemoteInvocation
	 * @param targetObject the target object to apply the invocation to
	 * @return 结果
	 * @throws NoSuchMethodException     异常
	 * @throws IllegalAccessException    异常
	 * @throws InvocationTargetException 异常
	 * @see RemoteInvocation#invoke(java.lang.Object)
	 */
	@Override
	public Object invoke(RemoteInvocation invocation, Object targetObject)
			throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {

		Assert.notNull(invocation, "RemoteInvocation must not be null");
		Assert.notNull(targetObject, "Target object must not be null");
		// 通过反射方式激活方法
		return invocation.invoke(targetObject);
	}

}
