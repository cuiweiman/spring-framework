package org.springframework.beans.factory.xml;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.Resource;

/**
 * 对 {@link DefaultListableBeanFactory} 类进行扩展，主要用于从 XML 文件中读取 BeanDefinition ，对于
 * 注册及获取 Bean 都是使用从父类 DefaultListableBeanFactory 继承的方法。与父类的不同在于，个性化实现了
 * {@link XmlBeanDefinitionReader} 类的 Reader 属性对 XML 资源进行数据读取。
 * <p>
 * ```java
 * BeanFactory bf = new XmlBeanFactory(new ClassPathResource("beanResourceTest.xml"));
 * ```
 * 首先调用 {@link org.springframework.core.io.ClassPathResource} 的构造函数，来构造 Resource 资源文件对象的实例，
 * 然后就可以使用 Resource 提供的各种服务来操作，有了Resource后就可以进行 XmlBeanFactory 初始化了。可是 Resource资
 * 源是如何封装的呢？{@see org.springframework.core.io.ClassPathResource}
 * <p>
 * Convenience extension of {@link DefaultListableBeanFactory} that reads bean definitions
 * from an XML document. Delegates to {@link XmlBeanDefinitionReader} underneath; effectively
 * equivalent to using an XmlBeanDefinitionReader with a DefaultListableBeanFactory.
 *
 * <p>The structure, element and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). "beans" doesn't need to be the root element of the XML
 * document: This class will parse all bean definition elements in the XML file.
 *
 * <p>This class registers each bean definition with the {@link DefaultListableBeanFactory}
 * superclass, and relies on the latter's implementation of the {@link BeanFactory} interface.
 * It supports singletons, prototypes, and references to either of these kinds of bean.
 * See {@code "spring-beans-3.x.xsd"} (or historically, {@code "spring-beans-2.0.dtd"}) for
 * details on options and configuration style.
 *
 * <p><b>For advanced needs, consider using a {@link DefaultListableBeanFactory} with
 * an {@link XmlBeanDefinitionReader}.</b> The latter allows for reading from multiple XML
 * resources and is highly configurable in its actual XML parsing behavior.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @see org.springframework.beans.factory.support.DefaultListableBeanFactory
 * @see XmlBeanDefinitionReader
 * @since 15 April 2001
 * @deprecated as of Spring 3.1 in favor of {@link DefaultListableBeanFactory} and
 * {@link XmlBeanDefinitionReader}
 */
@Deprecated
@SuppressWarnings({"serial", "all"})
public class XmlBeanFactory extends DefaultListableBeanFactory {

	private final XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(this);


	/**
	 * <p>
	 * 当Resource相关类封装好 XML配置文件后，配置文件的读取工作交给 XmlBeanDefinitionReader 处理。
	 * XmlBeanFactory初始化有若干方法
	 * </p>
	 * Create a new XmlBeanFactory with the given resource,
	 * which must be parsable using DOM.
	 *
	 * @param resource the XML resource to load bean definitions from
	 * @throws BeansException in case of loading or parsing errors
	 */
	public XmlBeanFactory(Resource resource) throws BeansException {
		// 调用 XmlBeanFactory(Resource,BeanFactory) 构造方法
		this(resource, null);
	}

	/**
	 * 真正实现资源加载的构造函数：XmlBeanDefinitionReader 进行数据加载
	 * 将配置在XML文档中的 BeanDefinition 解析出来并添加到 Spring 容器中，以便在使用时注入
	 * </p>
	 * parentBeanFactory 父类的 BeanFactory，用于 Factory 的合并，可以为null。
	 * <p>
	 * Create a new XmlBeanFactory with the given input stream,
	 * which must be parsable using DOM.
	 *
	 * @param resource          the XML resource to load bean definitions from
	 * @param parentBeanFactory parent bean factory。
	 * @throws BeansException in case of loading or parsing errors
	 */
	public XmlBeanFactory(Resource resource, BeanFactory parentBeanFactory) throws BeansException {
		// 调用父类构造函数，进行初始化。{@code AbstractAutowireCapableBeanFactory#AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory)}
		super(parentBeanFactory);
		// 资源加载的实现
		this.reader.loadBeanDefinitions(resource);
	}

}
