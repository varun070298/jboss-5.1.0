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

package org.jboss.test.cluster.web;

import java.util.StringTokenizer;

import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.jboss.test.AbstractTestDelegate;
import org.jboss.test.AbstractTestSetup;
import org.jboss.test.JBossClusteredTestCase;
import org.jboss.test.JBossTestCase;
import org.jboss.test.JBossTestClusteredServices;
import org.jboss.test.JBossTestSetup;

/**
 * @author Brian Stansberry
 *
 */
public class JBossClusteredWebTestCase extends JBossClusteredTestCase
{

   public static AbstractTestDelegate getDelegate(Class clazz)
       throws Exception
   {
       return new JBossTestClusteredServices(clazz);
   }
   
   public JBossClusteredWebTestCase(String name)
   {
      super(name);
   } 
   
   public String getCacheConfigName()
   {
      return System.getProperty(CacheHelper.CACHE_CONFIG_PROP);
   }

   public static Test getDeploySetup(Test test, String jarNames)
       throws Exception
   {
       return new JBossClusteredWebTestSetup(test, jarNames);
   }

   public static Test getDeploySetup(Class clazz, String jarNames)
       throws Exception
   {
       TestSuite suite = new TestSuite();
       suite.addTest(new TestSuite(clazz));
       return getDeploySetup(suite, jarNames);
   }
   
   private static String getJarNamesWithHelper(String jarNames)
   {
      if (jarNames == null || jarNames.length() == 0)
         return "jbosscache-helper.sar";
      else
         return "jbosscache-helper.sar, " + jarNames;
   }
   
   public static class JBossClusteredWebTestSetup extends JBossTestSetup
   {
      public static final String SYSTEM_PROPS_SVC = "jboss:type=Service,name=SystemProperties";
      
      private String cacheConfigName;
      private String usePojoCache;
      private String jarNames = null;
      private JBossTestClusteredServices clusteredServices;
      
      /**
       * Create a new JBossTestClusteredWebSetup.
       * 
       * @param test
       * @param jarNames
       * @throws Exception
       */
      public JBossClusteredWebTestSetup(Test test, String jarNames) throws Exception
      {
         super(JBossClusteredWebTestCase.class, test);
         this.jarNames = getJarNamesWithHelper(jarNames);
      }
   
      @Override
      protected void setUp() throws Exception
      {      
         super.setUp();        
         
         getLog().debug("delegate is " + delegate);
         
         clusteredServices = (JBossTestClusteredServices) delegate;
         
         cacheConfigName = System.getProperty(CacheHelper.CACHE_CONFIG_PROP);  
         usePojoCache = System.getProperty(CacheHelper.CACHE_TYPE_PROP, "false");
         if (cacheConfigName != null || Boolean.parseBoolean(usePojoCache))
         {
            setServerSideCacheConfigProperties();
         }
         
         deployJars();
      }
   
      @Override
      protected void tearDown() throws Exception
      {
         try
         {
            undeployJars();
            
            super.tearDown();
            if (cacheConfigName != null)
            {
               clearServerSideCacheConfigProperties();
            }
         }
         finally
         {
            AbstractTestSetup.delegate = null;
         }
      }
   
      private void setServerSideCacheConfigProperties() throws Exception
      {
         getLog().debug("configuring server with cacheConfigName=" + cacheConfigName + " and usePojoCache=" + usePojoCache);
         
         ObjectName on = new ObjectName(SYSTEM_PROPS_SVC);
         for (MBeanServerConnection adaptor : clusteredServices.getAdaptors())
         {
            adaptor.invoke(on, "set", 
                           new Object[]{CacheHelper.CACHE_CONFIG_PROP, cacheConfigName}, 
                           new String[] {String.class.getName(), String.class.getName()});

            adaptor.invoke(on, "set", 
                           new Object[]{CacheHelper.CACHE_TYPE_PROP, usePojoCache}, 
                           new String[] {String.class.getName(), String.class.getName()});
         }         
      }
   
      private void clearServerSideCacheConfigProperties() throws Exception
      {
         ObjectName on = new ObjectName(SYSTEM_PROPS_SVC);
         for (MBeanServerConnection adaptor : clusteredServices.getAdaptors())
         {
            adaptor.invoke(on, "remove", 
                           new Object[]{CacheHelper.CACHE_CONFIG_PROP}, 
                           new String[] {String.class.getName()});

            adaptor.invoke(on, "remove", 
                           new Object[] {CacheHelper.CACHE_TYPE_PROP}, 
                           new String[] {String.class.getName()});
         } 
      }
      
      private void deployJars() throws Exception
      {      
         JBossTestCase.deploymentException = null;
         try
         {
            // deploy the comma seperated list of jars
            StringTokenizer st = new StringTokenizer(jarNames, ", ");
            while (st.hasMoreTokens())
            {
               String jarName = st.nextToken();
               this.redeploy(jarName);
               this.getLog().debug("deployed package: " + jarName);
            }
         }
         catch (Exception ex)
         {
            // Throw this in testServerFound() instead.
            JBossTestCase.deploymentException = ex;
         }
             
         // wait a couple seconds to let the cluster stabilize
         synchronized (this)
         {
            wait(2000);
         }
      }
      
      private void undeployJars() throws Exception
      {
         // deploy the comma seperated list of jars
         StringTokenizer st = new StringTokenizer(jarNames, ", ");
         String[] depoyments = new String[st.countTokens()];
         for (int i = depoyments.length - 1; i >= 0; i--)
            depoyments[i] = st.nextToken();
         for (int i = 0; i < depoyments.length; i++)
         {
            String jarName = depoyments[i];
            this.getLog().debug("Attempt undeploy of " + jarName);
            this.undeploy(jarName);
            this.getLog().debug("undeployed package: " + jarName);
         }   
      }
   
   }

}
