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

package org.springframework.jdbc.core;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.SQLWarningException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

/**
 * JDBC(Java Data Base Connectivity java数据库连接) 是一种用于执行 SQL 语句的 Java API。
 * 使用JDBC连接数据库的流程及其原理如下：
 * <p>
 * 1. 在开发环境中加载指定数据库的驱动程序。
 * 2. 在Java 程序中加载驱动程序。可以通过 “Class.forName("指定数据库的驱动程序")”的方式来将驱动添加到开发环境中。
 * 3. 创建数据连接对象。通过 {@link DriverManager} 类创建数据库连接对象 {@link Connection}。DriverManager 类作用于Java 程序和JDBC
 * 驱动程序之间，用于检查所加载的驱动是否可以建立连接，然后通过它的 {@link DriverManager#getConnection(java.lang.String, java.util.Properties)} 方法
 * 根据数据库的 URL、用户名、密码，创建一个JDBC Connection 对象。URL：协议名+IP+端口+数据库名称。
 * Connection connectMySQL= DriverManager.getConnection("jdbc:mysql://localhost:3306/my_user","root","root");
 * 4. 创建 {@link Statement}对象，用来执行 静态SQL 语句并返回它所生成的 结果对象
 * {@link Connection#createStatement()}：Statement statement=connectMySQL.createStatement().
 * 5. 调用 Statement 对象的相关方法，执行相应的 SQL 语句。例如：{@link Statement#executeUpdate(String)}可以新增和修改数据，
 * {@link Statement#executeQuery(String)} 可以查询数据，并将数据封装后返回 {@link ResultSet}对象。ResultSet 表示执行查询数据库后
 * 返回的 数据的集合，ResultSet 对象具有可以指向当前数据行的指针。通过该对象的 next() 方法，使得指针指向下一行，然后将数据按照列号或者字段名取出。
 * 若 next() 方法返回 null，表示下一行中没有数据存在。ResultSet resultSet=statement.executeQuery("select * from user");
 * 6. 关闭数据库连接。使用完数据库或者不需要访问数据库时，通过 {@link Connection#close()}方法及时关闭数据库连接。
 * <p>
 * 本类的源码中 阅读的方法有：
 * 1. 数据修改操作：{@link #update(java.lang.String, java.lang.Object[], int[])}
 * 2. 数据修改操作的核心数据处理语句：{@link #update(org.springframework.jdbc.core.PreparedStatementCreator, org.springframework.jdbc.core.PreparedStatementSetter)}
 * 3. 基础 SQL 执行方法，核心：{@link #execute(org.springframework.jdbc.core.PreparedStatementCreator, org.springframework.jdbc.core.PreparedStatementCallback<T>)}
 * 4. 有参数的查询集合：{@link #query(java.lang.String, java.lang.Object[], int[], org.springframework.jdbc.core.RowMapper<T>)}
 * 5. 有参数的查询语句执行方法：{@link #query(org.springframework.jdbc.core.PreparedStatementCreator, org.springframework.jdbc.core.PreparedStatementSetter, org.springframework.jdbc.core.ResultSetExtractor<T>)}
 * 6. 无参数的查询：{@link #query(java.lang.String, org.springframework.jdbc.core.RowMapper<T>)}
 * 7. 无参数查询的实际执行方法：{@link #query(java.lang.String, org.springframework.jdbc.core.ResultSetExtractor<T>)}
 * 8. 其他查询方法：{@link #queryForObject(java.lang.String, java.lang.Class<T>)}
 *
 * <b>This is the central class in the JDBC core package.</b>
 * It simplifies the use of JDBC and helps to avoid common errors.
 * It executes core JDBC workflow, leaving application code to provide SQL
 * and extract results. This class executes SQL queries or updates, initiating
 * iteration over ResultSets and catching JDBC exceptions and translating
 * them to the generic, more informative exception hierarchy defined in the
 * {@code org.springframework.dao} package.
 *
 * <p>Code using this class need only implement callback interfaces, giving
 * them a clearly defined contract. The {@link PreparedStatementCreator} callback
 * interface creates a prepared statement given a Connection, providing SQL and
 * any necessary parameters. The {@link ResultSetExtractor} interface extracts
 * values from a ResultSet. See also {@link PreparedStatementSetter} and
 * {@link RowMapper} for two popular alternative callback interfaces.
 *
 * <p>Can be used within a service implementation via direct instantiation
 * with a DataSource reference, or get prepared in an application context
 * and given to services as bean reference. Note: The DataSource should
 * always be configured as a bean in the application context, in the first case
 * given to the service directly, in the second case to the prepared template.
 *
 * <p>Because this class is parameterizable by the callback interfaces and
 * the {@link org.springframework.jdbc.support.SQLExceptionTranslator}
 * interface, there should be no need to subclass it.
 *
 * <p>All SQL operations performed by this class are logged at debug level,
 * using "org.springframework.jdbc.core.JdbcTemplate" as log category.
 *
 * <p><b>NOTE: An instance of this class is thread-safe once configured.</b>
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Thomas Risberg
 * @see PreparedStatementCreator
 * @see PreparedStatementSetter
 * @see CallableStatementCreator
 * @see PreparedStatementCallback
 * @see CallableStatementCallback
 * @see ResultSetExtractor
 * @see RowCallbackHandler
 * @see RowMapper
 * @see org.springframework.jdbc.support.SQLExceptionTranslator
 * @since May 3, 2001
 */
public class JdbcTemplate extends JdbcAccessor implements JdbcOperations {

	/**
	 * 返回集合的 前缀
	 */
	private static final String RETURN_RESULT_SET_PREFIX = "#result-set-";

	/**
	 * 返回 更新 条数的 前缀
	 */
	private static final String RETURN_UPDATE_COUNT_PREFIX = "#update-count-";


	/**
	 * If this variable is false, we will throw exceptions on SQL warnings.
	 * 当这个参数是 false 时，遇到 SQL 警告我们会抛出异常。
	 */
	private boolean ignoreWarnings = true;

	/**
	 * 如果这个变量设置为 非负值，那么它将被用于 设置 查询查询处理语句 的 fetchSize 属性。
	 * If this variable is set to a non-negative value, it will be used for setting the
	 * fetchSize property on statements used for query processing.
	 */
	private int fetchSize = -1;

	/**
	 * 如果这个变量设置为 非负值，那么它将被用于 设置 查询处理语句 的 maxRows 属性。
	 * If this variable is set to a non-negative value, it will be used for setting the
	 * maxRows property on statements used for query processing.
	 */
	private int maxRows = -1;

	/**
	 * 如果这个变量设置为 非负值，那么它将被用于 设置 查询处理语句 的 queryTimeout 属性。
	 * If this variable is set to a non-negative value, it will be used for setting the
	 * queryTimeout property on statements used for query processing.
	 */
	private int queryTimeout = -1;

	/**
	 * 如果此变量设置为 true，则所有 执行语句的结果 都将忽略检查。
	 * 这可以用来避免一些旧的 oracle jdbc 驱动程序（如10.1.0.2）中的bug。
	 * If this variable is set to true, then all results checking will be bypassed for any
	 * callable statement processing. This can be used to avoid a bug in some older Oracle
	 * JDBC drivers like 10.1.0.2.
	 */
	private boolean skipResultsProcessing = false;

	/**
	 * 如果此变量设置为 true，则没有响应的 Sql OutParameter 声明的 存储过程的执行结果将会被忽略。
	 * 除非变量 skipUndeclaredResults 设置为 true，否则 将处理其它所有的结果。
	 * If this variable is set to true then all results from a stored procedure call
	 * that don't have a corresponding SqlOutParameter declaration will be bypassed.
	 * All other results processing will be take place unless the variable
	 * {@code skipResultsProcessing} is set to {@code true}.
	 */
	private boolean skipUndeclaredResults = false;

	/**
	 * 如果此变量设置为 true，则可执行语句的执行结果将会是 大小写敏感的 Map 集合。
	 * If this variable is set to true then execution of a CallableStatement will return
	 * the results in a Map that uses case insensitive names for the parameters.
	 */
	private boolean resultsMapCaseInsensitive = false;


	/**
	 * Construct a new JdbcTemplate for bean usage.
	 * <p>Note: The DataSource has to be set before using the instance.
	 * <p>注意：使用 JdbcTemplate 实例前，需要设置好 DataSource 数据源。</p>
	 *
	 * @see #setDataSource
	 */
	public JdbcTemplate() {
	}

	/**
	 * 构造一个 JdbcTemplate 实例，赋予一个 获取连接的 数据源。
	 * <p>
	 * {@link DataSource}通过参数注入，DataSource 的创建过程是 引入第三方的连接池，是整个数据库操作的基础，其中封装了整个数据库的连接信息。
	 * <p>
	 * Construct a new JdbcTemplate, given a DataSource to obtain connections from.
	 * <p>Note: This will not trigger initialization of the exception translator.
	 *
	 * @param dataSource the JDBC DataSource to obtain connections from
	 */
	public JdbcTemplate(DataSource dataSource) {
		setDataSource(dataSource);
		afterPropertiesSet();
	}

	/**
	 * 构造一个 JdbcTemplate 实例，赋予一个 获取连接的 数据源。
	 * <p>
	 * Construct a new JdbcTemplate, given a DataSource to obtain connections from.
	 * <p>Note: Depending on the "lazyInit" flag, initialization of the exception translator
	 * will be triggered.
	 *
	 * @param dataSource the JDBC DataSource to obtain connections from
	 * @param lazyInit   whether to lazily initialize the SQLExceptionTranslator
	 */
	public JdbcTemplate(DataSource dataSource, boolean lazyInit) {
		setDataSource(dataSource);
		setLazyInit(lazyInit);
		afterPropertiesSet();
	}


	/**
	 * 设置 是否忽略 SQL 的警告。
	 * 默认为 "true"，记录所有警告。设置为 "false"，警告时，JdbcTemplate 将会抛出 SQLWarningException 。
	 * <p>
	 * Set whether or not we want to ignore SQLWarnings.
	 * <p>Default is "true", swallowing and logging all warnings. Switch this flag
	 * to "false" to make the JdbcTemplate throw an SQLWarningException instead.
	 *
	 * @see java.sql.SQLWarning
	 * @see org.springframework.jdbc.SQLWarningException
	 * @see #handleWarnings
	 */
	public void setIgnoreWarnings(boolean ignoreWarnings) {
		this.ignoreWarnings = ignoreWarnings;
	}

	/**
	 * Return whether or not we ignore SQLWarnings.
	 */
	public boolean isIgnoreWarnings() {
		return this.ignoreWarnings;
	}

	/**
	 * 设置 JdbcTemplate 的 fetch size属性，这对处理 大型结果集 非常重要：将此值设置为高于默认值，
	 * 将以内存消耗为代价提高处理速度；将此值设置为低于默认值，可以避免传输应用程序永远不会读取的行数据。、
	 * 默认值是 -1.
	 * <p>
	 * Set the fetch size for this JdbcTemplate. This is important for processing large
	 * result sets: Setting this higher than the default value will increase processing
	 * speed at the cost of memory consumption; setting this lower can avoid transferring
	 * row data that will never be read by the application.
	 * <p>Default is -1, indicating to use the JDBC driver's default configuration
	 * (i.e. to not pass a specific fetch size setting on to the driver).
	 * <p>Note: As of 4.3, negative values other than -1 will get passed on to the
	 * driver, since e.g. MySQL supports special behavior for {@code Integer.MIN_VALUE}.
	 *
	 * @see java.sql.Statement#setFetchSize
	 */
	public void setFetchSize(int fetchSize) {
		this.fetchSize = fetchSize;
	}

	/**
	 * Return the fetch size specified for this JdbcTemplate.
	 */
	public int getFetchSize() {
		return this.fetchSize;
	}

	/**
	 * 设置 JdbcTemplate 的最大 行数。这对于处理大型结果集的子集非常重要，
	 * 如果我们对整个结果不感兴趣，那么就避免在数据库 或 JDBC 驱动程序 中 读取和保存整个结果集。
	 * （例如，在执行可能返回大量匹配项的搜索时。）
	 * 默认值是 -1，表示使用JDBC驱动程序的默认配置。即 不将特定的max rows设置传递给驱动程序。
	 * <p>
	 * Set the maximum number of rows for this JdbcTemplate. This is important for
	 * processing subsets of large result sets, avoiding to read and hold the entire
	 * result set in the database or in the JDBC driver if we're never interested in
	 * the entire result in the first place (for example, when performing searches
	 * that might return a large number of matches).
	 * <p>Default is -1, indicating to use the JDBC driver's default configuration
	 * (i.e. to not pass a specific max rows setting on to the driver).
	 * <p>Note: As of 4.3, negative values other than -1 will get passed on to the
	 * driver, in sync with {@link #setFetchSize}'s support for special MySQL values.
	 *
	 * @see java.sql.Statement#setMaxRows
	 */
	public void setMaxRows(int maxRows) {
		this.maxRows = maxRows;
	}

	/**
	 * Return the maximum number of rows specified for this JdbcTemplate.
	 */
	public int getMaxRows() {
		return this.maxRows;
	}

	/**
	 * 设置 JdbcTemplate 执行语句的 超时时间。默认是 -1，表明使用 JDBC 驱动的默认值。
	 * 即 驱动器上不设置 超时时间。
	 * <p>
	 * Set the query timeout for statements that this JdbcTemplate executes.
	 * <p>Default is -1, indicating to use the JDBC driver's default
	 * (i.e. to not pass a specific query timeout setting on the driver).
	 * <p>Note: Any timeout specified here will be overridden by the remaining
	 * transaction timeout when executing within a transaction that has a
	 * timeout specified at the transaction level.
	 *
	 * @see java.sql.Statement#setQueryTimeout
	 */
	public void setQueryTimeout(int queryTimeout) {
		this.queryTimeout = queryTimeout;
	}

	/**
	 * Return the query timeout for statements that this JdbcTemplate executes.
	 */
	public int getQueryTimeout() {
		return this.queryTimeout;
	}

	/**
	 * 设置 是否进行 执行结果的处理 。当我们知道没有结果被传回时，可以用来优化可调用语句的处理 — out参数
	 * 的处理仍将进行。这可以用来避免一些旧的 oracle jdbc 驱动程序（如10.1.0.2）中的 bug。
	 * Set whether results processing should be skipped. Can be used to optimize callable
	 * statement processing when we know that no results are being passed back - the processing
	 * of out parameter will still take place. This can be used to avoid a bug in some older
	 * Oracle JDBC drivers like 10.1.0.2.
	 */
	public void setSkipResultsProcessing(boolean skipResultsProcessing) {
		this.skipResultsProcessing = skipResultsProcessing;
	}

	/**
	 * Return whether results processing should be skipped.
	 */
	public boolean isSkipResultsProcessing() {
		return this.skipResultsProcessing;
	}

	/**
	 * 设置是否应跳过未声明的结果。
	 * Set whether undeclared results should be skipped.
	 */
	public void setSkipUndeclaredResults(boolean skipUndeclaredResults) {
		this.skipUndeclaredResults = skipUndeclaredResults;
	}

	/**
	 * Return whether undeclared results should be skipped.
	 */
	public boolean isSkipUndeclaredResults() {
		return this.skipUndeclaredResults;
	}

	/**
	 * Set whether execution of a CallableStatement will return the results in a Map
	 * that uses case insensitive names for the parameters.
	 */
	public void setResultsMapCaseInsensitive(boolean resultsMapCaseInsensitive) {
		this.resultsMapCaseInsensitive = resultsMapCaseInsensitive;
	}

	/**
	 * Return whether execution of a CallableStatement will return the results in a Map
	 * that uses case insensitive names for the parameters.
	 */
	public boolean isResultsMapCaseInsensitive() {
		return this.resultsMapCaseInsensitive;
	}


	//-------------------------------------------------------------------------
	// Methods dealing with a plain java.sql.Connection
	//-------------------------------------------------------------------------

	@Override
	@Nullable
	public <T> T execute(ConnectionCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		try {
			// Create close-suppressing Connection proxy, also preparing returned Statements.
			Connection conToUse = createConnectionProxy(con);
			return action.doInConnection(conToUse);
		} catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			String sql = getSql(action);
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("ConnectionCallback", sql, ex);
		} finally {
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	/**
	 * Create a close-suppressing proxy for the given JDBC Connection.
	 * Called by the {@code execute} method.
	 * <p>The proxy also prepares returned JDBC Statements, applying
	 * statement settings such as fetch size, max rows, and query timeout.
	 *
	 * @param con the JDBC Connection to create a proxy for
	 * @return the Connection proxy
	 * @see java.sql.Connection#close()
	 * @see #execute(ConnectionCallback)
	 * @see #applyStatementSettings
	 */
	protected Connection createConnectionProxy(Connection con) {
		return (Connection) Proxy.newProxyInstance(
				ConnectionProxy.class.getClassLoader(),
				new Class<?>[]{ConnectionProxy.class},
				new CloseSuppressingInvocationHandler(con));
	}


	//-------------------------------------------------------------------------
	// Methods dealing with static SQL (java.sql.Statement)
	//-------------------------------------------------------------------------

	/**
	 * 基础 SQL 执行方法。数据库操作的 核心入口。
	 * <p>
	 * 类似于 有参的 核心执行方法：{@link #execute(PreparedStatementCreator, PreparedStatementCallback)}。
	 * 但是不同的是，Statement 的创建。这里直接使用 stmt = con.createStatement(); 来创建了 {@link Statement}，而有参的
	 * 是调用 {@link PreparedStatementCreator#createPreparedStatement(Connection)} 来创建了 {@link PreparedStatement}。
	 * <p>
	 * 那么 Statement 和 PreparedStatement 有什么不同呢？
	 * 1. PreparedStatement 实例中包含 已编译的 SQL 语句，其中的 SQL 可具有 一个或多个 IN 参数，IN 参数的值在 SQL 语句创建时未被指定。
	 * 相反的，该语句为每个 IN 参数保留一个 “？”作为占位符，每个“？”的值必须在语句执行前，通过适当的 setXXX() 方法来提供。
	 * 2. 由于 PreparedStatement 对象已经 预编译过了，所以执行速度要比 Statement 快。因此多次执行的 SQL 语句 经常创建为 PreparedStatement
	 * 对象，以提高 执行效率。
	 * <p>
	 * 作为 Statement 的子类，PreparedStatement 继承了 Statement 的所有功能。另外还添加了一整套方法，来设置发送给数据库以取代 IN 参数占位符的值。
	 * 同时，三种方法 execute、executeQuery、executeUpdate 已被更改以使之不再需要参数。这些 重载方法 的 Statement 入参形式 不能使用 PreparedStatement 传参。
	 *
	 * @param action a callback that specifies the action。指定 操作行为的 回调。
	 * @param <T>    泛型
	 * @return 结果
	 * @throws DataAccessException 异常
	 */
	@Override
	@Nullable
	public <T> T execute(StatementCallback<T> action) throws DataAccessException {
		Assert.notNull(action, "Callback object must not be null");

		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		Statement stmt = null;
		try {
			stmt = con.createStatement();
			applyStatementSettings(stmt);
			T result = action.doInStatement(stmt);
			handleWarnings(stmt);
			return result;
		} catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			String sql = getSql(action);
			JdbcUtils.closeStatement(stmt);
			stmt = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("StatementCallback", sql, ex);
		} finally {
			JdbcUtils.closeStatement(stmt);
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	@Override
	public void execute(final String sql) throws DataAccessException {
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL statement [" + sql + "]");
		}

		/**
		 * Callback to execute the statement.
		 */
		class ExecuteStatementCallback implements StatementCallback<Object>, SqlProvider {
			@Override
			@Nullable
			public Object doInStatement(Statement stmt) throws SQLException {
				stmt.execute(sql);
				return null;
			}

			@Override
			public String getSql() {
				return sql;
			}
		}

		execute(new ExecuteStatementCallback());
	}

	/**
	 * 查询集合：无参数
	 *
	 * @param sql the SQL query to execute 执行的 SQL 查询语句。
	 * @param rse a callback that will extract all rows of results。数据库字段与实体类的映射关系。
	 * @param <T> 泛型
	 * @return 查询结果
	 * @throws DataAccessException 异常
	 */
	@Override
	@Nullable
	public <T> T query(final String sql, final ResultSetExtractor<T> rse) throws DataAccessException {
		Assert.notNull(sql, "SQL must not be null");
		Assert.notNull(rse, "ResultSetExtractor must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL query [" + sql + "]");
		}

		/**
		 * Callback to execute the query.
		 */
		class QueryStatementCallback implements StatementCallback<T>, SqlProvider {
			@Override
			@Nullable
			public T doInStatement(Statement stmt) throws SQLException {
				ResultSet rs = null;
				try {
					rs = stmt.executeQuery(sql);
					// org.springframework.jdbc.core.RowMapperResultSetExtractor#extractData
					return rse.extractData(rs);
				} finally {
					JdbcUtils.closeResultSet(rs);
				}
			}

			@Override
			public String getSql() {
				return sql;
			}
		}

		return execute(new QueryStatementCallback());
	}

	@Override
	public void query(String sql, RowCallbackHandler rch) throws DataAccessException {
		query(sql, new RowCallbackHandlerResultSetExtractor(rch));
	}

	/**
	 * 查询集合：无参数
	 *
	 * @param sql       the SQL query to execute 执行的 SQL 查询语句。
	 * @param rowMapper a callback that will map one object per row。数据库字段与实体类的映射关系。
	 * @param <T>       泛型
	 * @return 查询结果
	 * @throws DataAccessException 异常
	 */
	@Override
	public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public Map<String, Object> queryForMap(String sql) throws DataAccessException {
		return result(queryForObject(sql, getColumnMapRowMapper()));
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, RowMapper<T> rowMapper) throws DataAccessException {
		List<T> results = query(sql, rowMapper);
		return DataAccessUtils.nullableSingleResult(results);
	}

	/**
	 * Spring 不仅提供 {@link #query(String, Object[], int[], RowMapper)} 方法，还在此基础上做了 封装，提供不同的 query 方法。如下：
	 * 最大的不同 是对于 RowMapper 的使用，{@link SingleColumnRowMapper#mapRow(ResultSet, int)}
	 *
	 * @param sql          the SQL query to execute 将被执行的SQL语句
	 * @param requiredType the type that the result object is expected to match 实体类 映射类型。
	 * @param <T>          泛型
	 * @return 结果
	 * @throws DataAccessException 异常
	 */
	@Override
	@Nullable
	public <T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException {
		return queryForObject(sql, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public <T> List<T> queryForList(String sql, Class<T> elementType) throws DataAccessException {
		return query(sql, getSingleColumnRowMapper(elementType));
	}

	@Override
	public List<Map<String, Object>> queryForList(String sql) throws DataAccessException {
		return query(sql, getColumnMapRowMapper());
	}

	@Override
	public SqlRowSet queryForRowSet(String sql) throws DataAccessException {
		return result(query(sql, new SqlRowSetResultSetExtractor()));
	}

	@Override
	public int update(final String sql) throws DataAccessException {
		Assert.notNull(sql, "SQL must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL update [" + sql + "]");
		}

		/**
		 * Callback to execute the update statement.
		 */
		class UpdateStatementCallback implements StatementCallback<Integer>, SqlProvider {
			@Override
			public Integer doInStatement(Statement stmt) throws SQLException {
				int rows = stmt.executeUpdate(sql);
				if (logger.isTraceEnabled()) {
					logger.trace("SQL update affected " + rows + " rows");
				}
				return rows;
			}

			@Override
			public String getSql() {
				return sql;
			}
		}

		return updateCount(execute(new UpdateStatementCallback()));
	}

	@Override
	public int[] batchUpdate(final String... sql) throws DataAccessException {
		Assert.notEmpty(sql, "SQL array must not be empty");
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL batch update of " + sql.length + " statements");
		}

		/**
		 * Callback to execute the batch update.
		 */
		class BatchUpdateStatementCallback implements StatementCallback<int[]>, SqlProvider {

			@Nullable
			private String currSql;

			@Override
			public int[] doInStatement(Statement stmt) throws SQLException, DataAccessException {
				int[] rowsAffected = new int[sql.length];
				if (JdbcUtils.supportsBatchUpdates(stmt.getConnection())) {
					for (String sqlStmt : sql) {
						this.currSql = appendSql(this.currSql, sqlStmt);
						stmt.addBatch(sqlStmt);
					}
					try {
						rowsAffected = stmt.executeBatch();
					} catch (BatchUpdateException ex) {
						String batchExceptionSql = null;
						for (int i = 0; i < ex.getUpdateCounts().length; i++) {
							if (ex.getUpdateCounts()[i] == Statement.EXECUTE_FAILED) {
								batchExceptionSql = appendSql(batchExceptionSql, sql[i]);
							}
						}
						if (StringUtils.hasLength(batchExceptionSql)) {
							this.currSql = batchExceptionSql;
						}
						throw ex;
					}
				} else {
					for (int i = 0; i < sql.length; i++) {
						this.currSql = sql[i];
						if (!stmt.execute(sql[i])) {
							rowsAffected[i] = stmt.getUpdateCount();
						} else {
							throw new InvalidDataAccessApiUsageException("Invalid batch SQL statement: " + sql[i]);
						}
					}
				}
				return rowsAffected;
			}

			private String appendSql(@Nullable String sql, String statement) {
				return (StringUtils.hasLength(sql) ? sql + "; " + statement : statement);
			}

			@Override
			@Nullable
			public String getSql() {
				return this.currSql;
			}
		}

		int[] result = execute(new BatchUpdateStatementCallback());
		Assert.state(result != null, "No update counts");
		return result;
	}


	//-------------------------------------------------------------------------
	// Methods dealing with prepared statements
	//-------------------------------------------------------------------------

	/**
	 * 基础 SQL 执行方法。数据库操作的 核心入口。
	 *
	 * @param psc    a callback that creates a PreparedStatement given a Connection
	 * @param action a callback that specifies the action。个性化回调参数。
	 * @param <T>    泛型
	 * @return 影响条数
	 * @throws DataAccessException 异常
	 */
	@Override
	@Nullable
	public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action)
			throws DataAccessException {

		Assert.notNull(psc, "PreparedStatementCreator must not be null");
		Assert.notNull(action, "Callback object must not be null");
		if (logger.isDebugEnabled()) {
			String sql = getSql(psc);
			logger.debug("Executing prepared SQL statement" + (sql != null ? " [" + sql + "]" : ""));
		}

		// ★★★★★ 获取 数据库 连接
		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		PreparedStatement ps = null;
		try {
			// 获取 应用设定的参数
			ps = psc.createPreparedStatement(con);
			// ★★★★★ 应用 用户设定的 输入参数。
			applyStatementSettings(ps);
			// ★★★★★ 调用回调参数。处理一些通用方法外的 个性化处理，也就是 PreparedStatementCallback 类型的参数的 doInPreparedStatement 方法的回调。
			T result = action.doInPreparedStatement(ps);
			// ★★★★★ 处理警告
			handleWarnings(ps);
			return result;
		} catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			// 尽早释放连接，以避免在 异常转换器 尚未初始化 的情况下出现 连接池死锁的可能。
			if (psc instanceof ParameterDisposer) {
				((ParameterDisposer) psc).cleanupParameters();
			}
			String sql = getSql(psc);
			psc = null;
			JdbcUtils.closeStatement(ps);
			ps = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("PreparedStatementCallback", sql, ex);
		} finally {
			if (psc instanceof ParameterDisposer) {
				((ParameterDisposer) psc).cleanupParameters();
			}
			JdbcUtils.closeStatement(ps);
			// 释放 数据库的连接 资源
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	@Override
	@Nullable
	public <T> T execute(String sql, PreparedStatementCallback<T> action) throws DataAccessException {
		return execute(new SimplePreparedStatementCreator(sql), action);
	}

	/**
	 * 使用 prepared statement 进行查询，允许是 PreparedStatementCreator 和 PreparedStatementSetter 类型。
	 * 大多数查询方法都使用此方法，但应用程序代码将始终使用 Creator 或 Setter 。
	 * <p>
	 * 封装返回的结果数据 POJO： rse.extractData {@link RowMapperResultSetExtractor#extractData(java.sql.ResultSet)}
	 * <p>
	 * Query using a prepared statement, allowing for a PreparedStatementCreator
	 * and a PreparedStatementSetter. Most other query methods use this method,
	 * but application code will always work with either a creator or a setter.
	 *
	 * @param psc a callback that creates a PreparedStatement given a Connection
	 * @param pss a callback that knows how to set values on the prepared statement.
	 *            If this is {@code null}, the SQL will be assumed to contain no bind parameters.
	 * @param rse a callback that will extract results
	 * @return an arbitrary result object, as returned by the ResultSetExtractor
	 * @throws DataAccessException if there is any problem
	 */
	@Nullable
	public <T> T query(
			PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss, final ResultSetExtractor<T> rse)
			throws DataAccessException {

		Assert.notNull(rse, "ResultSetExtractor must not be null");
		logger.debug("Executing prepared SQL query");

		return execute(psc, new PreparedStatementCallback<T>() {
			@Override
			@Nullable
			public T doInPreparedStatement(PreparedStatement ps) throws SQLException {
				ResultSet rs = null;
				try {
					if (pss != null) {
						pss.setValues(ps);
					}
					// 执行 查询
					rs = ps.executeQuery();
					// 额外 数据封装。负责将 查询结果 进行封装，并转换至 POJO。rse即 ResultSetExtractor，其中封装了自定义的 RowMapper映射关系。
					return rse.extractData(rs);
				} finally {
					JdbcUtils.closeResultSet(rs);
					if (pss instanceof ParameterDisposer) {
						((ParameterDisposer) pss).cleanupParameters();
					}
				}
			}
		});
	}

	@Override
	@Nullable
	public <T> T query(PreparedStatementCreator psc, ResultSetExtractor<T> rse) throws DataAccessException {
		return query(psc, null, rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, @Nullable PreparedStatementSetter pss, ResultSetExtractor<T> rse) throws DataAccessException {
		return query(new SimplePreparedStatementCreator(sql), pss, rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, Object[] args, int[] argTypes, ResultSetExtractor<T> rse) throws DataAccessException {
		// 封装 参数
		return query(sql, newArgTypePreparedStatementSetter(args, argTypes), rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, @Nullable Object[] args, ResultSetExtractor<T> rse) throws DataAccessException {
		return query(sql, newArgPreparedStatementSetter(args), rse);
	}

	@Override
	@Nullable
	public <T> T query(String sql, ResultSetExtractor<T> rse, @Nullable Object... args) throws DataAccessException {
		return query(sql, newArgPreparedStatementSetter(args), rse);
	}

	@Override
	public void query(PreparedStatementCreator psc, RowCallbackHandler rch) throws DataAccessException {
		query(psc, new RowCallbackHandlerResultSetExtractor(rch));
	}

	@Override
	public void query(String sql, @Nullable PreparedStatementSetter pss, RowCallbackHandler rch) throws DataAccessException {
		query(sql, pss, new RowCallbackHandlerResultSetExtractor(rch));
	}

	@Override
	public void query(String sql, Object[] args, int[] argTypes, RowCallbackHandler rch) throws DataAccessException {
		query(sql, newArgTypePreparedStatementSetter(args, argTypes), rch);
	}

	@Override
	public void query(String sql, @Nullable Object[] args, RowCallbackHandler rch) throws DataAccessException {
		query(sql, newArgPreparedStatementSetter(args), rch);
	}

	@Override
	public void query(String sql, RowCallbackHandler rch, @Nullable Object... args) throws DataAccessException {
		query(sql, newArgPreparedStatementSetter(args), rch);
	}

	@Override
	public <T> List<T> query(PreparedStatementCreator psc, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(psc, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, @Nullable PreparedStatementSetter pss, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, pss, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	/**
	 * 查询集合：有参数
	 *
	 * @param sql       the SQL query to execute sql执行语句
	 * @param args      arguments to bind to the query 参数
	 * @param argTypes  the SQL types of the arguments 参数类型
	 *                  (constants from {@code java.sql.Types})
	 * @param rowMapper a callback that will map one object per row 查询结果与实体类对象的映射规则
	 * @param <T>       泛型
	 * @return 结果
	 * @throws DataAccessException 异常
	 */
	@Override
	public <T> List<T> query(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, args, argTypes, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, @Nullable Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
		return result(query(sql, args, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	public <T> List<T> query(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException {
		return result(query(sql, args, new RowMapperResultSetExtractor<>(rowMapper)));
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper)
			throws DataAccessException {

		List<T> results = query(sql, args, argTypes, new RowMapperResultSetExtractor<>(rowMapper, 1));
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, @Nullable Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
		List<T> results = query(sql, args, new RowMapperResultSetExtractor<>(rowMapper, 1));
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException {
		List<T> results = query(sql, args, new RowMapperResultSetExtractor<>(rowMapper, 1));
		return DataAccessUtils.nullableSingleResult(results);
	}

	@Override
	@Nullable
	public <T> T queryForObject(String sql, Object[] args, int[] argTypes, Class<T> requiredType)
			throws DataAccessException {

		return queryForObject(sql, args, argTypes, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public <T> T queryForObject(String sql, @Nullable Object[] args, Class<T> requiredType) throws DataAccessException {
		return queryForObject(sql, args, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public <T> T queryForObject(String sql, Class<T> requiredType, @Nullable Object... args) throws DataAccessException {
		return queryForObject(sql, args, getSingleColumnRowMapper(requiredType));
	}

	@Override
	public Map<String, Object> queryForMap(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		return result(queryForObject(sql, args, argTypes, getColumnMapRowMapper()));
	}

	@Override
	public Map<String, Object> queryForMap(String sql, @Nullable Object... args) throws DataAccessException {
		return result(queryForObject(sql, args, getColumnMapRowMapper()));
	}

	@Override
	public <T> List<T> queryForList(String sql, Object[] args, int[] argTypes, Class<T> elementType) throws DataAccessException {
		return query(sql, args, argTypes, getSingleColumnRowMapper(elementType));
	}

	@Override
	public <T> List<T> queryForList(String sql, @Nullable Object[] args, Class<T> elementType) throws DataAccessException {
		return query(sql, args, getSingleColumnRowMapper(elementType));
	}

	@Override
	public <T> List<T> queryForList(String sql, Class<T> elementType, @Nullable Object... args) throws DataAccessException {
		return query(sql, args, getSingleColumnRowMapper(elementType));
	}

	@Override
	public List<Map<String, Object>> queryForList(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		return query(sql, args, argTypes, getColumnMapRowMapper());
	}

	@Override
	public List<Map<String, Object>> queryForList(String sql, @Nullable Object... args) throws DataAccessException {
		return query(sql, args, getColumnMapRowMapper());
	}

	@Override
	public SqlRowSet queryForRowSet(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		return result(query(sql, args, argTypes, new SqlRowSetResultSetExtractor()));
	}

	@Override
	public SqlRowSet queryForRowSet(String sql, @Nullable Object... args) throws DataAccessException {
		return result(query(sql, args, new SqlRowSetResultSetExtractor()));
	}

	/**
	 * 数据修改操作：核心数据处理语句。
	 *
	 * @param psc 封装的 Sql 语句
	 * @param pss 封装的 参数
	 * @return 影响条数
	 * @throws DataAccessException 异常
	 */
	protected int update(final PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss)
			throws DataAccessException {

		logger.debug("Executing prepared SQL update");


		/*execute(psc, new PreparedStatementCallback<Integer>() {
			@Override
			public Integer doInPreparedStatement(PreparedStatement ps) throws SQLException, DataAccessException {
				try {
					if (pss != null) {
						pss.setValues(ps);
					}
					int rows = ps.executeUpdate();
					if (logger.isTraceEnabled()) {
						logger.trace("SQL update affected " + rows + " rows");
					}
					return rows;
				} finally {
					if (pss instanceof ParameterDisposer) {
						((ParameterDisposer) pss).cleanupParameters();
					}
				}
			}
		});*/
		// 调用 基础执行方法 execute。lambda表达式省略了 回调函数接口 PreparedStatementCallback#doInPreparedStatement。
		return updateCount(execute(psc, ps -> {
			try {
				if (pss != null) {
					pss.setValues(ps);
				}
				int rows = ps.executeUpdate();
				if (logger.isTraceEnabled()) {
					logger.trace("SQL update affected " + rows + " rows");
				}
				return rows;
			} finally {
				if (pss instanceof ParameterDisposer) {
					((ParameterDisposer) pss).cleanupParameters();
				}
			}
		}));
	}

	@Override
	public int update(PreparedStatementCreator psc) throws DataAccessException {
		return update(psc, (PreparedStatementSetter) null);
	}

	@Override
	public int update(final PreparedStatementCreator psc, final KeyHolder generatedKeyHolder)
			throws DataAccessException {

		Assert.notNull(generatedKeyHolder, "KeyHolder must not be null");
		logger.debug("Executing SQL update and returning generated keys");

		return updateCount(execute(psc, ps -> {
			int rows = ps.executeUpdate();
			List<Map<String, Object>> generatedKeys = generatedKeyHolder.getKeyList();
			generatedKeys.clear();
			ResultSet keys = ps.getGeneratedKeys();
			if (keys != null) {
				try {
					RowMapperResultSetExtractor<Map<String, Object>> rse =
							new RowMapperResultSetExtractor<>(getColumnMapRowMapper(), 1);
					generatedKeys.addAll(result(rse.extractData(keys)));
				} finally {
					JdbcUtils.closeResultSet(keys);
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("SQL update affected " + rows + " rows and returned " + generatedKeys.size() + " keys");
			}
			return rows;
		}));
	}

	/**
	 * 数据修改操作：封装 Sql 语句。
	 *
	 * @param sql the SQL containing bind parameters。SQL 语句。
	 * @param pss helper that sets bind parameters. If this is {@code null}
	 *            we run an update with static SQL.参数绑定。
	 * @return 影响条数
	 * @throws DataAccessException 异常
	 */
	@Override
	public int update(String sql, @Nullable PreparedStatementSetter pss) throws DataAccessException {
		return update(new SimplePreparedStatementCreator(sql), pss);
	}

	/**
	 * 1. 封装参数：本方法。
	 * 2. 封装 Sql 语句：{@link #update(String, PreparedStatementSetter)}
	 *
	 * @param sql      the SQL containing bind parameters。 SQL 语句。
	 * @param args     arguments to bind to the query。参数。
	 * @param argTypes the SQL types of the arguments。参数类型。
	 *                 (constants from {@code java.sql.Types})
	 * @return 影响条数
	 * @throws DataAccessException 异常
	 */
	@Override
	public int update(String sql, Object[] args, int[] argTypes) throws DataAccessException {
		// 封装参数
		return update(sql, newArgTypePreparedStatementSetter(args, argTypes));
	}

	/**
	 * 数据修改操作：更新或保存。
	 * <p>
	 * 封装参数：本方法。封装 Sql 语句：{@link #update(String, PreparedStatementSetter)}.
	 *
	 * @param sql  the SQL containing bind parameters
	 * @param args arguments to bind to the query
	 *             (leaving it to the PreparedStatement to guess the corresponding SQL type);
	 *             may also contain {@link SqlParameterValue} objects which indicate not
	 *             only the argument value but also the SQL type and optionally the scale
	 * @return 影响条数
	 * @throws DataAccessException 异常
	 */
	@Override
	public int update(String sql, @Nullable Object... args) throws DataAccessException {
		return update(sql, newArgPreparedStatementSetter(args));
	}

	@Override
	public int[] batchUpdate(String sql, final BatchPreparedStatementSetter pss) throws DataAccessException {
		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL batch update [" + sql + "]");
		}

		int[] result = execute(sql, (PreparedStatementCallback<int[]>) ps -> {
			try {
				int batchSize = pss.getBatchSize();
				InterruptibleBatchPreparedStatementSetter ipss =
						(pss instanceof InterruptibleBatchPreparedStatementSetter ?
								(InterruptibleBatchPreparedStatementSetter) pss : null);
				if (JdbcUtils.supportsBatchUpdates(ps.getConnection())) {
					for (int i = 0; i < batchSize; i++) {
						pss.setValues(ps, i);
						if (ipss != null && ipss.isBatchExhausted(i)) {
							break;
						}
						ps.addBatch();
					}
					return ps.executeBatch();
				} else {
					List<Integer> rowsAffected = new ArrayList<>();
					for (int i = 0; i < batchSize; i++) {
						pss.setValues(ps, i);
						if (ipss != null && ipss.isBatchExhausted(i)) {
							break;
						}
						rowsAffected.add(ps.executeUpdate());
					}
					int[] rowsAffectedArray = new int[rowsAffected.size()];
					for (int i = 0; i < rowsAffectedArray.length; i++) {
						rowsAffectedArray[i] = rowsAffected.get(i);
					}
					return rowsAffectedArray;
				}
			} finally {
				if (pss instanceof ParameterDisposer) {
					((ParameterDisposer) pss).cleanupParameters();
				}
			}
		});

		Assert.state(result != null, "No result array");
		return result;
	}

	@Override
	public int[] batchUpdate(String sql, List<Object[]> batchArgs) throws DataAccessException {
		return batchUpdate(sql, batchArgs, new int[0]);
	}

	@Override
	public int[] batchUpdate(String sql, List<Object[]> batchArgs, final int[] argTypes) throws DataAccessException {
		if (batchArgs.isEmpty()) {
			return new int[0];
		}

		return batchUpdate(
				sql,
				new BatchPreparedStatementSetter() {
					@Override
					public void setValues(PreparedStatement ps, int i) throws SQLException {
						Object[] values = batchArgs.get(i);
						int colIndex = 0;
						for (Object value : values) {
							colIndex++;
							if (value instanceof SqlParameterValue) {
								SqlParameterValue paramValue = (SqlParameterValue) value;
								StatementCreatorUtils.setParameterValue(ps, colIndex, paramValue, paramValue.getValue());
							} else {
								int colType;
								if (argTypes.length < colIndex) {
									colType = SqlTypeValue.TYPE_UNKNOWN;
								} else {
									colType = argTypes[colIndex - 1];
								}
								StatementCreatorUtils.setParameterValue(ps, colIndex, colType, value);
							}
						}
					}

					@Override
					public int getBatchSize() {
						return batchArgs.size();
					}
				});
	}

	@Override
	public <T> int[][] batchUpdate(String sql, final Collection<T> batchArgs, final int batchSize,
								   final ParameterizedPreparedStatementSetter<T> pss) throws DataAccessException {

		if (logger.isDebugEnabled()) {
			logger.debug("Executing SQL batch update [" + sql + "] with a batch size of " + batchSize);
		}
		int[][] result = execute(sql, (PreparedStatementCallback<int[][]>) ps -> {
			List<int[]> rowsAffected = new ArrayList<>();
			try {
				boolean batchSupported = JdbcUtils.supportsBatchUpdates(ps.getConnection());
				int n = 0;
				for (T obj : batchArgs) {
					pss.setValues(ps, obj);
					n++;
					if (batchSupported) {
						ps.addBatch();
						if (n % batchSize == 0 || n == batchArgs.size()) {
							if (logger.isTraceEnabled()) {
								int batchIdx = (n % batchSize == 0) ? n / batchSize : (n / batchSize) + 1;
								int items = n - ((n % batchSize == 0) ? n / batchSize - 1 : (n / batchSize)) * batchSize;
								logger.trace("Sending SQL batch update #" + batchIdx + " with " + items + " items");
							}
							rowsAffected.add(ps.executeBatch());
						}
					} else {
						int i = ps.executeUpdate();
						rowsAffected.add(new int[]{i});
					}
				}
				int[][] result1 = new int[rowsAffected.size()][];
				for (int i = 0; i < result1.length; i++) {
					result1[i] = rowsAffected.get(i);
				}
				return result1;
			} finally {
				if (pss instanceof ParameterDisposer) {
					((ParameterDisposer) pss).cleanupParameters();
				}
			}
		});

		Assert.state(result != null, "No result array");
		return result;
	}


	//-------------------------------------------------------------------------
	// Methods dealing with callable statements
	//-------------------------------------------------------------------------

	@Override
	@Nullable
	public <T> T execute(CallableStatementCreator csc, CallableStatementCallback<T> action)
			throws DataAccessException {

		Assert.notNull(csc, "CallableStatementCreator must not be null");
		Assert.notNull(action, "Callback object must not be null");
		if (logger.isDebugEnabled()) {
			String sql = getSql(csc);
			logger.debug("Calling stored procedure" + (sql != null ? " [" + sql + "]" : ""));
		}

		Connection con = DataSourceUtils.getConnection(obtainDataSource());
		CallableStatement cs = null;
		try {
			cs = csc.createCallableStatement(con);
			applyStatementSettings(cs);
			T result = action.doInCallableStatement(cs);
			handleWarnings(cs);
			return result;
		} catch (SQLException ex) {
			// Release Connection early, to avoid potential connection pool deadlock
			// in the case when the exception translator hasn't been initialized yet.
			if (csc instanceof ParameterDisposer) {
				((ParameterDisposer) csc).cleanupParameters();
			}
			String sql = getSql(csc);
			csc = null;
			JdbcUtils.closeStatement(cs);
			cs = null;
			DataSourceUtils.releaseConnection(con, getDataSource());
			con = null;
			throw translateException("CallableStatementCallback", sql, ex);
		} finally {
			if (csc instanceof ParameterDisposer) {
				((ParameterDisposer) csc).cleanupParameters();
			}
			JdbcUtils.closeStatement(cs);
			DataSourceUtils.releaseConnection(con, getDataSource());
		}
	}

	@Override
	@Nullable
	public <T> T execute(String callString, CallableStatementCallback<T> action) throws DataAccessException {
		return execute(new SimpleCallableStatementCreator(callString), action);
	}

	@Override
	public Map<String, Object> call(CallableStatementCreator csc, List<SqlParameter> declaredParameters)
			throws DataAccessException {

		final List<SqlParameter> updateCountParameters = new ArrayList<>();
		final List<SqlParameter> resultSetParameters = new ArrayList<>();
		final List<SqlParameter> callParameters = new ArrayList<>();

		for (SqlParameter parameter : declaredParameters) {
			if (parameter.isResultsParameter()) {
				if (parameter instanceof SqlReturnResultSet) {
					resultSetParameters.add(parameter);
				} else {
					updateCountParameters.add(parameter);
				}
			} else {
				callParameters.add(parameter);
			}
		}

		Map<String, Object> result = execute(csc, cs -> {
			boolean retVal = cs.execute();
			int updateCount = cs.getUpdateCount();
			if (logger.isTraceEnabled()) {
				logger.trace("CallableStatement.execute() returned '" + retVal + "'");
				logger.trace("CallableStatement.getUpdateCount() returned " + updateCount);
			}
			Map<String, Object> resultsMap = createResultsMap();
			if (retVal || updateCount != -1) {
				resultsMap.putAll(extractReturnedResults(cs, updateCountParameters, resultSetParameters, updateCount));
			}
			resultsMap.putAll(extractOutputParameters(cs, callParameters));
			return resultsMap;
		});

		Assert.state(result != null, "No result map");
		return result;
	}

	/**
	 * Extract returned ResultSets from the completed stored procedure.
	 *
	 * @param cs                    a JDBC wrapper for the stored procedure
	 * @param updateCountParameters the parameter list of declared update count parameters for the stored procedure
	 * @param resultSetParameters   the parameter list of declared resultSet parameters for the stored procedure
	 * @return a Map that contains returned results
	 */
	protected Map<String, Object> extractReturnedResults(CallableStatement cs,
														 @Nullable List<SqlParameter> updateCountParameters, @Nullable List<SqlParameter> resultSetParameters,
														 int updateCount) throws SQLException {

		Map<String, Object> results = new LinkedHashMap<>(4);
		int rsIndex = 0;
		int updateIndex = 0;
		boolean moreResults;
		if (!this.skipResultsProcessing) {
			do {
				if (updateCount == -1) {
					if (resultSetParameters != null && resultSetParameters.size() > rsIndex) {
						SqlReturnResultSet declaredRsParam = (SqlReturnResultSet) resultSetParameters.get(rsIndex);
						results.putAll(processResultSet(cs.getResultSet(), declaredRsParam));
						rsIndex++;
					} else {
						if (!this.skipUndeclaredResults) {
							String rsName = RETURN_RESULT_SET_PREFIX + (rsIndex + 1);
							SqlReturnResultSet undeclaredRsParam = new SqlReturnResultSet(rsName, getColumnMapRowMapper());
							if (logger.isTraceEnabled()) {
								logger.trace("Added default SqlReturnResultSet parameter named '" + rsName + "'");
							}
							results.putAll(processResultSet(cs.getResultSet(), undeclaredRsParam));
							rsIndex++;
						}
					}
				} else {
					if (updateCountParameters != null && updateCountParameters.size() > updateIndex) {
						SqlReturnUpdateCount ucParam = (SqlReturnUpdateCount) updateCountParameters.get(updateIndex);
						String declaredUcName = ucParam.getName();
						results.put(declaredUcName, updateCount);
						updateIndex++;
					} else {
						if (!this.skipUndeclaredResults) {
							String undeclaredName = RETURN_UPDATE_COUNT_PREFIX + (updateIndex + 1);
							if (logger.isTraceEnabled()) {
								logger.trace("Added default SqlReturnUpdateCount parameter named '" + undeclaredName + "'");
							}
							results.put(undeclaredName, updateCount);
							updateIndex++;
						}
					}
				}
				moreResults = cs.getMoreResults();
				updateCount = cs.getUpdateCount();
				if (logger.isTraceEnabled()) {
					logger.trace("CallableStatement.getUpdateCount() returned " + updateCount);
				}
			}
			while (moreResults || updateCount != -1);
		}
		return results;
	}

	/**
	 * Extract output parameters from the completed stored procedure.
	 *
	 * @param cs         the JDBC wrapper for the stored procedure
	 * @param parameters parameter list for the stored procedure
	 * @return a Map that contains returned results
	 */
	protected Map<String, Object> extractOutputParameters(CallableStatement cs, List<SqlParameter> parameters)
			throws SQLException {

		Map<String, Object> results = new LinkedHashMap<>(parameters.size());
		int sqlColIndex = 1;
		for (SqlParameter param : parameters) {
			if (param instanceof SqlOutParameter) {
				SqlOutParameter outParam = (SqlOutParameter) param;
				Assert.state(outParam.getName() != null, "Anonymous parameters not allowed");
				SqlReturnType returnType = outParam.getSqlReturnType();
				if (returnType != null) {
					Object out = returnType.getTypeValue(cs, sqlColIndex, outParam.getSqlType(), outParam.getTypeName());
					results.put(outParam.getName(), out);
				} else {
					Object out = cs.getObject(sqlColIndex);
					if (out instanceof ResultSet) {
						if (outParam.isResultSetSupported()) {
							results.putAll(processResultSet((ResultSet) out, outParam));
						} else {
							String rsName = outParam.getName();
							SqlReturnResultSet rsParam = new SqlReturnResultSet(rsName, getColumnMapRowMapper());
							results.putAll(processResultSet((ResultSet) out, rsParam));
							if (logger.isTraceEnabled()) {
								logger.trace("Added default SqlReturnResultSet parameter named '" + rsName + "'");
							}
						}
					} else {
						results.put(outParam.getName(), out);
					}
				}
			}
			if (!(param.isResultsParameter())) {
				sqlColIndex++;
			}
		}
		return results;
	}

	/**
	 * Process the given ResultSet from a stored procedure.
	 *
	 * @param rs    the ResultSet to process
	 * @param param the corresponding stored procedure parameter
	 * @return a Map that contains returned results
	 */
	protected Map<String, Object> processResultSet(
			@Nullable ResultSet rs, ResultSetSupportingSqlParameter param) throws SQLException {

		if (rs != null) {
			try {
				if (param.getRowMapper() != null) {
					RowMapper<?> rowMapper = param.getRowMapper();
					Object data = (new RowMapperResultSetExtractor<>(rowMapper)).extractData(rs);
					return Collections.singletonMap(param.getName(), data);
				} else if (param.getRowCallbackHandler() != null) {
					RowCallbackHandler rch = param.getRowCallbackHandler();
					(new RowCallbackHandlerResultSetExtractor(rch)).extractData(rs);
					return Collections.singletonMap(param.getName(),
							"ResultSet returned from stored procedure was processed");
				} else if (param.getResultSetExtractor() != null) {
					Object data = param.getResultSetExtractor().extractData(rs);
					return Collections.singletonMap(param.getName(), data);
				}
			} finally {
				JdbcUtils.closeResultSet(rs);
			}
		}
		return Collections.emptyMap();
	}


	//-------------------------------------------------------------------------
	// Implementation hooks and helper methods
	//-------------------------------------------------------------------------

	/**
	 * Create a new RowMapper for reading columns as key-value pairs.
	 *
	 * @return the RowMapper to use
	 * @see ColumnMapRowMapper
	 */
	protected RowMapper<Map<String, Object>> getColumnMapRowMapper() {
		return new ColumnMapRowMapper();
	}

	/**
	 * Create a new RowMapper for reading result objects from a single column.
	 *
	 * @param requiredType the type that each result object is expected to match
	 * @return the RowMapper to use
	 * @see SingleColumnRowMapper
	 */
	protected <T> RowMapper<T> getSingleColumnRowMapper(Class<T> requiredType) {
		return new SingleColumnRowMapper<>(requiredType);
	}

	/**
	 * Create a Map instance to be used as the results map.
	 * <p>If {@link #resultsMapCaseInsensitive} has been set to true,
	 * a {@link LinkedCaseInsensitiveMap} will be created; otherwise, a
	 * {@link LinkedHashMap} will be created.
	 *
	 * @return the results Map instance
	 * @see #setResultsMapCaseInsensitive
	 * @see #isResultsMapCaseInsensitive
	 */
	protected Map<String, Object> createResultsMap() {
		if (isResultsMapCaseInsensitive()) {
			return new LinkedCaseInsensitiveMap<>();
		} else {
			return new LinkedHashMap<>();
		}
	}

	/**
	 * 应用 用户设定的 输入参数。
	 * <p>
	 * 准备给定的JDBC语句（或PreparedStatement或CallableStatement），向其中应用
	 * 诸如fetch size、max rows和query timeout之类的语句设置。
	 * <p>
	 * setFetchSize：最主要是为了减少 网络交互次数设计的。访问 ResultSet 时，如果他每次只从服务器读取一行数据，则会产生大量的开销。
	 * setFetchSize 的意思是 当调用 rs.next 时，ResultSet 会一次性从服务器上取得多少行数据回来，这样在下次 rs.next时，它可以直接
	 * 从内存中获取数据而不需要网络交互，提高了效率。 这个设置可能会被 某些 JDBC 驱动忽略，而且设置过大，会造成内存使用的上升。
	 * <p>
	 * setMaxRows：将此 Statement 对象生成的所有 ResultSet 对象可以包含的最大行数限制 设置为给定数值。
	 * <p>
	 * Prepare the given JDBC Statement (or PreparedStatement or CallableStatement),
	 * applying statement settings such as fetch size, max rows, and query timeout.
	 *
	 * @param stmt the JDBC Statement to prepare
	 * @throws SQLException if thrown by JDBC API
	 * @see #setFetchSize
	 * @see #setMaxRows
	 * @see #setQueryTimeout
	 * @see org.springframework.jdbc.datasource.DataSourceUtils#applyTransactionTimeout
	 */
	protected void applyStatementSettings(Statement stmt) throws SQLException {
		int fetchSize = getFetchSize();
		if (fetchSize != -1) {
			stmt.setFetchSize(fetchSize);
		}
		int maxRows = getMaxRows();
		if (maxRows != -1) {
			stmt.setMaxRows(maxRows);
		}
		DataSourceUtils.applyTimeout(stmt, getDataSource(), getQueryTimeout());
	}

	/**
	 * Create a new arg-based PreparedStatementSetter using the args passed in.
	 * <p>By default, we'll create an {@link ArgumentPreparedStatementSetter}.
	 * This method allows for the creation to be overridden by subclasses.
	 *
	 * @param args object array with arguments
	 * @return the new PreparedStatementSetter to use
	 */
	protected PreparedStatementSetter newArgPreparedStatementSetter(@Nullable Object[] args) {
		return new ArgumentPreparedStatementSetter(args);
	}

	/**
	 * 使用 传入的参数和类型 创建新的 基于参数类型的 PreparedStatementSetter。此方法允许子类重写创建。
	 * <p>
	 * 我们默认 会创建一个 ArgumentTypePreparedStatementSetter 类。{@link ArgumentTypePreparedStatementSetter#setValues(PreparedStatement)}.
	 * <p>
	 * Create a new arg-type-based PreparedStatementSetter using the args and types passed in.
	 * <p>By default, we'll create an {@link ArgumentTypePreparedStatementSetter}.
	 * This method allows for the creation to be overridden by subclasses.
	 *
	 * @param args     object array with arguments
	 * @param argTypes int array of SQLTypes for the associated arguments
	 * @return the new PreparedStatementSetter to use
	 */
	protected PreparedStatementSetter newArgTypePreparedStatementSetter(Object[] args, int[] argTypes) {
		return new ArgumentTypePreparedStatementSetter(args, argTypes);
	}

	/**
	 * 处理警告。
	 * 如果不忽略警告，则抛出 SQLWarningException，否则 在调试级别 记录警告。
	 * <p>
	 * {@link SQLWarning} 提供关于数据库访问警告信息的异常。这些警告直接链接到 导致报告警告的方法 所在的对象。
	 * 警告可以从 Connection、Statement 和 ResultSet 对象中获得。试图在已经关闭的连接上获取警告 将导致抛出异常。
	 * 类似的，视图在已经关闭的结果集上获取警告，也将导致抛出异常。注意关闭语句时还会关闭它可能生成的结果集。
	 * <p>
	 * 什么情况下会产生警告而不是异常？例如最常见的警告，DataTruncation：直接继承自 SQLWarning，由于某种原因 意外地
	 * 截断数据值时，会以 DataTruncation 警告形式报告异常。
	 * <p>
	 * 对于警告的处理方式并不是直接抛出异常，出现警告很可能会出现数据错误，但是并不一定会影响程序执行，所以用户可以自己设置处理警告的方式，
	 * 如默认的是忽略警告，当出现警告时只打印警告日志，而另一种方式是直接抛出异常。
	 *
	 * <p>
	 * Throw an SQLWarningException if we're not ignoring warnings,
	 * otherwise log the warnings at debug level.
	 *
	 * @param stmt the current JDBC statement
	 * @throws SQLWarningException if not ignoring warnings
	 * @see org.springframework.jdbc.SQLWarningException
	 */
	protected void handleWarnings(Statement stmt) throws SQLException {
		// 当设置为 忽略警告时 只尝试 打印日志。
		if (isIgnoreWarnings()) {
			if (logger.isDebugEnabled()) {
				// 日志开启的情况下，打印日志。
				SQLWarning warningToLog = stmt.getWarnings();
				while (warningToLog != null) {
					logger.debug("SQLWarning ignored: SQL state '" + warningToLog.getSQLState() + "', error code '" +
							warningToLog.getErrorCode() + "', message [" + warningToLog.getMessage() + "]");
					warningToLog = warningToLog.getNextWarning();
				}
			}
		} else {
			// 否则，如果有警告，则会抛出 SQLWarningException 异常。
			handleWarnings(stmt.getWarnings());
		}
	}

	/**
	 * Throw an SQLWarningException if encountering an actual warning.
	 *
	 * @param warning the warnings object from the current statement.
	 *                May be {@code null}, in which case this method does nothing.
	 * @throws SQLWarningException in case of an actual warning to be raised
	 */
	protected void handleWarnings(@Nullable SQLWarning warning) throws SQLWarningException {
		if (warning != null) {
			throw new SQLWarningException("Warning not ignored", warning);
		}
	}

	/**
	 * Translate the given {@link SQLException} into a generic {@link DataAccessException}.
	 *
	 * @param task readable text describing the task being attempted
	 * @param sql  the SQL query or update that caused the problem (may be {@code null})
	 * @param ex   the offending {@code SQLException}
	 * @return a DataAccessException wrapping the {@code SQLException} (never {@code null})
	 * @see #getExceptionTranslator()
	 * @since 5.0
	 */
	protected DataAccessException translateException(String task, @Nullable String sql, SQLException ex) {
		DataAccessException dae = getExceptionTranslator().translate(task, sql, ex);
		return (dae != null ? dae : new UncategorizedSQLException(task, sql, ex));
	}


	/**
	 * Determine SQL from potential provider object.
	 *
	 * @param sqlProvider object which is potentially an SqlProvider
	 * @return the SQL string, or {@code null} if not known
	 * @see SqlProvider
	 */
	@Nullable
	private static String getSql(Object sqlProvider) {
		if (sqlProvider instanceof SqlProvider) {
			return ((SqlProvider) sqlProvider).getSql();
		} else {
			return null;
		}
	}

	private static <T> T result(@Nullable T result) {
		Assert.state(result != null, "No result");
		return result;
	}

	private static int updateCount(@Nullable Integer result) {
		Assert.state(result != null, "No update count");
		return result;
	}


	/**
	 * Invocation handler that suppresses close calls on JDBC Connections.
	 * Also prepares returned Statement (Prepared/CallbackStatement) objects.
	 *
	 * @see java.sql.Connection#close()
	 */
	private class CloseSuppressingInvocationHandler implements InvocationHandler {

		private final Connection target;

		public CloseSuppressingInvocationHandler(Connection target) {
			this.target = target;
		}

		@Override
		@Nullable
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			// Invocation on ConnectionProxy interface coming in...

			if (method.getName().equals("equals")) {
				// Only consider equal when proxies are identical.
				return (proxy == args[0]);
			} else if (method.getName().equals("hashCode")) {
				// Use hashCode of PersistenceManager proxy.
				return System.identityHashCode(proxy);
			} else if (method.getName().equals("unwrap")) {
				if (((Class<?>) args[0]).isInstance(proxy)) {
					return proxy;
				}
			} else if (method.getName().equals("isWrapperFor")) {
				if (((Class<?>) args[0]).isInstance(proxy)) {
					return true;
				}
			} else if (method.getName().equals("close")) {
				// Handle close method: suppress, not valid.
				return null;
			} else if (method.getName().equals("isClosed")) {
				return false;
			} else if (method.getName().equals("getTargetConnection")) {
				// Handle getTargetConnection method: return underlying Connection.
				return this.target;
			}

			// Invoke method on target Connection.
			try {
				Object retVal = method.invoke(this.target, args);

				// If return value is a JDBC Statement, apply statement settings
				// (fetch size, max rows, transaction timeout).
				if (retVal instanceof Statement) {
					applyStatementSettings(((Statement) retVal));
				}

				return retVal;
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Simple adapter for PreparedStatementCreator, allowing to use a plain SQL statement.
	 */
	private static class SimplePreparedStatementCreator implements PreparedStatementCreator, SqlProvider {

		private final String sql;

		public SimplePreparedStatementCreator(String sql) {
			Assert.notNull(sql, "SQL must not be null");
			this.sql = sql;
		}

		@Override
		public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
			return con.prepareStatement(this.sql);
		}

		@Override
		public String getSql() {
			return this.sql;
		}
	}


	/**
	 * Simple adapter for CallableStatementCreator, allowing to use a plain SQL statement.
	 */
	private static class SimpleCallableStatementCreator implements CallableStatementCreator, SqlProvider {

		private final String callString;

		public SimpleCallableStatementCreator(String callString) {
			Assert.notNull(callString, "Call string must not be null");
			this.callString = callString;
		}

		@Override
		public CallableStatement createCallableStatement(Connection con) throws SQLException {
			return con.prepareCall(this.callString);
		}

		@Override
		public String getSql() {
			return this.callString;
		}
	}


	/**
	 * Adapter to enable use of a RowCallbackHandler inside a ResultSetExtractor.
	 * <p>Uses a regular ResultSet, so we have to be careful when using it:
	 * We don't use it for navigating since this could lead to unpredictable consequences.
	 */
	private static class RowCallbackHandlerResultSetExtractor implements ResultSetExtractor<Object> {

		private final RowCallbackHandler rch;

		public RowCallbackHandlerResultSetExtractor(RowCallbackHandler rch) {
			this.rch = rch;
		}

		@Override
		@Nullable
		public Object extractData(ResultSet rs) throws SQLException {
			while (rs.next()) {
				this.rch.processRow(rs);
			}
			return null;
		}
	}

}
