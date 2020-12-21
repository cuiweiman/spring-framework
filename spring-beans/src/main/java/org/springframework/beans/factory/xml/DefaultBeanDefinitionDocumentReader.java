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

package org.springframework.beans.factory.xml;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * 提取 Root ，以便再次将 root 作为参数继续 BeanDefinition 的注册
	 * <p>
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		this.readerContext = readerContext;
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * 真正开始 解析 XML 文档。
	 * 首先 对 profile 进行处理，然后开始解析，解析的预处理方法和后置处理方法都是空的，是专门为子类设计的——模板方法模式，
	 * 如果 继承自 DefaultBeanDefinitionDocumentReader 的子类需要对 Bean 解析前后做一些处理，只需重写这两个方法。
	 * <p>
	 * 使用给定根 的  {@code <beans/>} 元素节点，注册其中的每个 bean definition。
	 * <p>
	 * Register each bean definition within the given root {@code <beans/>} element.
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.

		// 专门处理解析
		BeanDefinitionParserDelegate parent = this.delegate;
		this.delegate = createDelegate(getReaderContext(), root, parent);

		/*
		 * 处理 profile 属性。
		 * ```xml
		 * -- 例如 JDBC 在XML文档中配置的 Bean
		 * <beans profile="dev">……</beans>
		 * <beans profile="production">……</beans>
		 * ```
		 * 集成到 WEB环境中后，在web.xml中进行如下配置：即可 配置生产环境和开发环境使用的 Bean 。
		 * ```xml
		 * <context-params>
		 * 		<param-name>Spring.profiles.active</param-name>
		 *	 	<param-value>dev</param-value>
		 * </context-params>
		 * ```
		 *
		 */
		if (this.delegate.isDefaultNamespace(root)) {
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}
		// 解析前处理，留给子类实现
		preProcessXml(root);
		// 从XML 中解析 出 XML配置的 BeanDefinition 信息
		parseBeanDefinitions(root, this.delegate);
		// 解析后处理，留给子类实现
		postProcessXml(root);

		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * 分析文档中 根级别 的元素。
	 * <p>
	 * 在 XML配置中，有两大类 Bean 声明，一个是默认的，如： <bean id="test" class="test.TestBean"/>，另一种是自定义的，如：
	 * <tx:annotation-driven />。这两种的读取以及解析有很大差别。如果是默认的，Spring 知道如何解析，但对于自定义的，需要用户实现
	 * 一些接口及配置。
	 * 对于 root节点或者子节点，如果是默认命名空间，采用 {@link #parseDefaultElement} 方法解析，否则使用
	 * {@link BeanDefinitionParserDelegate#parseCustomElement(Element)} 对自定义命名空间进行解析。
	 * <p>
	 * Spring中固定的默认命名空间是 “ http://www.springframework.org/schema/beans ”，判断是默认命名空间还是自定义的命名空间，
	 * 是使用 {@link BeanDefinitionParserDelegate#isDefaultNamespace(Node)}方法获取到命名空间，进行对比，如果一直则是默认命名空间，否则是自定义的。
	 * <p>
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 *
	 * @param root the DOM root element of the document
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// 对 Beas 处理
		if (delegate.isDefaultNamespace(root)) {
			// root节点 是 Spring 中存在的默认标签
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
						// 对 Bean标签 的处理
						parseDefaultElement(ele, delegate);
					} else {
						// 对 自定义标签 的处理
						delegate.parseCustomElement(ele);
					}
				}
			}
		} else {
			// 对 自定义标签 的处理
			delegate.parseCustomElement(root);
		}
	}

	/**
	 * 解析 XML 中 的 标签
	 * {@link #importBeanDefinitionResource} 解析 import 标签
	 * {@link #processAliasRegistration} 解析 alias 标签
	 * {@link #processBeanDefinition} 解析 bean 标签
	 * {@link #doRegisterBeanDefinitions} 解析 beans 标签
	 *
	 * @param ele      ele
	 * @param delegate delegate
	 */
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			// <import> 标签的处理
			importBeanDefinitionResource(ele);
		} else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			// <alias> 标签的处理
			processAliasRegistration(ele);
		} else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			// <bean> 标签的处理
			processBeanDefinition(ele, delegate);
		} else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// <beans> 标签的处理（recurse 递归）
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * <import> 标签的解析，并从给定的 resource 中加载 beanDefinition 到 BeanFactory 中，过程：
	 * 1. 获取标签中的 resource 属性值，记录在location 变量中
	 * 2. 解析路径中的系统属性，格式如 "${user.dir}"
	 * 3. 判断 location 是绝对路径还是相对路径
	 * 4. 如果是绝对路径，则直接根据地址加载资源文件，并递归调用 bean 的解析过程，进行一次解析
	 * 5. 如果是相对路径，则计算出绝对路径，在进行资源加载和解析
	 * 6. 解析完成，触发监听器事件
	 * <p>
	 * ```xml
	 * <?xml version="1.0" encoding="UTF-8"?>
	 * <!DOCTYPE beans PUBLIC "-//SPRING//DTD BEAN 2.0//EN" "https://www.springframework.org/dtd/spring-beans-2.0.dtd">
	 * <beans>
	 * <import resource="customerContext.xml"/>
	 * <import resource="systemContext.xml"/>
	 * ……
	 * </beans>
	 * ```
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 */
	protected void importBeanDefinitionResource(Element ele) {
		// 获取 resource 属性值：location
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			// 如果 resource 属性值不存在，那么不做任何处理
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// 解析系统属性，格式如："${user.dir}"
		// Resolve system properties: e.g. "${user.dir}"
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// 判断 resource 属性值 是个 绝对路径，还是 相对路径，true——绝对路径
		// Discover whether the location is an absolute or relative URI
		boolean absoluteLocation = false;
		try {
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		} catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
		}

		// Absolute or relative?
		if (absoluteLocation) {
			// 如果是绝对路径，直接根据 路径地址，加载相应的配置文件
			try {
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		} else {
			// No URL -> considering resource location as relative to the current file.
			// 如果是 相对路径，那么根据相对路径，计算出 绝对路径地址
			try {
				int importCount;
				// Resource 接口有多个实现类，如 VfsResource、FileSystemResource等，而每个 resource 的 createRelative 方法都不同，
				// 所以这里先使用 子类实现类的方法去尝试解析
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					actualResources.add(relativeResource);
				} else {
					// 如果子实现类 解析失败，那么使用 默认解析器 ResourcePatternResolver 进行解析
					String baseLocation = getReaderContext().getResource().getURL().toString();
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			} catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}
		// 解析完成后，触发监听器
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * <alias> 标签的解析
	 * 在对bean定义时，除了 使用 id 属性来指定名称之外，可以使用 alias 标签提供多个名称，指向同一个 bean。
	 * 在给 bean 增加别名时，有两种方式，一种时 name 属性，另一种就是 alias 标签:
	 * ```xml
	 * <bean id="testBean" name ="testBean,testBean2" class="com.Test"/>
	 * ```
	 * ```xml
	 * <bean id="testBean" class="com.Test"/>
	 * <alias name="testBean" alias="testBean,testBean2 />
	 * ```
	 * <p>
	 * 使用场景：组件 A 在 XML 配置文件中定义了一个 componentA 的DataSource 类型的 Bean，
	 * 组件B 想在 其XML 中以 componentB 命名来引用此同一个 Bean。
	 * <p>
	 * Process the given alias element, registering the alias with the registry.
	 */
	protected void processAliasRegistration(Element ele) {
		// 获取 BeanName
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		// 获取 alias
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {
			try {
				// 注册 alias
				getReaderContext().getRegistry().registerAlias(name, alias);
			} catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			// 别名注册 完成后 通知 监听器 做 相应的处理逻辑
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Bean标签的解析。处理给定的 Bean 元素，解析 Bean Definition 并使用注册器进行注册。
	 * <p>
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		/*
		1. 委托 BeanDefinitionParserDelegate#parseBeanDefinitionElement 方法进行元素解析，并返回 BeanDefinitionHolder 实例
		此时 bdHolder 中已经包含配置文件的各种属性，如：class、name、id、alias 等。
		*/
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			/*
			2. bdHolder 不为空时，若存在 默认 bean 标签的子节点是 自定义标签，那么还需要再对 自定义标签进行解析。场景如：
			```xml
			<bean id="test" type="com.test">
				<mybean:user username="test"/>
			</bean>
			```
			即 Spring 中的 bean 使用的是 默认标签配置，但是其中的子元素 使用的是 自定义配置标签，此时 下面的函数就会进行解析。
			 */
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				// 解析完成后，要对 bdHolder 进行注册。委托给方法：BeanDefinitionReaderUtils#registerBeanDefinition 方法
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			} catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event. 发送 注册 事件
			// 发出响应事件，通知相关的监听器，这个 Bean 加载完成。
			/*
			通知 监听器： 解析与注册 完成。当开发者 需要对注册 BeanDefinition 事件进行监听时，可以注册监听器，
			将处理逻辑写入监听器，在 bean 解析和注册完成后触发。目前Spring中没有对此事件做任务逻辑处理。
			 */
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 *
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 *
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
