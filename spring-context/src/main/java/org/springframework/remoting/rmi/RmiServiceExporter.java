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

package org.springframework.remoting.rmi;

import java.rmi.AlreadyBoundException;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.remoting.support.RemoteInvocation;

/**
 * RMI——服务端 服务暴露 的 实现。
 * RMI 客户端实现 参见{@link RmiProxyFactoryBean}
 * <p>
 * RMI 服务暴露器，使用指定的 服务名称和端口，将指定的服务暴露出去。
 * 这些被暴露出来的服务可以通过普通的 RMI 或 {@link RmiProxyFactoryBean} 访问到。
 * 还支持通过 RMI 调用程序公开任何 非RMI 服务，这些服务将通过 {@link RmiClientInterceptor}
 * / {@link RmiProxyFactoryBean}对此类调用程序的自动检测 来访问。
 *
 * <p>使用RMI调用器，RMI 通信在{@link RmiInvocationHandler}级别上工作，任何服务只需要一个存根。
 * 服务接口不必扩展{@code java.rmi.Remote}或者抛出{@code java.rmi.RemoteException}，
 * 但 输入 和 输出参数 必须是可序列化的。
 *
 * <p>与 Hessian 相比，RMI 的主要优点是序列化。实际上，任何可序列化的 Java 对象都可以轻松地传输。
 * Hessian 有自己的（反）序列化机制，但是基于 HTTP协议，因此比 RMI 更容易设置。或者，可以考虑使用 Spring
 * 的 HTTP 协议调用程序，将 Java 序列化与基于 HTTP 协议的传输结合起来。
 *
 * <p>注意：RMI 尽最大努力去尝试获取主机名的所有权限。如果一个主机名无法确定，
 * 它将返回并使用 IP 地址。根据您的网络配置，在某些情况下，它会将 IP 解析为 回环地址。
 * 为了确保 RMI 将使用绑定到正确网络接口的主机名，您应该传递{@code java.rmi.server.hostname}属性，
 * 该属性将使用“-D”JVM参数 导出注册表和/或服务。例如：{@code -Djava.rmi.server.hostname=myServer.com}
 * <p>
 * RMI exporter that exposes the specified service as RMI object with the specified name.
 * Such services can be accessed via plain RMI or via {@link RmiProxyFactoryBean}.
 * Also supports exposing any non-RMI service via RMI invokers, to be accessed via
 * {@link RmiClientInterceptor} / {@link RmiProxyFactoryBean}'s automatic detection
 * of such invokers.
 *
 * <p>With an RMI invoker, RMI communication works on the {@link RmiInvocationHandler}
 * level, needing only one stub for any service. Service interfaces do not have to
 * extend {@code java.rmi.Remote} or throw {@code java.rmi.RemoteException}
 * on all methods, but in and out parameters have to be serializable.
 *
 * <p>The major advantage of RMI, compared to Hessian, is serialization.
 * Effectively, any serializable Java object can be transported without hassle.
 * Hessian has its own (de-)serialization mechanisms, but is HTTP-based and thus
 * much easier to setup than RMI. Alternatively, consider Spring's HTTP invoker
 * to combine Java serialization with HTTP-based transport.
 *
 * <p>Note: RMI makes a best-effort attempt to obtain the fully qualified host name.
 * If one cannot be determined, it will fall back and use the IP address. Depending
 * on your network configuration, in some cases it will resolve the IP to the loopback
 * address. To ensure that RMI will use the host name bound to the correct network
 * interface, you should pass the {@code java.rmi.server.hostname} property to the
 * JVM that will export the registry and/or the service using the "-D" JVM argument.
 * For example: {@code -Djava.rmi.server.hostname=myserver.com}
 *
 * @author Juergen Hoeller
 * @see RmiClientInterceptor
 * @see RmiProxyFactoryBean
 * @see java.rmi.Remote
 * @see java.rmi.RemoteException
 * @see org.springframework.remoting.caucho.HessianServiceExporter
 * @see org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter
 * @see #afterPropertiesSet() 接口 InitializingBean 的实现方法，自定义初始化方法
 * @see #destroy() 接口 DisposableBean 的实现方法，实例销毁时的自定义销毁方法。
 * @since 13.05.2003
 */
public class RmiServiceExporter extends RmiBasedExporter implements InitializingBean, DisposableBean {

	/**
	 * 服务在注册表中 注册的名字
	 */
	private String serviceName;

	private int servicePort = 0;

	/**
	 * 进行 远程对象 调用的 客户端套接字工厂
	 */
	private RMIClientSocketFactory clientSocketFactory;

	/**
	 * 接收 远程调用的 服务端套接字工厂
	 */
	private RMIServerSocketFactory serverSocketFactory;

	private Registry registry;

	private String registryHost;

	/**
	 * 服务注册的 端口，默认 1099。
	 */
	private int registryPort = Registry.REGISTRY_PORT;

	private RMIClientSocketFactory registryClientSocketFactory;

	private RMIServerSocketFactory registryServerSocketFactory;

	private boolean alwaysCreateRegistry = false;

	private boolean replaceExistingBinding = true;

	private Remote exportedObject;

	private boolean createdRegistry = false;


	/**
	 * Set the name of the exported RMI service,
	 * i.e. {@code rmi://host:port/NAME}
	 */
	public void setServiceName(String serviceName) {
		this.serviceName = serviceName;
	}

	/**
	 * Set the port that the exported RMI service will use.
	 * <p>Default is 0 (anonymous port).
	 */
	public void setServicePort(int servicePort) {
		this.servicePort = servicePort;
	}

	/**
	 * Set a custom RMI client socket factory to use for exporting the service.
	 * <p>If the given object also implements {@code java.rmi.server.RMIServerSocketFactory},
	 * it will automatically be registered as server socket factory too.
	 *
	 * @see #setServerSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see UnicastRemoteObject#exportObject(Remote, int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setClientSocketFactory(RMIClientSocketFactory clientSocketFactory) {
		this.clientSocketFactory = clientSocketFactory;
	}

	/**
	 * Set a custom RMI server socket factory to use for exporting the service.
	 * <p>Only needs to be specified when the client socket factory does not
	 * implement {@code java.rmi.server.RMIServerSocketFactory} already.
	 *
	 * @see #setClientSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see UnicastRemoteObject#exportObject(Remote, int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setServerSocketFactory(RMIServerSocketFactory serverSocketFactory) {
		this.serverSocketFactory = serverSocketFactory;
	}

	/**
	 * Specify the RMI registry to register the exported service with.
	 * Typically used in combination with RmiRegistryFactoryBean.
	 * <p>Alternatively, you can specify all registry properties locally.
	 * This exporter will then try to locate the specified registry,
	 * automatically creating a new local one if appropriate.
	 * <p>Default is a local registry at the default port (1099),
	 * created on the fly if necessary.
	 *
	 * @see RmiRegistryFactoryBean
	 * @see #setRegistryHost
	 * @see #setRegistryPort
	 * @see #setRegistryClientSocketFactory
	 * @see #setRegistryServerSocketFactory
	 */
	public void setRegistry(Registry registry) {
		this.registry = registry;
	}

	/**
	 * Set the host of the registry for the exported RMI service,
	 * i.e. {@code rmi://HOST:port/name}
	 * <p>Default is localhost.
	 */
	public void setRegistryHost(String registryHost) {
		this.registryHost = registryHost;
	}

	/**
	 * Set the port of the registry for the exported RMI service,
	 * i.e. {@code rmi://host:PORT/name}
	 * <p>Default is {@code Registry.REGISTRY_PORT} (1099).
	 *
	 * @see java.rmi.registry.Registry#REGISTRY_PORT
	 */
	public void setRegistryPort(int registryPort) {
		this.registryPort = registryPort;
	}

	/**
	 * Set a custom RMI client socket factory to use for the RMI registry.
	 * <p>If the given object also implements {@code java.rmi.server.RMIServerSocketFactory},
	 * it will automatically be registered as server socket factory too.
	 *
	 * @see #setRegistryServerSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see LocateRegistry#getRegistry(String, int, RMIClientSocketFactory)
	 */
	public void setRegistryClientSocketFactory(RMIClientSocketFactory registryClientSocketFactory) {
		this.registryClientSocketFactory = registryClientSocketFactory;
	}

	/**
	 * Set a custom RMI server socket factory to use for the RMI registry.
	 * <p>Only needs to be specified when the client socket factory does not
	 * implement {@code java.rmi.server.RMIServerSocketFactory} already.
	 *
	 * @see #setRegistryClientSocketFactory
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.server.RMIServerSocketFactory
	 * @see LocateRegistry#createRegistry(int, RMIClientSocketFactory, RMIServerSocketFactory)
	 */
	public void setRegistryServerSocketFactory(RMIServerSocketFactory registryServerSocketFactory) {
		this.registryServerSocketFactory = registryServerSocketFactory;
	}

	/**
	 * Set whether to always create the registry in-process,
	 * not attempting to locate an existing registry at the specified port.
	 * <p>Default is "false". Switch this flag to "true" in order to avoid
	 * the overhead of locating an existing registry when you always
	 * intend to create a new registry in any case.
	 */
	public void setAlwaysCreateRegistry(boolean alwaysCreateRegistry) {
		this.alwaysCreateRegistry = alwaysCreateRegistry;
	}

	/**
	 * Set whether to replace an existing binding in the RMI registry,
	 * that is, whether to simply override an existing binding with the
	 * specified service in case of a naming conflict in the registry.
	 * <p>Default is "true", assuming that an existing binding for this
	 * exporter's service name is an accidental leftover from a previous
	 * execution. Switch this to "false" to make the exporter fail in such
	 * a scenario, indicating that there was already an RMI object bound.
	 */
	public void setReplaceExistingBinding(boolean replaceExistingBinding) {
		this.replaceExistingBinding = replaceExistingBinding;
	}


	@Override
	public void afterPropertiesSet() throws RemoteException {
		prepare();
	}

	/**
	 * 初始化 此服务 导出程序，将服务注册为RMI对象。如果不存在RMI注册表，则在指定端口上创建 RMI 注册表。
	 * <p>
	 * RMI 发布服务的流程：
	 * 1. 验证 service 属性配置信息。验证配置的 {@link RmiServiceExporter#service} 属性。
	 * 2. 处理用户自定义的 SocketFactory 属性。在 RMIServiceExporter 中提供了 4 个套接字工厂配置，分别是{@link #clientSocketFactory},
	 * {@link #serverSocketFactory},和 {@link #registryClientSocketFactory},{@link #registryServerSocketFactory}。
	 * <p>
	 * registryClientSocketFactory 和 registryServerSocketFactory 用于主机与 RMI 服务器之间连接的创建。即当使用
	 * LocateRegistry.createRegistry(registryPort,clientSocketFactory,serverSocketFactory)方法创建 Registry 实例时，会在 RMI 主机
	 * 使用 serverSocketFactory 创建套接字等待连接，而服务端与 RMI 主机通信时，会使用 clientSocketFactory 创建套接字连接。
	 * 3. 根据配置参数 获取 Registry。
	 * 4. 构造对外发布到实例：创建对外发布的实例，当外界通过注册的服务名 调用响应的方法时，RMI 服务会将请求引入此类来处理。
	 * 5. 发布实例。在发布 RMI 服务的流程中，需要注意一下几个步骤：
	 * 5.1 获取 Registry；如果 RMI 注册主机与发布的服务在同一台主机上，可以使用 LocateRegistry.createRegistry(...) 创建 Registry
	 * 实例即可。若不在同一台主机上，则需要远程获取 Registry 实例：LocateRegistry#getRegistry(registryHost, registryPort,clientSocketFactory)。
	 * 5.2 初始化将要 暴露 的 服务实体对象；
	 * 5.3 RMI 服务激活调用。exportObject 暴露的服务对象 其实是被 {@link RmiInvocationWrapper}封装的，即当其他服务器调用 serviceName 的 RMI
	 * 服务时，Java 会封装其内部操作，直接将代码转向 {@link RmiInvocationWrapper#invoke(RemoteInvocation)}方法中。
	 * <p>
	 * Initialize this service exporter, registering the service as RMI object.
	 * <p>Creates an RMI registry on the specified port if none exists.
	 *
	 * @throws RemoteException if service registration failed
	 * @see #getRegistry(String, int, RMIClientSocketFactory, RMIServerSocketFactory) 获取 Registry。
	 * @see RmiBasedExporter#getObjectToExport() 初始化将要 暴露 的服务实现类对象。
	 * @see RmiInvocationWrapper#invoke(RemoteInvocation) RMI 服务激活调用。
	 */
	public void prepare() throws RemoteException {
		// 校验 service 属性是否配置
		checkService();

		// service 服务在注册表中 注册的名字
		if (this.serviceName == null) {
			throw new IllegalArgumentException("Property 'serviceName' is required");
		}

		// Check socket factories for exported object.
		/*
		如果用户 配置了 clientSocketFactory 或者 serverSocketFactory 的话，对它们进行处理。
		如果配置中的 clientSocketFactory ，实现了 RMIServerSocketFactory 接口，那么
		将会忽略配置中的 serverSocketFactory，而是用 clientSocketFactory 代替
		 */
		if (this.clientSocketFactory instanceof RMIServerSocketFactory) {
			this.serverSocketFactory = (RMIServerSocketFactory) this.clientSocketFactory;
		}

		// clientSocketFactory 和 serverSocketFactory 要么同时出现，要么都不出现。
		if ((this.clientSocketFactory != null && this.serverSocketFactory == null) ||
				(this.clientSocketFactory == null && this.serverSocketFactory != null)) {
			throw new IllegalArgumentException(
					"Both RMIClientSocketFactory and RMIServerSocketFactory or none required");
		}

		// Check socket factories for RMI registry.
		/*
		如果配置中的 registryClientSocketFactory 实现了 RMIServerSocketFactory 接口，那么
		将会忽略配置中的 registryServerSocketFactory，而是用 registryClientSocketFactory 代替
		 */
		if (this.registryClientSocketFactory instanceof RMIServerSocketFactory) {
			this.registryServerSocketFactory = (RMIServerSocketFactory) this.registryClientSocketFactory;
		}
		// 不允许 只配置了 registryServerSocketFactory 但是没有配置 registryClientSocketFactory
		if (this.registryClientSocketFactory == null && this.registryServerSocketFactory != null) {
			throw new IllegalArgumentException(
					"RMIServerSocketFactory without RMIClientSocketFactory for registry not supported");
		}

		this.createdRegistry = false;

		// Determine RMI registry to use.
		// 确定 RMI registry
		if (this.registry == null) {
			// ★★★ 获取或者创建 registry 服务注册器
			this.registry = getRegistry(this.registryHost, this.registryPort,
					this.registryClientSocketFactory, this.registryServerSocketFactory);
			this.createdRegistry = true;
		}

		// Initialize and cache exported object.
		// ★★★ 初始化以及缓存 暴露的 Object
		this.exportedObject = getObjectToExport();

		if (logger.isDebugEnabled()) {
			logger.debug("Binding service '" + this.serviceName + "' to RMI registry: " + this.registry);
		}

		// Export RMI object. 暴露 服务
		// 如果配置了 远程对象 调用的 客户端套接字工厂
		if (this.clientSocketFactory != null) {
			/*
			★★★
			使用 由给定 套接字工厂 指定的传送方式 暴露 远程对象，以便能够接收传入的调用。
			clientSocketFactory：进行 远程对象 调用的 客户端套接字工厂
			serverSocketFactory：接收 远程调用的 服务端套接字工厂
			 */
			UnicastRemoteObject.exportObject(
					this.exportedObject, this.servicePort, this.clientSocketFactory, this.serverSocketFactory);
		} else {
			// 没有配置远程对象调用的客户端套接字工厂
			UnicastRemoteObject.exportObject(this.exportedObject, this.servicePort);
		}

		// Bind RMI object to registry.
		try {
			if (this.replaceExistingBinding) {
				this.registry.rebind(this.serviceName, this.exportedObject);
			} else {
				// 将配置的 serviceName 绑定到 暴露的 服务
				this.registry.bind(this.serviceName, this.exportedObject);
			}
		} catch (AlreadyBoundException ex) {
			// Already an RMI object bound for the specified service name...
			unexportObjectSilently();
			throw new IllegalStateException(
					"Already an RMI object bound for name '" + this.serviceName + "': " + ex.toString());
		} catch (RemoteException ex) {
			// Registry binding failed: let's unexport the RMI object as well.
			unexportObjectSilently();
			throw ex;
		}
	}


	/**
	 * 找到或创建这个 Exporter 的 RMI 注册表 。Locate or create the RMI registry for this exporter.
	 *
	 * @param registryHost        the registry host to use (if this is specified,
	 *                            no implicit creation of a RMI registry will happen)
	 * @param registryPort        the registry port to use
	 * @param clientSocketFactory the RMI client socket factory for the registry (if any)
	 * @param serverSocketFactory the RMI server socket factory for the registry (if any)
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 */
	protected Registry getRegistry(String registryHost, int registryPort,
								   @Nullable RMIClientSocketFactory clientSocketFactory, @Nullable RMIServerSocketFactory serverSocketFactory)
			throws RemoteException {

		if (registryHost != null) {
			// Host explicitly specified: only lookup possible.
			if (logger.isDebugEnabled()) {
				logger.debug("Looking for RMI registry at port '" + registryPort + "' of host [" + registryHost + "]");
			}
			Registry reg = LocateRegistry.getRegistry(registryHost, registryPort, clientSocketFactory);
			testRegistry(reg);
			return reg;
		} else {
			return getRegistry(registryPort, clientSocketFactory, serverSocketFactory);
		}
	}

	/**
	 * Locate or create the RMI registry for this exporter.
	 *
	 * @param registryPort        the registry port to use
	 * @param clientSocketFactory the RMI client socket factory for the registry (if any)
	 * @param serverSocketFactory the RMI server socket factory for the registry (if any)
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 */
	protected Registry getRegistry(int registryPort,
								   @Nullable RMIClientSocketFactory clientSocketFactory, @Nullable RMIServerSocketFactory serverSocketFactory)
			throws RemoteException {

		if (clientSocketFactory != null) {
			if (this.alwaysCreateRegistry) {
				logger.debug("Creating new RMI registry");
				return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Looking for RMI registry at port '" + registryPort + "', using custom socket factory");
			}
			synchronized (LocateRegistry.class) {
				try {
					// Retrieve existing registry.
					Registry reg = LocateRegistry.getRegistry(null, registryPort, clientSocketFactory);
					testRegistry(reg);
					return reg;
				} catch (RemoteException ex) {
					logger.trace("RMI registry access threw exception", ex);
					logger.debug("Could not detect RMI registry - creating new one");
					// Assume no registry found -> create new one.
					return LocateRegistry.createRegistry(registryPort, clientSocketFactory, serverSocketFactory);
				}
			}
		} else {
			return getRegistry(registryPort);
		}
	}

	/**
	 * Locate or create the RMI registry for this exporter.
	 *
	 * @param registryPort the registry port to use
	 * @return the RMI registry
	 * @throws RemoteException if the registry couldn't be located or created
	 */
	protected Registry getRegistry(int registryPort) throws RemoteException {
		if (this.alwaysCreateRegistry) {
			logger.debug("Creating new RMI registry");
			return LocateRegistry.createRegistry(registryPort);
		}
		if (logger.isDebugEnabled()) {
			logger.debug("Looking for RMI registry at port '" + registryPort + "'");
		}
		synchronized (LocateRegistry.class) {
			try {
				// Retrieve existing registry.
				Registry reg = LocateRegistry.getRegistry(registryPort);
				testRegistry(reg);
				return reg;
			} catch (RemoteException ex) {
				logger.trace("RMI registry access threw exception", ex);
				logger.debug("Could not detect RMI registry - creating new one");
				// Assume no registry found -> create new one.
				return LocateRegistry.createRegistry(registryPort);
			}
		}
	}

	/**
	 * Test the given RMI registry, calling some operation on it to
	 * check whether it is still active.
	 * <p>Default implementation calls {@code Registry.list()}.
	 *
	 * @param registry the RMI registry to test
	 * @throws RemoteException if thrown by registry methods
	 * @see java.rmi.registry.Registry#list()
	 */
	protected void testRegistry(Registry registry) throws RemoteException {
		registry.list();
	}


	/**
	 * Unbind the RMI service from the registry on bean factory shutdown.
	 */
	@Override
	public void destroy() throws RemoteException {
		if (logger.isDebugEnabled()) {
			logger.debug("Unbinding RMI service '" + this.serviceName +
					"' from registry" + (this.createdRegistry ? (" at port '" + this.registryPort + "'") : ""));
		}
		try {
			this.registry.unbind(this.serviceName);
		} catch (NotBoundException ex) {
			if (logger.isInfoEnabled()) {
				logger.info("RMI service '" + this.serviceName + "' is not bound to registry" +
						(this.createdRegistry ? (" at port '" + this.registryPort + "' anymore") : ""), ex);
			}
		} finally {
			unexportObjectSilently();
		}
	}

	/**
	 * Unexport the registered RMI object, logging any exception that arises.
	 */
	private void unexportObjectSilently() {
		try {
			UnicastRemoteObject.unexportObject(this.exportedObject, true);
		} catch (NoSuchObjectException ex) {
			if (logger.isInfoEnabled()) {
				logger.info("RMI object for service '" + this.serviceName + "' is not exported anymore", ex);
			}
		}
	}

}
