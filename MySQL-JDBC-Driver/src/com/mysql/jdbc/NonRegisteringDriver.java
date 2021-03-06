/*
 Copyright  2002-2007 MySQL AB, 2008 Sun Microsystems
 All rights reserved. Use is subject to license terms.

 This program is free software; you can redistribute it and/or modify
 it under the terms of version 2 of the GNU General Public License as
 published by the Free Software Foundation.

 There are special exceptions to the terms and conditions of the GPL
 as it is applied to this software. View the full text of the
 exception in file EXCEPTIONS-CONNECTOR-J in the directory of this
 software distribution.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA



 */
package com.mysql.jdbc;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Logger;
/**
 * The Java SQL framework allows for multiple database drivers. Each driver
 * should supply a class that implements the Driver interface
 * 
 * <p>
 * The DriverManager will try to load as many drivers as it can find and then
 * for any given connection request, it will ask each driver in turn to try to
 * connect to the target URL.
 * </p>
 * 
 * <p>
 * It is strongly recommended that each Driver class should be small and
 * standalone so that the Driver class can be loaded and queried without
 * bringing in vast quantities of supporting code.
 * </p>
 * 
 * <p>
 * When a Driver class is loaded, it should create an instance of itself and
 * register it with the DriverManager. This means that a user can load and
 * register a driver by doing Class.forName("foo.bah.Driver")
 * </p>
 * 
 * @author Mark Matthews
 * @version $Id: NonRegisteringDriver.java,v 1.1.2.1 2005/05/13 18:58:38
 *          mmatthews Exp $
 * 
 * @see org.gjt.mm.mysql.Connection
 * @see java.sql.Driver
 */
public class NonRegisteringDriver implements java.sql.Driver {
	private static my.Debug DEBUG = new my.Debug(my.Debug.Driver);//我加上的

	private static final String REPLICATION_URL_PREFIX = "jdbc:mysql:replication://";

	private static final String URL_PREFIX = "jdbc:mysql://";

	//用这个url等同于"jdbc:mysql://"
	//只不过在建立Socket时用这个SocketFactory:
	//com.mysql.management.driverlaunched.ServerLauncherSocketFactory(这个类实际不存在)
	private static final String MXJ_URL_PREFIX = "jdbc:mysql:mxj://";

	private static final String LOADBALANCE_URL_PREFIX = "jdbc:mysql:loadbalance://";

	/**
	 * Key used to retreive the database value from the properties instance
	 * passed to the driver.
	 */
	public static final String DBNAME_PROPERTY_KEY = "DBNAME";

	/** Should the driver generate debugging output? */
	public static final boolean DEBUG2 = false; //我把DEBUG改成DEBUG2

	/** Index for hostname coming out of parseHostPortPair(). */
	//当解析“localhost:3306”这样的串时会得到一个长度为2的数组，
	//主机名就在0号位置处，HOST_NAME_INDEX就是下标(下面的PORT_NUMBER_INDEX类似)
	public final static int HOST_NAME_INDEX = 0;

	/**
	 * Key used to retreive the hostname value from the properties instance
	 * passed to the driver.
	 */
	public static final String HOST_PROPERTY_KEY = "HOST";

	//url的分隔符"//.../"中间可以有多个主机名:端口对(或ip)，用逗号分隔
	//这个字段就是用来记住有多少个这样的“主机名:端口对”
	public static final String NUM_HOSTS_PROPERTY_KEY = "NUM_HOSTS";
	
	/**
	 * Key used to retreive the password value from the properties instance
	 * passed to the driver.
	 */
	public static final String PASSWORD_PROPERTY_KEY = "password";

	/** Index for port # coming out of parseHostPortPair(). */
	public final static int PORT_NUMBER_INDEX = 1;

	/**
	 * Key used to retreive the port number value from the properties instance
	 * passed to the driver.
	 */
	public static final String PORT_PROPERTY_KEY = "PORT";

	//可以自定义一个实现com.mysql.jdbc.ConnectionPropertiesTransform接口的类，
	//然后在url中加入这个属性值，如propertiesTransform=MyConnectionPropertiesTransform
	//这样就可以对所有的属性值进行最后的转换调整
	public static final String PROPERTIES_TRANSFORM_KEY = "propertiesTransform";

	/** Should the driver generate method-call traces? */
	public static final boolean TRACE = false;

	//可以用这个属性指定一个本地的配置文件，
	//然后所以的参数可以从本地配置文件中取
	public static final String USE_CONFIG_PROPERTY_KEY = "useConfigs";

	/**
	 * Key used to retreive the username value from the properties instance
	 * passed to the driver.
	 */
	public static final String USER_PROPERTY_KEY = "user";

	/**
	 * Gets the drivers major version number
	 * 
	 * @return the drivers major version number
	 */
	static int getMajorVersionInternal() { //实际编译打包后的jar是5
		return safeIntParse("@MYSQL_CJ_MAJOR_VERSION@"); //$NON-NLS-1$
	}

	/**
	 * Get the drivers minor version number
	 * 
	 * @return the drivers minor version number
	 */
	static int getMinorVersionInternal() { //实际编译打包后的jar是1
		return safeIntParse("@MYSQL_CJ_MINOR_VERSION@"); //$NON-NLS-1$
	}

	/**
	 * Parses hostPortPair in the form of [host][:port] into an array, with the
	 * element of index HOST_NAME_INDEX being the host (or null if not
	 * specified), and the element of index PORT_NUMBER_INDEX being the port (or
	 * null if not specified).
	 * 
	 * @param hostPortPair
	 *            host and port in form of of [host][:port]
	 * 
	 * @return array containing host and port as Strings
	 * 
	 * @throws SQLException
	 *             if a parse error occurs
	 */
	protected static String[] parseHostPortPair(String hostPortPair)
			throws SQLException {
		int portIndex = hostPortPair.indexOf(":"); //$NON-NLS-1$

		String[] splitValues = new String[2];

		String hostname = null;

		if (portIndex != -1) {
			//如"//localhost:/"指定了":"，但没有端口号
			if ((portIndex + 1) < hostPortPair.length()) {
				String portAsString = hostPortPair.substring(portIndex + 1);
				hostname = hostPortPair.substring(0, portIndex);

				splitValues[HOST_NAME_INDEX] = hostname;

				splitValues[PORT_NUMBER_INDEX] = portAsString;
			} else {
				throw SQLError.createSQLException(Messages
						.getString("NonRegisteringDriver.37"), //$NON-NLS-1$
						SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
			}
		} else {
			splitValues[HOST_NAME_INDEX] = hostPortPair;
			splitValues[PORT_NUMBER_INDEX] = null;
		}

		return splitValues;
	}

	private static int safeIntParse(String intAsString) {
		try {
			return Integer.parseInt(intAsString);
		} catch (NumberFormatException nfe) {
			return 0;
		}
	}

	/**
	 * Construct a new driver and register it with DriverManager
	 * 
	 * @throws SQLException
	 *             if a database error occurs.
	 */
	public NonRegisteringDriver() throws SQLException {
		// Required for Class.forName().newInstance()
	}

	/**
	 * Typically, drivers will return true if they understand the subprotocol
	 * specified in the URL and false if they don't. This driver's protocols
	 * start with jdbc:mysql:
	 * 
	 * @param url
	 *            the URL of the driver
	 * 
	 * @return true if this driver accepts the given URL
	 * 
	 * @exception SQLException
	 *                if a database-access error occurs
	 * 
	 * @see java.sql.Driver#acceptsURL
	 */
	public boolean acceptsURL(String url) throws SQLException {
		return (parseURL(url, null) != null);
	}

	//
	// return the database name property
	//

	/**
	 * Try to make a database connection to the given URL. The driver should
	 * return "null" if it realizes it is the wrong kind of driver to connect to
	 * the given URL. This will be common, as when the JDBC driverManager is
	 * asked to connect to a given URL, it passes the URL to each loaded driver
	 * in turn.
	 * 
	 * <p>
	 * The driver should raise an SQLException if it is the right driver to
	 * connect to the given URL, but has trouble connecting to the database.
	 * </p>
	 * 
	 * <p>
	 * The java.util.Properties argument can be used to pass arbitrary string
	 * tag/value pairs as connection arguments.
	 * </p>
	 * 
	 * <p>
	 * My protocol takes the form:
	 * 
	 * <PRE>
	 * 
	 * jdbc:mysql://host:port/database
	 * 
	 * </PRE>
	 * 
	 * </p>
	 * 
	 * @param url
	 *            the URL of the database to connect to
	 * @param info
	 *            a list of arbitrary tag/value pairs as connection arguments
	 * 
	 * @return a connection to the URL or null if it isnt us
	 * 
	 * @exception SQLException
	 *                if a database access error occurs
	 * 
	 * @see java.sql.Driver#connect
	 */
	public java.sql.Connection connect(String url, Properties info)
			throws SQLException {
		try {//我加上的
		DEBUG.P(this,"connect(2)");
		DEBUG.P("url="+url);
		DEBUG.P("info="+info);

		//info.setProperty("user","root");
		//info.setProperty("password","test");

		if (url != null) {
			//不同的url前缀会得到不同的Connection
			//MXJ_URL_PREFIX这种url前缀只会多一个socketFactory属性，
			//在parseURL中处理了，等同于"jdbc:mysql://"
			if (StringUtils.startsWithIgnoreCase(url, LOADBALANCE_URL_PREFIX)) {
				return connectLoadBalanced(url, info);
			} else if (StringUtils.startsWithIgnoreCase(url,
					REPLICATION_URL_PREFIX)) {
				return connectReplicationConnection(url, info);
			}
		}

		Properties props = null;

		//parseURL返回null，有可能是url本身为null，也有可能是url的前缀不合法
		//可以这样调用: new com.mysql.jdbc.Driver().connect(url, info);
		//解析完url后得到的props是info的超集
		if ((props = parseURL(url, info)) == null) {
			return null;
		}

		if (!"1".equals(props.getProperty(NUM_HOSTS_PROPERTY_KEY))) {
			return connectFailover(url, info);
		}
		
		try {
			Connection newConn = com.mysql.jdbc.ConnectionImpl.getInstance(
					host(props), port(props), props, database(props), url);

			return newConn;
		} catch (SQLException sqlEx) {
			// Don't wrap SQLExceptions, throw
			// them un-changed.
			throw sqlEx;
		} catch (Exception ex) { //其他的原因
			SQLException sqlEx = SQLError.createSQLException(Messages
					.getString("NonRegisteringDriver.17") //$NON-NLS-1$
					+ ex.toString()
					+ Messages.getString("NonRegisteringDriver.18"), //$NON-NLS-1$
					SQLError.SQL_STATE_UNABLE_TO_CONNECT_TO_DATASOURCE, null);
			
			sqlEx.initCause(ex);
			
			throw sqlEx;
		}

		}finally{//我加上的
		DEBUG.P(0,this,"connect(2)");
		}
	}

	private java.sql.Connection connectLoadBalanced(String url, Properties info)
			throws SQLException {
		try {//我加上的
		DEBUG.P(this,"connectLoadBalanced(2)");
		DEBUG.P("url="+url);
		DEBUG.P("info="+info);

		Properties parsedProps = parseURL(url, info);

		//对于"jdbc:mysql:loadbalance://localhost:3306,127.0.0.1/test"
		//会得到三个HOST、PORT对:
		//HOST=localhost PORT=3306 (就是第一个)
		//HOST.1=localhost PORT.1=3306
		//HOST.2=127.0.0.1 PORT.2=3306
		DEBUG.P("parsedProps="+parsedProps);

		// People tend to drop this in, it doesn't make sense
		parsedProps.remove("roundRobinLoadBalance");
		
		if (parsedProps == null) {
			return null;
		}

		int numHosts = Integer.parseInt(parsedProps.getProperty(NUM_HOSTS_PROPERTY_KEY));

		DEBUG.P("numHosts="+numHosts);

		List<String> hostList = new ArrayList<String>();

		for (int i = 0; i < numHosts; i++) {
			int index = i + 1;
			
			hostList.add(parsedProps.getProperty(HOST_PROPERTY_KEY + "." + index) + ":" 
					+ parsedProps.getProperty(PORT_PROPERTY_KEY + "." + index));
		}

		DEBUG.P("hostList="+hostList); //如:hostList=[localhost:3306, 127.0.0.1:3306]

		LoadBalancingConnectionProxy proxyBal = new LoadBalancingConnectionProxy(
				hostList, parsedProps);

		return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(this
				.getClass().getClassLoader(),
				new Class[] { com.mysql.jdbc.Connection.class }, proxyBal);

		}finally{//我加上的
		DEBUG.P(0,this,"connectLoadBalanced(2)");
		}
	}
	
	private java.sql.Connection connectFailover(String url, Properties info)
			throws SQLException {
		Properties parsedProps = parseURL(url, info);

		// People tend to drop this in, it doesn't make sense
		parsedProps.remove("roundRobinLoadBalance");
		parsedProps.setProperty("autoReconnect", "false");
		
		if (parsedProps == null) {
			return null;
		}

		int numHosts = Integer.parseInt(parsedProps
				.getProperty(NUM_HOSTS_PROPERTY_KEY));

		List<String> hostList = new ArrayList<String>();
		
		for (int i = 0; i < numHosts; i++) {
			int index = i + 1;

			hostList.add(parsedProps.getProperty(HOST_PROPERTY_KEY + "."
					+ index)
					+ ":"
					+ parsedProps.getProperty(PORT_PROPERTY_KEY + "." + index));
		}

		FailoverConnectionProxy connProxy = new FailoverConnectionProxy(
				hostList, parsedProps);

		return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
				this.getClass().getClassLoader(),
				new Class[] { com.mysql.jdbc.Connection.class }, connProxy);
	}

	protected java.sql.Connection connectReplicationConnection(String url, Properties info)
			throws SQLException {
		try {//我加上的
		DEBUG.P(this,"connectReplicationConnection(2)");
		DEBUG.P("url="+url);
		DEBUG.P("info="+info);

		Properties parsedProps = parseURL(url, info);

		DEBUG.P("parsedProps="+parsedProps);

		if (parsedProps == null) {
			return null;
		}

		Properties masterProps = (Properties) parsedProps.clone();
		Properties slavesProps = (Properties) parsedProps.clone();

		DEBUG.P("masterProps="+masterProps);
		DEBUG.P("slavesProps="+slavesProps);

		// Marker used for further testing later on, also when
		// debugging
		slavesProps.setProperty("com.mysql.jdbc.ReplicationConnection.isSlave",
				"true");
		
		int numHosts = Integer.parseInt(parsedProps.getProperty(NUM_HOSTS_PROPERTY_KEY));

		DEBUG.P("numHosts="+numHosts);
		if (numHosts < 2) {
			throw SQLError
					.createSQLException(
							"Must specify at least one slave host to connect to for master/slave replication load-balancing functionality",
							SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
		}
		
		//对于"jdbc:mysql:replication://localhost:3306,127.0.0.1/test"
		//会得到三个HOST、PORT对:
		//HOST=localhost PORT=3306 (就是第一个)
		//HOST.1=localhost PORT.1=3306
		//HOST.2=127.0.0.1 PORT.2=3306
		for (int i = 1; i < numHosts; i++) {
			int index = i + 1;

			//因为HOST.1总是为主库，所以在主库的masterProps中删掉index>=2之后的属性值
			
			masterProps.remove(HOST_PROPERTY_KEY + "." + index);
			masterProps.remove(PORT_PROPERTY_KEY + "." + index);
			
			//因为之前的HOST.1和PORT.1会在备库的slavesProps中删除，
			//所以从index>=2之后序号都要往前移
			slavesProps.setProperty(HOST_PROPERTY_KEY + "." + i, parsedProps.getProperty(HOST_PROPERTY_KEY + "." + index));
			slavesProps.setProperty(PORT_PROPERTY_KEY + "." + i, parsedProps.getProperty(PORT_PROPERTY_KEY + "." + index));
		}

		DEBUG.P("masterProps="+masterProps);
		DEBUG.P("slavesProps="+slavesProps);

		masterProps.setProperty(NUM_HOSTS_PROPERTY_KEY, "1");

		//删掉最大的那个序号，因为在之前的for循环中已经往前移过了
		slavesProps.remove(HOST_PROPERTY_KEY + "." + numHosts);
		slavesProps.remove(PORT_PROPERTY_KEY + "." + numHosts);
		slavesProps.setProperty(NUM_HOSTS_PROPERTY_KEY, String.valueOf(numHosts - 1));
		
		//默认主机是第一个
		slavesProps.setProperty(HOST_PROPERTY_KEY, slavesProps.getProperty(HOST_PROPERTY_KEY + ".1"));
		slavesProps.setProperty(PORT_PROPERTY_KEY, slavesProps.getProperty(PORT_PROPERTY_KEY + ".1"));

		DEBUG.P("masterProps="+masterProps);
		DEBUG.P("slavesProps="+slavesProps);

		return new ReplicationConnection(masterProps, slavesProps);

		}finally{//我加上的
		DEBUG.P(0,this,"connectReplicationConnection(2)");
		}
	}

	/**
	 * Returns the database property from <code>props</code>
	 * 
	 * @param props
	 *            the Properties to look for the database property.
	 * 
	 * @return the database name.
	 */
	public String database(Properties props) {
		return props.getProperty(DBNAME_PROPERTY_KEY); //$NON-NLS-1$
	}

	/**
	 * Gets the drivers major version number
	 * 
	 * @return the drivers major version number
	 */
	public int getMajorVersion() {
		return getMajorVersionInternal();
	}

	/**
	 * Get the drivers minor version number
	 * 
	 * @return the drivers minor version number
	 */
	public int getMinorVersion() {
		return getMinorVersionInternal();
	}

	/**
	 * The getPropertyInfo method is intended to allow a generic GUI tool to
	 * discover what properties it should prompt a human for in order to get
	 * enough information to connect to a database.
	 * 
	 * <p>
	 * Note that depending on the values the human has supplied so far,
	 * additional values may become necessary, so it may be necessary to iterate
	 * through several calls to getPropertyInfo
	 * </p>
	 * 
	 * @param url
	 *            the Url of the database to connect to
	 * @param info
	 *            a proposed list of tag/value pairs that will be sent on
	 *            connect open.
	 * 
	 * @return An array of DriverPropertyInfo objects describing possible
	 *         properties. This array may be an empty array if no properties are
	 *         required
	 * 
	 * @exception SQLException
	 *                if a database-access error occurs
	 * 
	 * @see java.sql.Driver#getPropertyInfo
	 */
	public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)
			throws SQLException {
		if (info == null) {
			info = new Properties();
		}

		if ((url != null) && url.startsWith(URL_PREFIX)) { //$NON-NLS-1$
			info = parseURL(url, info);
		}

		DriverPropertyInfo hostProp = new DriverPropertyInfo(HOST_PROPERTY_KEY, //$NON-NLS-1$
				info.getProperty(HOST_PROPERTY_KEY)); //$NON-NLS-1$
		hostProp.required = true;
		hostProp.description = Messages.getString("NonRegisteringDriver.3"); //$NON-NLS-1$

		DriverPropertyInfo portProp = new DriverPropertyInfo(PORT_PROPERTY_KEY, //$NON-NLS-1$
				info.getProperty(PORT_PROPERTY_KEY, "3306")); //$NON-NLS-1$ //$NON-NLS-2$
		portProp.required = false;
		portProp.description = Messages.getString("NonRegisteringDriver.7"); //$NON-NLS-1$

		DriverPropertyInfo dbProp = new DriverPropertyInfo(DBNAME_PROPERTY_KEY, //$NON-NLS-1$
				info.getProperty(DBNAME_PROPERTY_KEY)); //$NON-NLS-1$
		dbProp.required = false;
		dbProp.description = "Database name"; //$NON-NLS-1$

		DriverPropertyInfo userProp = new DriverPropertyInfo(USER_PROPERTY_KEY, //$NON-NLS-1$
				info.getProperty(USER_PROPERTY_KEY)); //$NON-NLS-1$
		userProp.required = true;
		userProp.description = Messages.getString("NonRegisteringDriver.13"); //$NON-NLS-1$

		DriverPropertyInfo passwordProp = new DriverPropertyInfo(
				PASSWORD_PROPERTY_KEY, //$NON-NLS-1$
				info.getProperty(PASSWORD_PROPERTY_KEY)); //$NON-NLS-1$
		passwordProp.required = true;
		passwordProp.description = Messages
				.getString("NonRegisteringDriver.16"); //$NON-NLS-1$

		//预留前面5个位置
		DriverPropertyInfo[] dpi = ConnectionPropertiesImpl
				.exposeAsDriverPropertyInfo(info, 5);

		dpi[0] = hostProp;
		dpi[1] = portProp;
		dpi[2] = dbProp;
		dpi[3] = userProp;
		dpi[4] = passwordProp;

		return dpi;
	}

	//
	// return the value of any property this driver knows about
	//

	/**
	 * Returns the hostname property
	 * 
	 * @param props
	 *            the java.util.Properties instance to retrieve the hostname
	 *            from.
	 * 
	 * @return the hostname
	 */
	public String host(Properties props) {
		return props.getProperty(HOST_PROPERTY_KEY, "localhost"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Report whether the driver is a genuine JDBC compliant driver. A driver
	 * may only report "true" here if it passes the JDBC compliance tests,
	 * otherwise it is required to return false. JDBC compliance requires full
	 * support for the JDBC API and full support for SQL 92 Entry Level.
	 * 
	 * <p>
	 * MySQL is not SQL92 compliant
	 * </p>
	 * 
	 * @return is this driver JDBC compliant?
	 */
	public boolean jdbcCompliant() {
		return false;
	}

	public Properties parseURL(String url, Properties defaults)
			throws java.sql.SQLException {
		Properties urlProps = (defaults != null) ? new Properties(defaults)
				: new Properties();

		if (url == null) {
			return null;
		}

		if (!StringUtils.startsWithIgnoreCase(url, URL_PREFIX)
				&& !StringUtils.startsWithIgnoreCase(url, MXJ_URL_PREFIX)
				&& !StringUtils.startsWithIgnoreCase(url,
						LOADBALANCE_URL_PREFIX)
				&& !StringUtils.startsWithIgnoreCase(url,
						REPLICATION_URL_PREFIX)) { //$NON-NLS-1$

			return null;
		}

		int beginningOfSlashes = url.indexOf("//");

		if (StringUtils.startsWithIgnoreCase(url, MXJ_URL_PREFIX)) {
			
			urlProps
					.setProperty("socketFactory",
							"com.mysql.management.driverlaunched.ServerLauncherSocketFactory");
		}

		/*
		 * Parse parameters after the ? in the URL and remove them from the
		 * original URL.
		 */
		int index = url.indexOf("?"); //$NON-NLS-1$

		if (index != -1) {
			String paramString = url.substring(index + 1, url.length());
			url = url.substring(0, index);

			StringTokenizer queryParams = new StringTokenizer(paramString, "&"); //$NON-NLS-1$

			while (queryParams.hasMoreTokens()) {
				String parameterValuePair = queryParams.nextToken();

				int indexOfEquals = StringUtils.indexOfIgnoreCase(0,
						parameterValuePair, "=");

				String parameter = null;
				String value = null;

				if (indexOfEquals != -1) {
					parameter = parameterValuePair.substring(0, indexOfEquals);

					if (indexOfEquals + 1 < parameterValuePair.length()) {
						value = parameterValuePair.substring(indexOfEquals + 1);
					}
				}

				if ((value != null && value.length() > 0)
						&& (parameter != null && parameter.length() > 0)) {
					try {
						urlProps.put(parameter, URLDecoder.decode(value,
								"UTF-8"));
					} catch (UnsupportedEncodingException badEncoding) {
						// punt
						urlProps.put(parameter, URLDecoder.decode(value));
					} catch (NoSuchMethodError nsme) {
						// punt again
						urlProps.put(parameter, URLDecoder.decode(value));
					}
				}
			}
		}

		url = url.substring(beginningOfSlashes + 2);

		String hostStuff = null;

		int slashIndex = url.indexOf("/"); //$NON-NLS-1$

		if (slashIndex != -1) {
			hostStuff = url.substring(0, slashIndex);

			if ((slashIndex + 1) < url.length()) {
				urlProps.put(DBNAME_PROPERTY_KEY, //$NON-NLS-1$
						url.substring((slashIndex + 1), url.length()));
			}
		} else {
			hostStuff = url;
		}

		int numHosts = 0;
		
		if ((hostStuff != null) && (hostStuff.trim().length() > 0)) {
			StringTokenizer st = new StringTokenizer(hostStuff, ",");
			
			while (st.hasMoreTokens()) {
				numHosts++;
			
				String[] hostPortPair = parseHostPortPair(st.nextToken());

				if (hostPortPair[HOST_NAME_INDEX] != null && hostPortPair[HOST_NAME_INDEX].trim().length() > 0) {
					urlProps.setProperty(HOST_PROPERTY_KEY + "." + numHosts, hostPortPair[HOST_NAME_INDEX]);
				} else {
					urlProps.setProperty(HOST_PROPERTY_KEY + "." + numHosts, "localhost");
				}

				if (hostPortPair[PORT_NUMBER_INDEX] != null) {
					urlProps.setProperty(PORT_PROPERTY_KEY + "." + numHosts, hostPortPair[PORT_NUMBER_INDEX]);
				} else {
					urlProps.setProperty(PORT_PROPERTY_KEY + "." + numHosts, "3306");
				}
			}
		} else {
			numHosts = 1;
			urlProps.setProperty(HOST_PROPERTY_KEY + ".1", "localhost");
			urlProps.setProperty(PORT_PROPERTY_KEY + ".1", "3306");
		}

		urlProps.setProperty(NUM_HOSTS_PROPERTY_KEY, String.valueOf(numHosts));
		urlProps.setProperty(HOST_PROPERTY_KEY, urlProps.getProperty(HOST_PROPERTY_KEY + ".1"));
		urlProps.setProperty(PORT_PROPERTY_KEY, urlProps.getProperty(PORT_PROPERTY_KEY + ".1"));
		
		String propertiesTransformClassName = urlProps
				.getProperty(PROPERTIES_TRANSFORM_KEY);

		if (propertiesTransformClassName != null) {
			try {
				ConnectionPropertiesTransform propTransformer = (ConnectionPropertiesTransform) Class
						.forName(propertiesTransformClassName).newInstance();

				urlProps = propTransformer.transformProperties(urlProps);
			} catch (InstantiationException e) {
				throw SQLError.createSQLException(
						"Unable to create properties transform instance '"
								+ propertiesTransformClassName
								+ "' due to underlying exception: "
								+ e.toString(),
						SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
			} catch (IllegalAccessException e) {
				throw SQLError.createSQLException(
						"Unable to create properties transform instance '"
								+ propertiesTransformClassName
								+ "' due to underlying exception: "
								+ e.toString(),
						SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
			} catch (ClassNotFoundException e) {
				throw SQLError.createSQLException(
						"Unable to create properties transform instance '"
								+ propertiesTransformClassName
								+ "' due to underlying exception: "
								+ e.toString(),
						SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
			}
		}

		if (Util.isColdFusion() &&
				urlProps.getProperty("autoConfigureForColdFusion", "true").equalsIgnoreCase("true")) {
			String configs = urlProps.getProperty(USE_CONFIG_PROPERTY_KEY);
			
			StringBuffer newConfigs = new StringBuffer();
			
			if (configs != null) {
				newConfigs.append(configs);
				newConfigs.append(",");
			}
			
			newConfigs.append("coldFusion");
			
			urlProps.setProperty(USE_CONFIG_PROPERTY_KEY, newConfigs.toString());
		}
		
		// If we use a config, it actually should get overridden by anything in
		// the URL or passed-in properties

		String configNames = null;

		if (defaults != null) {
			configNames = defaults.getProperty(USE_CONFIG_PROPERTY_KEY);
		}

		if (configNames == null) {
			configNames = urlProps.getProperty(USE_CONFIG_PROPERTY_KEY);
		}

		if (configNames != null) {
			List splitNames = StringUtils.split(configNames, ",", true);

			Properties configProps = new Properties();

			Iterator namesIter = splitNames.iterator();

			while (namesIter.hasNext()) {
				String configName = (String) namesIter.next();

				try {
					InputStream configAsStream = getClass()
							.getResourceAsStream(
									"configs/" + configName + ".properties");

					if (configAsStream == null) {
						throw SQLError
								.createSQLException(
										"Can't find configuration template named '"
												+ configName + "'",
										SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
					}
					configProps.load(configAsStream);
				} catch (IOException ioEx) {
					SQLException sqlEx = SQLError.createSQLException(
							"Unable to load configuration template '"
									+ configName
									+ "' due to underlying IOException: "
									+ ioEx,
							SQLError.SQL_STATE_INVALID_CONNECTION_ATTRIBUTE, null);
					sqlEx.initCause(ioEx);
					
					throw sqlEx;
				}
			}

			Iterator propsIter = urlProps.keySet().iterator();

			while (propsIter.hasNext()) {
				String key = propsIter.next().toString();
				String property = urlProps.getProperty(key);
				configProps.setProperty(key, property);
			}

			urlProps = configProps;
		}

		// Properties passed in should override ones in URL

		if (defaults != null) {
			Iterator propsIter = defaults.keySet().iterator();

			while (propsIter.hasNext()) {
				String key = propsIter.next().toString();
				if (!key.equals(NUM_HOSTS_PROPERTY_KEY)) {
					String property = defaults.getProperty(key);
					urlProps.setProperty(key, property);
				}
			}
		}

		return urlProps;
	}

	/**
	 * Returns the port number property
	 * 
	 * @param props
	 *            the properties to get the port number from
	 * 
	 * @return the port number
	 */
	public int port(Properties props) {
		return Integer.parseInt(props.getProperty(PORT_PROPERTY_KEY, "3306")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Returns the given property from <code>props</code>
	 * 
	 * @param name
	 *            the property name
	 * @param props
	 *            the property instance to look in
	 * 
	 * @return the property value, or null if not found.
	 */
	public String property(String name, Properties props) {
		return props.getProperty(name);
	}

    //@Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        // TODO Auto-generated method stub
        return null;
    }
}
