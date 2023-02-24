/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.hibernate.jmx;

import java.io.IOException;
import java.net.URL;
import java.util.Date;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.jmx.StatisticsService;
import org.hibernate.jmx.StatisticsServiceMBean;
import org.hibernate.transaction.JBossTransactionManagerLookup;
import org.hibernate.transaction.JTATransactionFactory;
import org.jboss.beans.metadata.api.annotations.Inject;
import org.jboss.beans.metadata.api.model.FromContext;
import org.jboss.beans.metadata.spi.builder.BeanMetaDataBuilder;
import org.jboss.hibernate.ListenerInjector;
import org.jboss.kernel.plugins.bootstrap.basic.KernelConstants;
import org.jboss.kernel.spi.dependency.KernelController;
import org.jboss.logging.Logger;
import org.jboss.util.naming.Util;
import org.jboss.virtual.VFS;
import org.jboss.virtual.VirtualFile;

/**
 * The {@link HibernateMBean} implementation.
 *
 * @author <a href="mailto:alex@jboss.org">Alexey Loubyansky</a>
 * @author <a href="mailto:gavin@hibernate.org">Gavin King</a>
 * @author <a href="mailto:steve@hibernate.org">Steve Ebersole</a>
 * @author <a href="mailto:dimitris@jboss.org">Dimitris Andreadis</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:pferraro@redhat.com">Paul Ferraro</a>
 * @version <tt>$Revision: 84628 $</tt>
 */
public class Hibernate implements HibernateMBean
{
   private static final Logger log = Logger.getLogger( Hibernate.class );

   public static final String SESSION_FACTORY_CREATE = "hibernate.sessionfactory.create";
   public static final String SESSION_FACTORY_DESTROY = "hibernate.sessionfactory.destroy";

   // Configuration attributes "passed through" to Hibernate
   private String datasourceName;
   private String dialect;
   private String defaultSchema;
   private String defaultCatalog;
   private Boolean sqlCommentsEnabled;
   private Integer maxFetchDepth;
   private Integer jdbcFetchSize;
   private Integer jdbcBatchSize;
   private Boolean batchVersionedDataEnabled;
   private Boolean jdbcScrollableResultSetEnabled;
   private Boolean getGeneratedKeysEnabled;
   private Boolean streamsForBinaryEnabled;
   private String hbm2ddlAuto;
   private String querySubstitutions;
   private Boolean showSqlEnabled;
   private String username;
   private String password;
   private Boolean secondLevelCacheEnabled = Boolean.TRUE;
   private Boolean queryCacheEnabled;
   private String cacheProviderClass;
   private String cacheRegionFactoryClass;
   private String deployedCacheJndiName;
   private String deployedCacheManagerJndiName;
   private Boolean minimalPutsEnabled;
   private String cacheRegionPrefix;
   private Boolean structuredCacheEntriesEnabled;
   private Boolean statGenerationEnabled;
   private Boolean reflectionOptimizationEnabled;

   // Configuration attributes used by the MBean
   private String sessionFactoryName;
   private String sessionFactoryInterceptor;
   private String listenerInjector;
   private URL harUrl;
   private boolean scanForMappingsEnabled = false;

   // Internal state
   private VirtualFile root;
   private boolean dirty = false;
   private Date runningSince;
   private SessionFactory sessionFactory;
   private String hibernateStatisticsServiceName;

   // back compatible
   public Hibernate()
   {
   }

   public Hibernate(VirtualFile root)
   {
      if (root == null)
         throw new IllegalArgumentException("Null root file");
      this.root = root;
   }

   // Injected from underlying MC
   private Object beanName;
   private KernelController controller;

   @Inject(fromContext = FromContext.NAME)
   public void setBeanName(Object beanName)
   {
      this.beanName = beanName;
   }

   @Inject(bean = KernelConstants.KERNEL_CONTROLLER_NAME)
   public void setController(KernelController controller)
   {
      this.controller = controller;
   }

   /**
    * Configure Hibernate and bind the <tt>SessionFactory</tt> to JNDI.
    */
   public void start() throws Throwable
   {
      log.debug( "Hibernate MBean starting; " + this );

      // be defensive...
      if ( sessionFactory != null )
      {
         destroySessionFactory();
      }

      buildSessionFactory();
   }

   /**
    * Close the <tt>SessionFactory</tt>.
    */
   public void stop() throws Exception
   {
      destroySessionFactory();
   }

   /**
    * Centralize the logic needed for starting/binding the SessionFactory.
    *
    * @throws Exception
    */
   private void buildSessionFactory() throws Throwable
   {
      log.debug( "Building SessionFactory; " + this );

      Configuration cfg = new Configuration();
      cfg.getProperties().clear(); // avoid reading hibernate.properties and Sys-props

      // Handle custom listeners....
      ListenerInjector listenerInjector = generateListenerInjectorInstance();
      if ( listenerInjector != null )
      {
         listenerInjector.injectListeners( beanName, cfg );
      }

      // Handle config settings....
      transferSettings( cfg.getProperties() );

      // Handle mappings....
      handleMappings( cfg );

      // Handle interceptor....
      Interceptor interceptorInstance = generateInterceptorInstance();
      if ( interceptorInstance != null )
      {
         cfg.setInterceptor( interceptorInstance );
      }

      // Generate sf....
      sessionFactory = cfg.buildSessionFactory();

      try
      {
         // Handle stat-mbean creation/registration....
         if ( sessionFactory.getStatistics() != null && sessionFactory.getStatistics().isStatisticsEnabled() )
         {
            String serviceName = beanName.toString();
            if( serviceName.indexOf("type=service") != -1 )
            {
               serviceName = serviceName.replaceAll("type=service","type=stats");
            }
            else
            {
               serviceName = serviceName + ",type=stats";
            }
            hibernateStatisticsServiceName = serviceName;
            StatisticsService hibernateStatisticsService = new StatisticsService();
            hibernateStatisticsService.setSessionFactory( sessionFactory );
            BeanMetaDataBuilder builder = BeanMetaDataBuilder.createBuilder(hibernateStatisticsServiceName, StatisticsService.class.getName());
            StringBuffer buffer = new StringBuffer();
            buffer.append("@org.jboss.aop.microcontainer.aspects.jmx.JMX(name=\"").append(hibernateStatisticsServiceName).append("JMX\"");
            buffer.append(", exposedInterface=").append(StatisticsServiceMBean.class.getName()).append(".class, registerDirectly=true)");
            String jmxAnnotation = buffer.toString();
            // todo - add the @JMX into annotations
            //builder.addAnnotation(jmxAnnotation);
            controller.install(builder.getBeanMetaData(), hibernateStatisticsService);
         }

         // Handle JNDI binding....
         bind();
      }
      catch ( Exception e )
      {
         forceCleanup();
         throw e;
      }

      dirty = false;

      runningSince = new Date();

      log.info( "SessionFactory successfully built and bound into JNDI [" + sessionFactoryName + "]" );
   }

   /**
    * Centralize the logic needed to unbind/close a SessionFactory.
    *
    * @throws Exception
    */
   private void destroySessionFactory() throws Exception
   {
      if ( sessionFactory != null )
      {
         // TODO : exact situations where we need to clear the 2nd-lvl cache?
         // (to allow clean release of the classloaders)
         // Most likely, if custom classes are directly cached (UserTypes); anything else?
         unbind();
         sessionFactory.close();
         sessionFactory = null;
         runningSince = null;

         if ( hibernateStatisticsServiceName != null )
         {
            try
            {
               controller.uninstall( hibernateStatisticsServiceName );
            }
            catch ( Throwable t )
            {
               // just log it
               log.warn( "unable to cleanup statistics mbean", t );
            }
         }
      }
   }

   private void handleMappings(Configuration cfg) throws IOException
   {
      if (root == null)
      {
         if (harUrl == null)
            throw new IllegalArgumentException("Must set one of the resources, root or harUrl: " + this);

         root = VFS.getRoot(harUrl);
      }

      HibernateMappingVisitor visitor = new HibernateMappingVisitor();
      root.visit(visitor);
      for (URL url : visitor.getUrls())
      {
         log.debug("Passing input stream [" + url + "] to Hibernate Configration");
         cfg.addInputStream(url.openStream());
      }
   }

   /**
    * Transfer the state represented by our current attribute values into the given Properties instance, translating our
    * attributes into the appropriate Hibernate settings.
    *
    * @param settings The Properties instance to which to add our state.
    */
   private void transferSettings(Properties settings)
   {
      if ( cacheProviderClass == null )
      {
         cacheProviderClass = "org.hibernate.cache.HashtableCacheProvider";
      }

      log.debug( "Using JDBC batch size : " + jdbcBatchSize );

      setUnlessNull( settings, Environment.DATASOURCE, datasourceName );
      setUnlessNull( settings, Environment.DIALECT, dialect );
      setUnlessNull( settings, Environment.CACHE_PROVIDER, cacheProviderClass );
      setUnlessNull( settings, Environment.CACHE_REGION_FACTORY, cacheRegionFactoryClass );
      setUnlessNull( settings, Environment.CACHE_REGION_PREFIX, cacheRegionPrefix );
      setUnlessNull( settings, Environment.USE_MINIMAL_PUTS, minimalPutsEnabled );
      setUnlessNull( settings, Environment.HBM2DDL_AUTO, hbm2ddlAuto );
      setUnlessNull( settings, Environment.DEFAULT_SCHEMA, defaultSchema );
      setUnlessNull( settings, Environment.STATEMENT_BATCH_SIZE, jdbcBatchSize );
      setUnlessNull( settings, Environment.USE_SQL_COMMENTS, sqlCommentsEnabled );

      setUnlessNull( settings, Environment.STATEMENT_FETCH_SIZE, jdbcFetchSize );
      setUnlessNull( settings, Environment.USE_SCROLLABLE_RESULTSET, jdbcScrollableResultSetEnabled );
      setUnlessNull( settings, Environment.USE_QUERY_CACHE, queryCacheEnabled );
      setUnlessNull( settings, Environment.USE_STRUCTURED_CACHE, structuredCacheEntriesEnabled );
      setUnlessNull( settings, Environment.QUERY_SUBSTITUTIONS, querySubstitutions );
      setUnlessNull( settings, Environment.MAX_FETCH_DEPTH, maxFetchDepth );
      setUnlessNull( settings, Environment.SHOW_SQL, showSqlEnabled );
      setUnlessNull( settings, Environment.USE_GET_GENERATED_KEYS, getGeneratedKeysEnabled );
      setUnlessNull( settings, Environment.USER, username );
      setUnlessNull( settings, Environment.PASS, password );
      setUnlessNull( settings, Environment.BATCH_VERSIONED_DATA, batchVersionedDataEnabled );
      setUnlessNull( settings, Environment.USE_STREAMS_FOR_BINARY, streamsForBinaryEnabled );
      setUnlessNull( settings, Environment.USE_REFLECTION_OPTIMIZER, reflectionOptimizationEnabled );
      setUnlessNull( settings, Environment.GENERATE_STATISTICS, statGenerationEnabled );

      setUnlessNull(
            settings, Environment.TRANSACTION_MANAGER_STRATEGY, JBossTransactionManagerLookup.class.getName()
      );
      setUnlessNull( settings, Environment.TRANSACTION_STRATEGY, JTATransactionFactory.class.getName() );

      if (this.deployedCacheJndiName != null)
      {
         settings.setProperty(org.hibernate.cache.jbc2.builder.JndiSharedCacheInstanceManager.CACHE_RESOURCE_PROP, this.deployedCacheJndiName);
         
         // Implies shared cache region factory
         if (!settings.containsKey(Environment.CACHE_REGION_FACTORY))
         {
            settings.setProperty(Environment.CACHE_REGION_FACTORY, org.hibernate.cache.jbc2.JndiSharedJBossCacheRegionFactory.class.getName());
         }
      }
      
      if (this.deployedCacheManagerJndiName != null)
      {
         settings.setProperty(org.hibernate.cache.jbc2.builder.JndiMultiplexingCacheInstanceManager.CACHE_FACTORY_RESOURCE_PROP, this.deployedCacheManagerJndiName);
         
         // Implies multliplexed cache region factory
         if (!settings.containsKey(Environment.CACHE_REGION_FACTORY))
         {
            settings.setProperty(Environment.CACHE_REGION_FACTORY, org.hibernate.cache.jbc2.JndiMultiplexedJBossCacheRegionFactory.class.getName());
         }
      }

      settings.setProperty( Environment.FLUSH_BEFORE_COMPLETION, "true" );
      settings.setProperty( Environment.AUTO_CLOSE_SESSION, "true" );

      // This is really H3-version-specific:
      // in 3.0.3 and later, this should be the ConnectionReleaseMode enum;
      // in 3.0.2, this is a true/false setting;
      // in 3.0 -> 3.0.1, there is no such setting
      //
      // so we just set them both :)
      settings.setProperty( "hibernate.connection.agressive_release", "true" );
      settings.setProperty( "hibernate.connection.release_mode", "after_statement" );
   }

   /**
    * Simple helper method for transferring individual settings to a properties
    * instance only if the setting's value is not null.
    *
    * @param props The properties instance into which to transfer the setting
    * @param key   The key under which to transfer the setting
    * @param value The value of the setting.
    */
   private void setUnlessNull(Properties props, String key, Object value)
   {
      if ( value != null )
      {
         props.setProperty( key, value.toString() );
      }
   }

   private ListenerInjector generateListenerInjectorInstance()
   {
      if ( listenerInjector == null )
      {
         return null;
      }

      log.info( "attempting to use listener injector [" + listenerInjector + "]" );
      try
      {
         return ( ListenerInjector ) Thread.currentThread()
               .getContextClassLoader()
               .loadClass( listenerInjector )
               .newInstance();
      }
      catch ( Throwable t )
      {
         log.warn( "Unable to generate specified listener injector", t );
      }

      return null;
   }

   private Interceptor generateInterceptorInstance()
   {
      if ( sessionFactoryInterceptor == null )
      {
         return null;
      }

      log.info( "Generating session factory interceptor instance [" + sessionFactoryInterceptor + "]" );
      try
      {
         return ( Interceptor ) Thread.currentThread()
               .getContextClassLoader()
               .loadClass( sessionFactoryInterceptor )
               .newInstance();
      }
      catch ( Throwable t )
      {
         log.warn( "Unable to generate session factory interceptor instance", t );
      }

      return null;
   }

   /**
    * Perform the steps necessary to bind the managed SessionFactory into JNDI.
    *
    * @throws HibernateException
    */
   private void bind() throws HibernateException
   {
      InitialContext ctx = null;
      try
      {
         ctx = new InitialContext();
         Util.bind( ctx, sessionFactoryName, sessionFactory );
      }
      catch ( NamingException e )
      {
         throw new HibernateException( "Unable to bind SessionFactory into JNDI", e );
      }
      finally
      {
         if ( ctx != null )
         {
            try
            {
               ctx.close();
            }
            catch ( Throwable ignore )
            {
               // ignore
            }
         }
      }
   }

   /**
    * Perform the steps necessary to unbind the managed SessionFactory from JNDI.
    *
    * @throws HibernateException
    */
   private void unbind() throws HibernateException
   {
      InitialContext ctx = null;
      try
      {
         ctx = new InitialContext();
         Util.unbind( ctx, sessionFactoryName );
      }
      catch ( NamingException e )
      {
         throw new HibernateException( "Unable to unbind SessionFactory from JNDI", e );
      }
      finally
      {
         if ( ctx != null )
         {
            try
            {
               ctx.close();
            }
            catch ( Throwable ignore )
            {
               // ignore
            }
         }
      }
   }

   private void forceCleanup()
   {
      try
      {
         sessionFactory.close();
         sessionFactory = null;
      }
      catch ( Throwable ignore )
      {
         // ignore
      }
   }

   public String toString()
   {
      return super.toString() + " [BeanName=" + beanName + ", JNDI=" + sessionFactoryName + "]";
   }


   // Managed operations ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   public void rebuildSessionFactory() throws Throwable
   {
      destroySessionFactory();
      buildSessionFactory();
   }


   // RO managed attributes ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   public boolean isDirty()
   {
      return dirty;
   }

   public boolean isSessionFactoryRunning()
   {
      return sessionFactory != null;
   }

   public String getVersion()
   {
      return Environment.VERSION;
   }

   public SessionFactory getInstance()
   {
      return sessionFactory;
   }

   public Object getStatisticsServiceName()
   {
      return hibernateStatisticsServiceName;
   }

   public Date getRunningSince()
   {
      return runningSince;
   }

   // R/W managed attributes ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

   public String getSessionFactoryName()
   {
      return sessionFactoryName;
   }

   public void setSessionFactoryName(String sessionFactoryName)
   {
      this.sessionFactoryName = sessionFactoryName;
      dirty = true;
   }

   public String getDatasourceName()
   {
      return datasourceName;
   }

   public void setDatasourceName(String datasourceName)
   {
      this.datasourceName = datasourceName;
      dirty = true;
   }

   public String getUsername()
   {
      return username;
   }

   public void setUsername(String username)
   {
      this.username = username;
      dirty = true;
   }

   public void setPassword(String password)
   {
      this.password = password;
      dirty = true;
   }

   public String getDefaultSchema()
   {
      return defaultSchema;
   }

   public void setDefaultSchema(String defaultSchema)
   {
      this.defaultSchema = defaultSchema;
      dirty = true;
   }

   public String getDefaultCatalog()
   {
      return defaultCatalog;
   }

   public void setDefaultCatalog(String defaultCatalog)
   {
      this.defaultCatalog = defaultCatalog;
   }

   public String getHbm2ddlAuto()
   {
      return hbm2ddlAuto;
   }

   public void setHbm2ddlAuto(String hbm2ddlAuto)
   {
      this.hbm2ddlAuto = hbm2ddlAuto;
      dirty = true;
   }

   public String getDialect()
   {
      return dialect;
   }

   public void setDialect(String dialect)
   {
      this.dialect = dialect;
      dirty = true;
   }

   public Integer getMaxFetchDepth()
   {
      return maxFetchDepth;
   }

   public void setMaxFetchDepth(Integer maxFetchDepth)
   {
      this.maxFetchDepth = maxFetchDepth;
      dirty = true;
   }

   public Integer getJdbcBatchSize()
   {
      return jdbcBatchSize;
   }

   public void setJdbcBatchSize(Integer jdbcBatchSize)
   {
      this.jdbcBatchSize = jdbcBatchSize;
      dirty = true;
   }

   public Integer getJdbcFetchSize()
   {
      return jdbcFetchSize;
   }

   public void setJdbcFetchSize(Integer jdbcFetchSize)
   {
      this.jdbcFetchSize = jdbcFetchSize;
      dirty = true;
   }

   public Boolean getJdbcScrollableResultSetEnabled()
   {
      return jdbcScrollableResultSetEnabled;
   }

   public void setJdbcScrollableResultSetEnabled(Boolean jdbcScrollableResultSetEnabled)
   {
      this.jdbcScrollableResultSetEnabled = jdbcScrollableResultSetEnabled;
      dirty = true;
   }

   public Boolean getGetGeneratedKeysEnabled()
   {
      return getGeneratedKeysEnabled;
   }

   public void setGetGeneratedKeysEnabled(Boolean getGeneratedKeysEnabled)
   {
      this.getGeneratedKeysEnabled = getGeneratedKeysEnabled;
      dirty = true;
   }

   public String getQuerySubstitutions()
   {
      return querySubstitutions;
   }

   public void setQuerySubstitutions(String querySubstitutions)
   {
      this.querySubstitutions = querySubstitutions;
      dirty = true;
   }

   public Boolean getSecondLevelCacheEnabled()
   {
      return secondLevelCacheEnabled;
   }

   public void setSecondLevelCacheEnabled(Boolean secondLevelCacheEnabled)
   {
      this.secondLevelCacheEnabled = secondLevelCacheEnabled;
      dirty = true;
   }

   public Boolean getQueryCacheEnabled()
   {
      return queryCacheEnabled;
   }

   public void setQueryCacheEnabled(Boolean queryCacheEnabled)
   {
      this.queryCacheEnabled = queryCacheEnabled;
      dirty = true;
   }

   public String getCacheProviderClass()
   {
      return cacheProviderClass;
   }

   public void setCacheProviderClass(String cacheProviderClass)
   {
      this.cacheProviderClass = cacheProviderClass;
      dirty = true;
   }

   /**
    * @see org.jboss.hibernate.jmx.HibernateMBean#getCacheRegionFactoryClass()
    */
   public String getCacheRegionFactoryClass()
   {
      return this.cacheRegionFactoryClass;
   }

   /**
    * @see org.jboss.hibernate.jmx.HibernateMBean#setCacheRegionFactoryClass(java.lang.String)
    */
   public void setCacheRegionFactoryClass(String regionFactoryClass)
   {
      this.cacheRegionFactoryClass = regionFactoryClass;
      dirty = true;
   }

   public String getCacheRegionPrefix()
   {
      return cacheRegionPrefix;
   }

   public void setCacheRegionPrefix(String cacheRegionPrefix)
   {
      this.cacheRegionPrefix = cacheRegionPrefix;
      dirty = true;
   }

   public Boolean getMinimalPutsEnabled()
   {
      return minimalPutsEnabled;
   }

   public void setMinimalPutsEnabled(Boolean minimalPutsEnabled)
   {
      this.minimalPutsEnabled = minimalPutsEnabled;
      dirty = true;
   }

   public Boolean getUseStructuredCacheEntriesEnabled()
   {
      return structuredCacheEntriesEnabled;
   }

   public void setUseStructuredCacheEntriesEnabled(Boolean structuredCacheEntriesEnabled)
   {
      this.structuredCacheEntriesEnabled = structuredCacheEntriesEnabled;
   }

   public Boolean getShowSqlEnabled()
   {
      return showSqlEnabled;
   }

   public void setShowSqlEnabled(Boolean showSqlEnabled)
   {
      this.showSqlEnabled = showSqlEnabled;
      dirty = true;
   }

   public Boolean getSqlCommentsEnabled()
   {
      return sqlCommentsEnabled;
   }

   public void setSqlCommentsEnabled(Boolean commentsEnabled)
   {
      this.sqlCommentsEnabled = commentsEnabled;
   }

   public String getSessionFactoryInterceptor()
   {
      return sessionFactoryInterceptor;
   }

   public void setSessionFactoryInterceptor(String sessionFactoryInterceptor)
   {
      this.sessionFactoryInterceptor = sessionFactoryInterceptor;
      dirty = true;
   }

   public String getListenerInjector()
   {
      return listenerInjector;
   }

   public void setListenerInjector(String listenerInjector)
   {
      this.listenerInjector = listenerInjector;
   }

   /**
    * @see org.jboss.hibernate.jmx.HibernateMBean#getDeployedCacheJndiName()
    */
   public String getDeployedCacheJndiName()
   {
      return this.deployedCacheJndiName;
   }

   /**
    * @see org.jboss.hibernate.jmx.HibernateMBean#setDeployedCacheJndiName(java.lang.String)
    */
   public void setDeployedCacheJndiName(String name)
   {
      this.deployedCacheJndiName = name;
      dirty = true;
   }

   /**
    * @see org.jboss.hibernate.jmx.HibernateMBean#getDeployedCacheManagerJndiName()
    */
   public String getDeployedCacheManagerJndiName()
   {
      return this.deployedCacheManagerJndiName;
   }

   /**
    * @see org.jboss.hibernate.jmx.HibernateMBean#setDeployedCacheManagerJndiName(java.lang.String)
    */
   public void setDeployedCacheManagerJndiName(String name)
   {
      this.deployedCacheManagerJndiName = name;
      dirty = true;
   }

   public Boolean getBatchVersionedDataEnabled()
   {
      return batchVersionedDataEnabled;
   }

   public void setBatchVersionedDataEnabled(Boolean batchVersionedDataEnabled)
   {
      this.batchVersionedDataEnabled = batchVersionedDataEnabled;
      this.dirty = true;
   }

   public Boolean getStreamsForBinaryEnabled()
   {
      return streamsForBinaryEnabled;
   }

   public void setStreamsForBinaryEnabled(Boolean streamsForBinaryEnabled)
   {
      this.streamsForBinaryEnabled = streamsForBinaryEnabled;
      this.dirty = true;
   }

   public Boolean getReflectionOptimizationEnabled()
   {
      return reflectionOptimizationEnabled;
   }

   public void setReflectionOptimizationEnabled(Boolean reflectionOptimizationEnabled)
   {
      this.reflectionOptimizationEnabled = reflectionOptimizationEnabled;
      this.dirty = true;
   }

   public Boolean getStatGenerationEnabled()
   {
      return statGenerationEnabled;
   }

   public void setStatGenerationEnabled(Boolean statGenerationEnabled)
   {
      this.statGenerationEnabled = statGenerationEnabled;
   }

   public URL getHarUrl()
   {
      return harUrl;
   }

   public void setHarUrl(URL harUrl)
   {
      this.harUrl = harUrl;
      dirty = true;
   }

   public boolean isScanForMappingsEnabled()
   {
      return scanForMappingsEnabled;
   }

   public void setScanForMappingsEnabled(boolean scanForMappingsEnabled)
   {
      this.scanForMappingsEnabled = scanForMappingsEnabled;
   }
}
