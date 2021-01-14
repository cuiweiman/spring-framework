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

package org.springframework.aop.aspectj;

import java.io.Serializable;
import java.lang.reflect.Method;

import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.lang.Nullable;

/**
 * 与后置 增强器不同，在拦截器链中放置了 {@link org.springframework.aop.framework.adapter.MethodBeforeAdviceInterceptor}，
 * 并在其中又放置了 {@link AspectJMethodBeforeAdvice}，在调用 invoke 时首先串联调用。
 * <p>
 * Spring AOP advice that wraps an AspectJ before method.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @see org.springframework.aop.framework.adapter.MethodBeforeAdviceInterceptor
 * @since 2.0
 */
@SuppressWarnings("serial")
public class AspectJMethodBeforeAdvice extends AbstractAspectJAdvice implements MethodBeforeAdvice, Serializable {

	public AspectJMethodBeforeAdvice(
			Method aspectJBeforeAdviceMethod, AspectJExpressionPointcut pointcut, AspectInstanceFactory aif) {

		super(aspectJBeforeAdviceMethod, pointcut, aif);
	}


	/**
	 * before 方法
	 *
	 * @param method the method being invoked
	 * @param args   the arguments to the method
	 * @param target the target of the method invocation. May be {@code null}.
	 * @throws Throwable 异常
	 */
	@Override
	public void before(Method method, Object[] args, @Nullable Object target) throws Throwable {
		invokeAdviceMethod(getJoinPointMatch(), null, null);
	}

	@Override
	public boolean isBeforeAdvice() {
		return true;
	}

	@Override
	public boolean isAfterAdvice() {
		return false;
	}

}
