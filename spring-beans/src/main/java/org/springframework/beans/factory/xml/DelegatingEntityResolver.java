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

package org.springframework.beans.factory.xml;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;

/**
 * Spring中使用 DelegatingEntityResolver 实现 EntityResolver，主要是 提供一个寻找DTD声明的方法，以通过SAX实现对XML文档的验证.
 * resolveEntity 方法的实现 {@link DelegatingEntityResolver#resolveEntity} 所示。
 * <p>
 * 对于 XSD 配置格式的 XML 文件而言：{@link EntityResolver#resolveEntity} 方法中两个参数
 * publicId 和 systemId 分别为：null，http://www.Springframework.org/schema/beams/Spring-beans.xsd
 * ```xml
 * <?xml version="1.0" encoding="UTF-8"?>
 * <beans xmlns="http://www.Springframework.org/schema/beams"
 * xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
 * xsi:schemaLocation="https://www/springframework.org/schema/beans
 * http://www.Springframework.org/schema/beams/Spring-beans.xsd">
 * </beans>
 * ```
 * <p>
 * 对于 DTD 验证模式的 XML文档中：{@link EntityResolver#resolveEntity} 方法中两个参数
 * publicId 和 systemId 分别为：-//SPRING//DTD BEAN 2.0//EN，https://www.springframework.org/dtd/spring-beans-2.0.dtd
 * ```xml
 * <?xml version="1.0" encoding="UTF-8"?>
 * <!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN 2.0//EN" "https://www.springframework.org/dtd/spring-beans-2.0.dtd">
 * <beans> </beans>
 * ```
 * <p>
 * {@link EntityResolver} implementation that delegates to a {@link BeansDtdResolver}
 * and a {@link PluggableSchemaResolver} for DTDs and XML schemas, respectively.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @see BeansDtdResolver
 * @see PluggableSchemaResolver
 * @since 2.0
 */
public class DelegatingEntityResolver implements EntityResolver {

	/**
	 * Suffix for DTD files.
	 */
	public static final String DTD_SUFFIX = ".dtd";

	/**
	 * Suffix for schema definition files.
	 */
	public static final String XSD_SUFFIX = ".xsd";


	private final EntityResolver dtdResolver;

	private final EntityResolver schemaResolver;


	/**
	 * Create a new DelegatingEntityResolver that delegates to
	 * a default {@link BeansDtdResolver} and a default {@link PluggableSchemaResolver}.
	 * <p>Configures the {@link PluggableSchemaResolver} with the supplied
	 * {@link ClassLoader}.
	 *
	 * @param classLoader the ClassLoader to use for loading
	 *                    (can be {@code null}) to use the default ClassLoader)
	 */
	public DelegatingEntityResolver(@Nullable ClassLoader classLoader) {
		this.dtdResolver = new BeansDtdResolver();
		this.schemaResolver = new PluggableSchemaResolver(classLoader);
	}

	/**
	 * Create a new DelegatingEntityResolver that delegates to
	 * the given {@link EntityResolver EntityResolvers}.
	 *
	 * @param dtdResolver    the EntityResolver to resolve DTDs with
	 * @param schemaResolver the EntityResolver to resolve XML schemas with
	 */
	public DelegatingEntityResolver(EntityResolver dtdResolver, EntityResolver schemaResolver) {
		Assert.notNull(dtdResolver, "'dtdResolver' is required");
		Assert.notNull(schemaResolver, "'schemaResolver' is required");
		this.dtdResolver = dtdResolver;
		this.schemaResolver = schemaResolver;
	}


	/**
	 * EntityResolver 接口 实现类。不同的验证模式，返回了不同的 解析器。
	 * <p>
	 * 加载 DTD 类型的是 {@link BeansDtdResolver#resolveEntity} 方法，是直接 截取systemId 最后的xx.dtd
	 * 然后从当前路径下寻找；
	 * <p>
	 * 加载 XSD 类型的是 {@link PluggableSchemaResolver#resolveEntity} 方法，默认到 META-INF/Spring.schemas
	 * 文件中找到 systemId 对应的 XSD 文件并加载。
	 *
	 * @param publicId publicId
	 * @param systemId systemId
	 * @return InputSource
	 * @throws SAXException SAX解析异常
	 * @throws IOException  IO异常
	 */
	@Override
	@Nullable
	public InputSource resolveEntity(@Nullable String publicId, @Nullable String systemId)
			throws SAXException, IOException {

		if (systemId != null) {
			if (systemId.endsWith(DTD_SUFFIX)) {
				// 如果是 DTD 验证模式，从这里解析
				return this.dtdResolver.resolveEntity(publicId, systemId);
			} else if (systemId.endsWith(XSD_SUFFIX)) {
				// 如果是 XSD 验证模式，通过调用 META-INF/Spring.schemas 解析
				return this.schemaResolver.resolveEntity(publicId, systemId);
			}
		}

		// Fall back to the parser's default behavior.
		return null;
	}


	@Override
	public String toString() {
		return "EntityResolver delegating " + XSD_SUFFIX + " to " + this.schemaResolver +
				" and " + DTD_SUFFIX + " to " + this.dtdResolver;
	}

}
