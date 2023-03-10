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
package org.jboss.test.profileservice.test;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.InitialContext;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.jboss.deployers.spi.management.KnownComponentTypes;
import org.jboss.deployers.spi.management.KnownDeploymentTypes;
import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.deployers.spi.management.deploy.DeploymentManager;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.DeploymentTemplateInfo;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedDeployment;
import org.jboss.managed.api.ManagedOperation;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.managed.api.RunState;
import org.jboss.managed.api.annotation.ManagementProperty;
import org.jboss.managed.api.annotation.ViewUse;
import org.jboss.metatype.api.types.CompositeMetaType;
import org.jboss.metatype.api.types.MapCompositeMetaType;
import org.jboss.metatype.api.types.MetaType;
import org.jboss.metatype.api.types.SimpleMetaType;
import org.jboss.metatype.api.values.CompositeValue;
import org.jboss.metatype.api.values.CompositeValueSupport;
import org.jboss.metatype.api.values.MapCompositeValueSupport;
import org.jboss.metatype.api.values.MetaValue;
import org.jboss.metatype.api.values.SimpleValueSupport;
import org.jboss.metatype.plugins.types.MutableCompositeMetaType;
import org.jboss.profileservice.spi.NoSuchProfileException;
import org.jboss.profileservice.spi.ProfileKey;
import org.jboss.profileservice.spi.ProfileService;
import org.jboss.virtual.VFS;

/** Test of using ProfileService

 @author Scott.Stark@jboss.org
 @version $Revision: 88365 $
 */
public class ProfileServiceUnitTestCase extends AbstractProfileServiceTest
{
   /** The profileservice server name. */
   public static final String PROFILESERVICE_SERVER_NAME = "profileservice";
   
   /**
    * We need to define the order in which tests runs
    * @return
    * @throws Exception
    */
   public static Test suite() throws Exception
   {
      TestSuite suite = new TestSuite();

      suite.addTest(new ProfileServiceUnitTestCase("testProfileKeys"));
      suite.addTest(new ProfileServiceUnitTestCase("testDeploymentNames"));
      suite.addTest(new ProfileServiceUnitTestCase("testIgnoredDeploymentNames"));
      
      suite.addTest(new ProfileServiceUnitTestCase("testTemplateNames"));
      suite.addTest(new ProfileServiceUnitTestCase("testNoSuchProfileException"));
      // JMS
      suite.addTest(new ProfileServiceUnitTestCase("testJmsDestinationComponents"));
      // DataSource
      suite.addTest(new ProfileServiceUnitTestCase("testLocalTxDataSourceTemplatePropertiesAreMetaValues"));
      suite.addTest(new ProfileServiceUnitTestCase("testXADataSourceTemplateTemplatePropertiesAreMetaValues"));
      suite.addTest(new ProfileServiceUnitTestCase("testDataSourceDeploymentType"));
      suite.addTest(new ProfileServiceUnitTestCase("testListDataSourceComponents"));
      suite.addTest(new ProfileServiceUnitTestCase("testUpdateDefaultDS"));
      suite.addTest(new ProfileServiceUnitTestCase("testDefaultDSOps"));
      suite.addTest(new ProfileServiceUnitTestCase("testDefaultDSStats"));
      suite.addTest(new ProfileServiceUnitTestCase("testAddDataSource"));
      suite.addTest(new ProfileServiceUnitTestCase("testRemoveDataSource"));
      suite.addTest(new ProfileServiceUnitTestCase("testAddXADataSource"));
      suite.addTest(new ProfileServiceUnitTestCase("testRemoveXADataSource"));
      suite.addTest(new ProfileServiceUnitTestCase("testAddTxConnectionFactory"));
      suite.addTest(new ProfileServiceUnitTestCase("testRemoveTxConnectionFactory"));
      suite.addTest(new ProfileServiceUnitTestCase("testAddTxXAConnectionFactory"));
      suite.addTest(new ProfileServiceUnitTestCase("testRemoveTxXAConnectionFactory"));
      suite.addTest(new ProfileServiceUnitTestCase("testAddNoTxConnectionFactory"));
      suite.addTest(new ProfileServiceUnitTestCase("testRemoveNoTxConnectionFactory"));
      suite.addTest(new ProfileServiceUnitTestCase("testAddNoTxDataSource"));
      suite.addTest(new ProfileServiceUnitTestCase("testRemoveNoTxDataSource"));

      return suite;
   }

   public ProfileServiceUnitTestCase(String name)
   {
      super(name);
   }

   /**
    * Basic test of accessing the ProfileService and checking the
    * available profile keys.
    * 
    * As we are running the -c profileservice, the default key should be profileservice.
    * 
    */
   public void testProfileKeys()
      throws Exception
   {
      InitialContext ctx = super.getInitialContext();
      ProfileService ps = (ProfileService) ctx.lookup("ProfileService");
      Collection<ProfileKey> keys = ps.getActiveProfileKeys();
      log.info("getProfileKeys: "+keys);
      ProfileKey defaultKey = new ProfileKey(PROFILESERVICE_SERVER_NAME);
      assertTrue("keys contains profileservice", keys.contains(defaultKey));
   }

   /**
    * Validate some of the expected deployment names. This test will have
    * to be updated as the profile repository format evolves, and descriptors
    * change.
    * @throws Exception
    */
   public void testDeploymentNames()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNames();
      log.info("getDeploymentNames, "+names);
      /* Build a set of the simple deployment names with their immeadiate
       * parent directory
       */
      HashSet<String> simpleNames = new HashSet<String>();
      for (String name : names)
      {
         // Look for /server/profileservice/
         String serverName = "/server/"+ PROFILESERVICE_SERVER_NAME + "/";
         int index = name.indexOf(serverName);
         if (index == -1)
         {
            // ignore names from /testsuite/output/lib for now
            continue;
         }

         String sname = name.substring(index + serverName.length(), name.length());
         simpleNames.add(sname);
      }
      
      log.info("Deployment simple names: " + simpleNames);
      // Validate some well known deployments
      String[] expectedNames = {
         "conf/bootstrap/aop.xml",
         "conf/bootstrap/classloader.xml",
         "conf/bootstrap/deployers.xml",
         "conf/bootstrap/jmx.xml",
         "conf/bootstrap/profile.xml", 
         "conf/jboss-service.xml",
         "deployers/jbossweb.deployer/",
    		"deployers/ear-deployer-jboss-beans.xml",
    		"deployers/jbossws.deployer/",
    		"deployers/ejb-deployer-jboss-beans.xml",
    		"deployers/ejb3.deployer/",
    		"deployers/jboss-aop-jboss5.deployer/",
    		"deployers/security-deployer-jboss-beans.xml",
    		"deployers/jboss-jca.deployer/",
    		"deploy/hsqldb-ds.xml",
    		"deploy/jboss-local-jdbc.rar/",
    		"deploy/jboss-xa-jdbc.rar/",
    		"deploy/jca-jboss-beans.xml",
    		"deploy/jbossws.sar/",
         "deploy/messaging/connection-factories-service.xml",
         "deploy/messaging/destinations-service.xml",
    		"deploy/jms-ra.rar/",
    		"deploy/jmx-console.war/",
    		"deploy/jmx-invoker-service.xml",
    		"deploy/jmx-remoting.sar/",
    		"deploy/jsr88-service.xml",
    		"deploy/mail-service.xml",
    		"deploy/ROOT.war/"
      };
      TreeSet<String> missingNames = new TreeSet<String>();
      for (String name : expectedNames)
      {
         if(simpleNames.contains(name) == false)
            missingNames.add(name);
      }
      assertEquals("There are missing names: "+missingNames + ", available: " + simpleNames, 0, missingNames.size());
   }
   
   public void testManagedDeployments()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNames();
      for(String name : names)
      {
         ManagedDeployment deployment = mgtView.getDeployment(name);
         log.info(deployment);
      }
   }

   /**
    * Test that ignored deployments are not deployed.
    * e.g. ignore.war.bak in the deploy dir
    * 
    * @throws Exception
    */
   public void testIgnoredDeploymentNames() throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNames();
      for(String name : names)
      {
         if(name.endsWith(".bak"))
            fail("deployment should be ignored: " + name);
      }
   }

   /** 
    * Test the expected template names
    * @throws Exception
    */
   public void testTemplateNames()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getTemplateNames();
      log.info("getTemplateNames, "+names);
      assertTrue("TopicTemplate exists", names.contains("TopicTemplate"));
      assertTrue("QueueTemplate exists", names.contains("QueueTemplate"));
      assertTrue("LocalTxDataSourceTemplate exists", names.contains("LocalTxDataSourceTemplate"));
      assertTrue("TxConnectionFactoryTemplate exists", names.contains("TxConnectionFactoryTemplate"));
      assertTrue("NoTxConnectionFactoryTemplate exists", names.contains("NoTxConnectionFactoryTemplate"));
   }

   /**
    * Basic test to validate NoSuchProfileException is thrown for
    * a ProfileKey that does not map to a profile.
    *
    */
   public void testNoSuchProfileException()
      throws Exception
   {
      DeploymentManager deployMgr = getDeploymentManager(); 
      ProfileKey badKey = new ProfileKey("no-such-profile");
      try
      {
         deployMgr.loadProfile(badKey);
         fail("Did not see NoSuchProfileException");
      }
      catch(NoSuchProfileException e)
      {
         assertTrue("NoSuchProfileException", e.getMessage().contains("no-such-profile"));
      }
   }

   /**
    * Test that one can query for deployments of type jca-ds to
    * obtain data source deployments.
    * TODO: don't know if we should keep this
   */
   public void testDataSourceDeploymentType()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNamesForType("jca-ds");
      log.info("jca-ds names: "+names);
      assertTrue("names.size > 0 ", names.size() > 0);
      boolean sawHsqldbDS = false;
      for (String name : names)
      {
         sawHsqldbDS = name.endsWith("hsqldb-ds.xml");
         if (sawHsqldbDS)
            break;
      }
      assertTrue("Saw hsqldb-ds.xml in names", sawHsqldbDS);
   }

   public void testWarDeploymentType()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNamesForType(KnownDeploymentTypes.JavaEEWebApplication.getType());
      log.info("war names: "+names);
      assertTrue("names.size > 0 ", names.size() > 0);
      boolean sawRootWar = false;
      for (String name : names)
      {
         sawRootWar = name.endsWith("ROOT.war") || name.endsWith("ROOT.war/");
         if (sawRootWar)
            break;
      }
      assertTrue("Saw ROOT.war in names", sawRootWar);
   }
   public void testSarDeploymentType()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNamesForType(KnownDeploymentTypes.JBossServices.getType());
      log.info("sar names: "+names);
      assertTrue("names.size > 0 ", names.size() > 0);
      // To check for a jbossweb.sar
      boolean sawJbosswebSar = false;
      // To check for a transaction-service.xml
      boolean sawTSService = false;
      for (String name : names)
      {
         if(sawJbosswebSar == false)
            sawJbosswebSar = name.endsWith("jbossweb.sar") || name.endsWith("jbossweb.sar/");
         if(sawTSService == false)
            sawTSService = name.endsWith("transaction-service.xml");
      }
      assertTrue("Saw jbossweb.sar in names", sawJbosswebSar);
      assertTrue("Saw transaction-service.xml in names", sawTSService);
   }

   public void testMCBeansDeploymentType()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      Set<String> names = mgtView.getDeploymentNamesForType(KnownDeploymentTypes.MCBeans.getType());
      log.info("beans names: "+names);
      assertTrue("names.size > 0 ", names.size() > 0);
      // To check for a jbossweb.deployer
      boolean sawJbosswebDeployer = false;
      // To check for a profileservice-beans.xml
      boolean sawPSBeans = false;
      // To check for a security-beans.xml
      boolean sawSecurityBeans = false;
      for (String name : names)
      {
         if(sawJbosswebDeployer == false)
            sawJbosswebDeployer = name.endsWith("jbossweb.deployer") || name.endsWith("jbossweb.deployer/");
         if(sawPSBeans == false)
            sawPSBeans = name.endsWith("profileservice-beans.xml");
         if(sawSecurityBeans == false)
            sawSecurityBeans = name.endsWith("security-beans.xml");
      }
      assertTrue("Saw jbossweb.deployer in names", sawJbosswebDeployer);
      assertTrue("Saw profileservice-beans.xml in names", sawPSBeans);
      assertTrue("Saw security-beans.xml in names", sawSecurityBeans);
   }

   /**
    * test api usage only
    * @throws Exception
    */   
   public void testRemoveComponent () throws Exception
   {
	   String componentName = "defaultDS";
	   ManagementView mgtView = getManagementView();
	   
	   ComponentType type = new ComponentType("DataSource", "LocalTx");
	   Set<ManagedComponent> comps = mgtView.getComponentsForType(type);
	   
	   // maybe a mgtView.getComponentByNameAndType(type, componentName) would be helpful
	   // i'm assuming componentName and type will be unique in a given profile.
	   
	   if (comps != null) 
	   {
		   for (ManagedComponent comp : comps)
		   {
			  if (componentName.equals(comp.getName()))
			  {
				  ManagedDeployment deployment = comp.getDeployment();
				  deployment.removeComponent(componentName); 
				  break;
			  }		 
		   }
	   }
   }

   /**
    * Query for the ComponentType("DataSource", "LocalTx")
    * and validate the expected property names.
    * @throws Exception
    */
   public void testListDataSourceComponents() throws Exception
   {
      log.info("+++ testListDataSourceComponents");

      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("DataSource", "LocalTx");
      ManagedComponent ds = getManagedComponent(mgtView, type, "DefaultDS");
      assertNotNull("hsqldb-ds.xml ManagedComponent", ds);
      Map<String,ManagedProperty> props = ds.getProperties();
      log.info("hsqldb-ds.props: "+props);
      // Validate the property names
      ManagedProperty p = props.get("jndi-name");
      assertEquals("jndi-name", SimpleValueSupport.wrap("DefaultDS"), p.getValue());
      p = props.get("driver-class");
      assertEquals("driver-class", SimpleValueSupport.wrap("org.hsqldb.jdbcDriver"), p.getValue());
      p = props.get("connection-url");
      assertEquals("connection-url", SimpleValueSupport.wrap("jdbc:hsqldb:${jboss.server.data.dir}${/}hypersonic${/}localDB"), p.getValue());
      p = props.get("user-name");
      assertEquals("user-name", SimpleValueSupport.wrap("sa"), p.getValue());
      p = props.get("password");
      assertEquals("password", SimpleValueSupport.wrap(""), p.getValue());
      p = props.get("min-pool-size");
      assertEquals("min-pool-size", SimpleValueSupport.wrap(5), p.getValue());
      p = props.get("max-pool-size");
      assertEquals("max-pool-size", SimpleValueSupport.wrap(20), p.getValue());
      p = props.get("idle-timeout-minutes");
      assertEquals("idle-timeout-minutes", SimpleValueSupport.wrap(0), p.getValue());
      p = props.get("prepared-statement-cache-size");
      assertEquals("prepared-statement-cache-size", SimpleValueSupport.wrap(32), p.getValue());
/*
      TODO - Uncomment when Weston has ManagedConnectionFactoryDeploymentMetaData/DBMSMetaData done
      p = props.get("type-mapping");
      assertEquals("type-mapping", SimpleValueSupport.wrap("Hypersonic SQL"), p.getValue());
*/
      p = props.get("security-domain");
      assertNotNull("security-domain", p);

      CompositeMetaType secType = (CompositeMetaType) p.getMetaType();
      assertNotNull(secType);
      assertTrue(secType.containsItem("domain"));
      assertTrue(secType.containsItem("securityDeploymentType"));

      log.info("security-domain: "+secType);
   }

   /**
    * Query for the ComponentType("JMSDestination", "Queue"/"Topic") types.
    * @throws Exception
    */
   public void testJmsDestinationComponents()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      ComponentType queueType = new ComponentType("JMSDestination", "Queue");
      Set<ManagedComponent> queues = mgtView.getComponentsForType(queueType);
      log.info("queues: "+queues);
      assertNotNull("Null JMS queues", queues);
      assertTrue("queues.size", queues.size() > 0);

      ComponentType topicType = new ComponentType("JMSDestination", "Topic");
      Set<ManagedComponent> topics = mgtView.getComponentsForType(topicType);
      log.info("topics: "+topics);
      assertNotNull(topics);
      assertTrue("topics.size", topics.size() > 0);
   }

   /**
    * Test an update of the hsqldb-ds.xml min/max pool size. This queries
    * the mbeans to validate runtime changes to the min/max pool size. This
    * couples the test to the deployment implementation details.
    * 
    * @throws Exception
    */
   public void testUpdateDefaultDS()
      throws Exception
   {
      log.info("+++ testUpdateDefaultDS");
      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("DataSource", "LocalTx");
      ManagedComponent hsqldb = mgtView.getComponent("DefaultDS", type);
      Map<String,ManagedProperty> props = hsqldb.getProperties();
      log.info("hsqldb.props: "+props);
      // Update properties
      ManagedProperty minSize = props.get("min-pool-size");
      minSize.setValue(SimpleValueSupport.wrap(new Integer(13)));
      ManagedProperty maxSize = props.get("max-pool-size");
      maxSize.setValue(SimpleValueSupport.wrap(new Integer(53)));

      mgtView.updateComponent(hsqldb);

      // TODO: Query the mbeans to validate the change
      // TODO: Query the profile service repository for the overriden data
   }

   /**
    * Validate that the LocalTxDataSourceTemplate ManagedPropertys are values are of type MetaValue
    * @throws Exception
    */
   public void testLocalTxDataSourceTemplatePropertiesAreMetaValues()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      DeploymentTemplateInfo dsInfo = mgtView.getTemplate("LocalTxDataSourceTemplate");
      Map<String,ManagedProperty> props = dsInfo.getProperties();
      validatePropertyMetaValues(props);
   }

   /**
    * Validate that there is only 1 DefaultDS ManagedComponent
    * @throws Exception
    */
   public void testDefaultDSComponentCount()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("DataSource", "LocalTx");
      Set<ManagedComponent> comps = mgtView.getComponentsForType(type);
      int count = 0;
      for (ManagedComponent comp : comps)
      {
        String cname = comp.getName();
        if( cname.endsWith("DefaultDS") )
        {
           count ++;
        }
      }
      assertEquals("There is 1 DefaultDS ManagedComponent", 1, 1);
   }

   /**
    * Validate that all ManagedPropertys are values are of type MetaValue
    * @throws Exception
    */
   public void testDefaultDSPropertiesAreMetaValues()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("DataSource", "LocalTx");
      ManagedComponent hsqldb = mgtView.getComponent("DefaultDS", type);
      Map<String,ManagedProperty> props = hsqldb.getProperties();
      validatePropertyMetaValues(props);

      // Validate the config-property
      ManagedProperty configProperty = hsqldb.getProperty("config-property");
      assertNotNull(configProperty);
      MetaValue value = configProperty.getValue();
      assertTrue("MapCompositeMetaType", value.getMetaType() instanceof MapCompositeMetaType);
      log.debug("config-property: "+configProperty);
      assertTrue(value instanceof CompositeValue);
      log.debug("config-property.value: "+value);


      // Validate more details on specific properties
      ManagedProperty securityDomain = props.get("security-domain");
      assertNotNull("security-domain", securityDomain);
      MetaType securityDomainType = securityDomain.getMetaType();
      assertTrue("security-domain type is a GenericMetaType", securityDomainType instanceof CompositeMetaType);
      log.debug("security-domain type: "+securityDomainType);
      MetaValue securityDomainValue = securityDomain.getValue();
      assertTrue("security-domain value is a GenericValue", securityDomainValue instanceof CompositeValue);
      log.debug("security-domain value: "+securityDomainValue);
   }
   
   /**
    * Validate the DefaultDS stats that are non-null
    * 
    * @throws Exception
    */
   public void testDefaultDSStats()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("DataSource", "LocalTx");
      ManagedComponent hsqldb = mgtView.getComponent("DefaultDS", type);
      Map<String,ManagedProperty> props = hsqldb.getProperties();
      ArrayList<ManagedProperty> stats = new ArrayList<ManagedProperty>();
      for(ManagedProperty prop : props.values())
      {
         Map<String, Annotation> annotations = prop.getAnnotations();
         if(annotations == null)
            continue;
         ManagementProperty mpa = (ManagementProperty) annotations.get(ManagementProperty.class.getName());
         ViewUse[] uses = mpa.use();
         for(ViewUse use : uses)
         {
            if(use == ViewUse.STATISTIC)
            {
               log.info("STATISTIC: "+prop+", value: "+prop.getValue());
               stats.add(prop);
            }
         }
      }
      assertTrue("Saw ManagedProperty(ViewUse.STATISTIC)", stats.size() > 0);
      HashSet<String> nullStats = new HashSet<String>();
      // These should also have non-null values
      for(ManagedProperty stat : stats)
      {
         Object value = stat.getValue();
         if(value == null)
            nullStats.add(stat.getName());
      }
      assertTrue("There are no null stats: "+nullStats, nullStats.size() == 0);
      
      // Validate the component run state
      RunState state = hsqldb.getRunState();
      assertEquals("DefaultDS is running", RunState.RUNNING, state);
   }

   /**
    * Validate that the DefaultDS management ops work and that they have the
    * expected MetaValue return type.
    * 
    * @throws Exception
    */
   public void testDefaultDSOps()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("DataSource", "LocalTx");
      ManagedComponent hsqldb = mgtView.getComponent("DefaultDS", type);
      Set<ManagedOperation> ops = hsqldb.getOperations();
      log.info("DefaultDS ops: "+ops);
      assertNotNull("Set<ManagedOperation>", ops);
      assertTrue("Set<ManagedOperation> > 0", ops.size() > 0);
      ManagedOperation listFormattedSubPoolStatistics = null;
      HashMap<String, ManagedOperation> opsByName = new HashMap<String, ManagedOperation>();
      for (ManagedOperation op : ops)
      {
         opsByName.put(op.getName(), op);
      }
      // Validate the listFormattedSubPoolStatistics op
      listFormattedSubPoolStatistics = opsByName.get("listFormattedSubPoolStatistics");
      assertNotNull("listFormattedSubPoolStatistics", listFormattedSubPoolStatistics);
      MetaValue[] params = {};
      Object result = listFormattedSubPoolStatistics.invoke(params);
      assertNotNull("Expecting non null result", result);
      log.info("listFormattedSubPoolStatistics.invoke: "+result);
      // It needs to be a MetaValue as well
      assertTrue("result is a MetaValue", result instanceof MetaValue);
      // Validate the listStatistics op
      ManagedOperation listStatistics = opsByName.get("listStatistics");
      assertNotNull("listStatistics", listStatistics);
      result = listStatistics.invoke(params);
      assertNotNull("Expecting non null result", result);
      log.info("listStatistics.invoke: "+result);
      // It needs to be a MetaValue as well
      assertTrue("result is a MetaValue", result instanceof MetaValue);
   }

   /**
    * Test adding a new hsql DataSource deployment (JBAS-4671)
    * @throws Exception
    */
   public void testAddDataSource() throws Exception
   {
      String jndiName = "TestLocalTxDs";
      Map<String, MetaValue> propValues = new HashMap<String, MetaValue>();
      addNonXaDsProperties(propValues, jndiName, "jboss-local-jdbc.rar", "javax.sql.DataSource");
      createComponentTest("LocalTxDataSourceTemplate", propValues, "testLocalTxDs",
         KnownComponentTypes.DataSourceTypes.LocalTx.getType(), jndiName);
   }

   public void testRemoveDataSource()
      throws Exception
   {
      removeDeployment("testLocalTxDs-ds.xml");
   }

   /**
    * Validate that the XADataSourceTemplate ManagedPropertys are values are of type MetaValue
    * @throws Exception
    */
   public void testXADataSourceTemplateTemplatePropertiesAreMetaValues()
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      DeploymentTemplateInfo dsInfo = mgtView.getTemplate("XADataSourceTemplate");
      Map<String,ManagedProperty> props = dsInfo.getProperties();
      validatePropertyMetaValues(props);
   }
   /**
    * Test adding a new hsql DataSource deployment
    * @throws Exception
    */
   public void testAddXADataSource() throws Exception
   {
      String jndiName = "TestXaDs";
      Map<String, MetaValue> propValues = new HashMap<String, MetaValue>();

      addCommonDsProperties(propValues, jndiName, "jboss-xa-jdbc.rar", "javax.sql.DataSource");

      propValues.put("xa-datasource-class", SimpleValueSupport.wrap("org.hsqldb.jdbcDriver"));
      propValues.put("xa-resource-timeout", SimpleValueSupport.wrap(new Integer(256)));

      HashMap<String, String> xaPropValues = new HashMap<String, String>();
      xaPropValues.put("URL", "jdbc:hsqldb");
      xaPropValues.put("User", "sa");
      xaPropValues.put("Password", "");

      //MetaValue metaValue = getMetaValueFactory().create(xaPropValues, getMapType());
      MetaValue metaValue = this.compositeValueMap(xaPropValues);
      propValues.put("xa-datasource-properties", metaValue);

      createComponentTest("XADataSourceTemplate", propValues, "testXaDs",
         KnownComponentTypes.DataSourceTypes.XA.getType(), jndiName);
   }

   /**
    * removes the XADataSource created in the testAddXADataSource
    * @throws Exception
    */
   public void testRemoveXADataSource()
      throws Exception
   {
      removeDeployment("testXaDs-ds.xml");
   }

   /**
    * Test adding a new tx-connection-factory deployment
    * @throws Exception
    */
   public void testAddTxConnectionFactory() throws Exception
   {
      String jndiName = "TestTxCf";
      Map<String, MetaValue> propValues = new HashMap<String, MetaValue>();

      addCommonCfProperties(propValues, jndiName, "jms-ra.rar", "org.jboss.resource.adapter.jms.JmsConnectionFactory");

      Map<String, String> xaProps = new HashMap<String, String>();
      xaProps.put("SessionDefaultType", "javax.jms.Topic");
      xaProps.put("SessionDefaultType.type", "java.lang.String");
      xaProps.put("JmsProviderAdapterJNDI", "java:/DefaultJMSProvider");
      xaProps.put("JmsProviderAdapterJNDI.type", "java.lang.String");
      MetaValue metaValue = this.compositeValueMap(xaProps);

      propValues.put("config-property", metaValue);

      propValues.put("xa-transaction", SimpleValueSupport.wrap(Boolean.FALSE));
      propValues.put("xa-resource-timeout", SimpleValueSupport.wrap(new Integer(256)));

      // todo: how to set the specific domain?
      //ApplicationManagedSecurityMetaData secDomain = new ApplicationManagedSecurityMetaData();
      //props.get("security-domain").setValue(secDomain);

      createComponentTest("TxConnectionFactoryTemplate", propValues, "testTxCf",
         new ComponentType("ConnectionFactory", "Tx"), jndiName);
   }

   /**
    * removes the tx-connection-factory created in the testAddTxConnectionFactory
    * @throws Exception
    */
   public void testRemoveTxConnectionFactory()
      throws Exception
   {
      removeDeployment("testTxCf-ds.xml");
   }

   /**
    * Test adding a new tx-connection-factory deployment with xa enabled
    * @throws Exception
    */
   public void testAddTxXAConnectionFactory() throws Exception
   {
      String jndiName = "TestTxCf";
      Map<String, MetaValue> propValues = new HashMap<String, MetaValue>();

      addCommonCfProperties(propValues, jndiName, "jms-ra.rar", "org.jboss.resource.adapter.jms.JmsConnectionFactory");

      Map<String, String> xaProps = new HashMap<String, String>();
      xaProps.put("SessionDefaultType", "javax.jms.Topic");
      xaProps.put("SessionDefaultType.type", "java.lang.String");
      xaProps.put("JmsProviderAdapterJNDI", "java:/DefaultJMSProvider");
      xaProps.put("JmsProviderAdapterJNDI.type", "java.lang.String");
      MetaValue metaValue = this.compositeValueMap(xaProps);

      propValues.put("config-property", metaValue);

      propValues.put("xa-transaction", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("xa-resource-timeout", SimpleValueSupport.wrap(new Integer(256)));

      // todo: how to set the specific domain?
      //ApplicationManagedSecurityMetaData secDomain = new ApplicationManagedSecurityMetaData();
      //props.get("security-domain").setValue(secDomain);

      createComponentTest("TxConnectionFactoryTemplate", propValues, "testTxXACf",
         new ComponentType("ConnectionFactory", "Tx"), jndiName);
   }

   /**
    * removes the tx-connection-factory created in the testAddXAConnectionFactory
    * @throws Exception
    */
   public void testRemoveTxXAConnectionFactory()
      throws Exception
   {
      removeDeployment("testTxXACf-ds.xml");
   }

   /**
    * Test adding a new no-tx-datasource deployment (JBAS-4671)
    * @throws Exception
    */
   public void testAddNoTxDataSource() throws Exception
   {
      String jndiName = "TestNoTxDs";
      Map<String, MetaValue> propValues = new HashMap<String, MetaValue>();
      addNonXaDsProperties(propValues, jndiName, "jboss-local-jdbc.rar", "javax.sql.DataSource");
      createComponentTest("NoTxDataSourceTemplate", propValues, "testNoTxDs", KnownComponentTypes.DataSourceTypes.NoTx.getType(), jndiName);
   }

   public void testRemoveNoTxDataSource()
      throws Exception
   {
      removeDeployment("testNoTxDs-ds.xml");
   }

   /**
    * Test adding a new no-tx-connection-factory deployment
    * @throws Exception
    */
   public void testAddNoTxConnectionFactory() throws Exception
   {
      String jndiName = "TestNoTxCf";
      Map<String, MetaValue> propValues = new HashMap<String, MetaValue>();

      addCommonCfProperties(propValues, jndiName, "jms-ra.rar", "org.jboss.resource.adapter.jms.JmsConnectionFactory");

      Map<String, String> xaProps = new HashMap<String, String>();
      xaProps.put("SessionDefaultType", "javax.jms.Topic");
      xaProps.put("SessionDefaultType.type", "java.lang.String");
      xaProps.put("JmsProviderAdapterJNDI", "java:/DefaultJMSProvider");
      xaProps.put("JmsProviderAdapterJNDI.type", "java.lang.String");
      MetaValue metaValue = this.compositeValueMap(xaProps);
      propValues.put("config-property", metaValue);

      propValues.put("track-connection-by-tx", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("config-property", 
            new MapCompositeValueSupport(new HashMap<String, MetaValue>(),
                  new MapCompositeMetaType(SimpleMetaType.STRING)));
      // todo: how to set the specific domain?
      //ApplicationManagedSecurityMetaData secDomain = new ApplicationManagedSecurityMetaData();
      //props.get("security-domain").setValue(secDomain);

      ComponentType compType = new ComponentType("ConnectionFactory", "NoTx");
      createComponentTest("NoTxConnectionFactoryTemplate", propValues, "testNoTxCf", compType, jndiName);

      // Validate the config-property
      ManagementView mgtView = getManagementView();
      ManagedComponent dsMC = getManagedComponent(mgtView, compType, jndiName);
      ManagedProperty configProperty = dsMC.getProperty("config-property");
      assertNotNull(configProperty);
      MetaValue value = configProperty.getValue();
      assertTrue("MapCompositeMetaType", value.getMetaType() instanceof MapCompositeMetaType);
      
      MapCompositeValueSupport cValue = (MapCompositeValueSupport) value;
      cValue.put("testKey", new SimpleValueSupport(SimpleMetaType.STRING, "testValue"));
      
      mgtView.updateComponent(dsMC);

      mgtView = getManagementView();
      dsMC = getManagedComponent(mgtView, compType, jndiName);
      configProperty = dsMC.getProperty("config-property");
      assertNotNull(configProperty);
      cValue = (MapCompositeValueSupport) configProperty.getValue();
      assertNotNull(cValue.get("testKey"));
   }

   /**
    * removes the tx-connection-factory created in the testAddTxConnectionFactory
    * @throws Exception
    */
   public void testRemoveNoTxConnectionFactory()
      throws Exception
   {
      removeDeployment("testNoTxCf-ds.xml");
   }


   // Private and protected

   private void addNonXaDsProperties(Map<String, MetaValue> propValues,
                                     String jndiName,
                                     String rarName,
                                     String conDef)
   {
      addCommonDsProperties(propValues, jndiName, rarName, conDef);

      propValues.put("transaction-isolation", SimpleValueSupport.wrap("TRANSACTION_SERIALIZABLE"));
      propValues.put("user-name", SimpleValueSupport.wrap("sa"));
      propValues.put("password", SimpleValueSupport.wrap(""));

      // non xa ds
      propValues.put("driver-class", SimpleValueSupport.wrap("org.hsqldb.jdbcDriver"));
      propValues.put("connection-url", SimpleValueSupport.wrap("jdbc:hsqldb:."));
      // A metadata type with a null type-mapping, JBAS-6215
      MutableCompositeMetaType metadataType = new MutableCompositeMetaType("org.jboss.resource.metadata.mcf.DBMSMetaData", "metadata type");
      metadataType.addItem("typeMapping", "The jdbc type mapping", SimpleMetaType.STRING);
      HashMap<String, MetaValue> items = new HashMap<String, MetaValue>();
      items.put("typeMapping", null);
      CompositeValueSupport metadata = new CompositeValueSupport(metadataType, items);
      propValues.put("metadata", metadata);

      // todo: connection-properties
   }

   private void addCommonDsProperties(Map<String, MetaValue> propValues,
                                              String jndiName,
                                              String rarName,
                                              String conDef)
   {
      addCommonCfProperties(propValues, jndiName, rarName, conDef);
      propValues.put("new-connection-sql", SimpleValueSupport.wrap("CALL ABS(2.0)"));
      propValues.put("check-valid-connection-sql", SimpleValueSupport.wrap("CALL ABS(1.0)"));
      propValues.put("valid-connection-checker-class-name", SimpleValueSupport.wrap("org.jboss.resource.adapter.jdbc.vendor.DummyValidConnectionChecker"));
      propValues.put("exception-sorter-class-name", SimpleValueSupport.wrap("org.jboss.resource.adapter.jdbc.vendor.DummyExceptionSorter"));
      propValues.put("stale-connection-checker-class-name", SimpleValueSupport.wrap("org.jboss.resource.adapter.jdbc.vendor.DummyValidConnectionChecker"));
      propValues.put("track-statements", SimpleValueSupport.wrap(""));
      propValues.put("prepared-statement-cache-size", SimpleValueSupport.wrap(12));
      propValues.put("share-prepared-statements", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("set-tx-query-timeout", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("query-timeout", SimpleValueSupport.wrap(new Integer(100)));
      propValues.put("url-delimiter", SimpleValueSupport.wrap("+"));
      propValues.put("url-selector-strategy-class-name", SimpleValueSupport.wrap("org.jboss.test.jca.support.MyURLSelector"));
      propValues.put("use-try-lock", SimpleValueSupport.wrap(new Long(5000)));
   }

   private void addCommonCfProperties(Map<String, MetaValue> propValues,
                                    String jndiName,
                                    String rarName,
                                    String conDef)
   {
      propValues.put("jndi-name", SimpleValueSupport.wrap(jndiName));
      propValues.put("rar-name", SimpleValueSupport.wrap(rarName));
      propValues.put("use-java-context", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("connection-definition", SimpleValueSupport.wrap(conDef));
      //propValues.put("jmx-invoker-name", SimpleValueSupport.wrap("jboss:service=invoker,type=jrmp"));
      propValues.put("min-pool-size", SimpleValueSupport.wrap(new Integer(0)));
      propValues.put("max-pool-size", SimpleValueSupport.wrap(new Integer(11)));
      propValues.put("blocking-timeout-millis", SimpleValueSupport.wrap(new Long(15000)));
      propValues.put("idle-timeout-minutes", SimpleValueSupport.wrap(new Integer(111)));
      propValues.put("prefill", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("background-validation", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("background-validation-millis", SimpleValueSupport.wrap(new Long(5000)));
      propValues.put("validate-on-match", SimpleValueSupport.wrap(Boolean.FALSE));
      propValues.put("use-strict-min", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("no-tx-separate-pools", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("statistics-formatter", SimpleValueSupport.wrap("org.jboss.resource.statistic.pool.JBossDefaultSubPoolStatisticFormatter"));
      propValues.put("isSameRM-override-value", SimpleValueSupport.wrap(Boolean.FALSE));
      propValues.put("track-connection-by-tx", SimpleValueSupport.wrap(Boolean.TRUE));
      propValues.put("type-mapping", SimpleValueSupport.wrap("Hypersonic SQL"));
      // todo: config-property
      // todo: security-domain
      // todo: depends
      // todo: metadata
      // todo: local-transaction
   }

   protected void createComponentTest(String templateName,
                                    Map<String, MetaValue> propValues,
                                    String deploymentName,
                                    ComponentType componentType, String componentName)
      throws Exception
   {
      ManagementView mgtView = getManagementView();
      DeploymentTemplateInfo dsInfo = mgtView.getTemplate(templateName);
      assertNotNull("template " + templateName + " found", dsInfo);
      Map<String, ManagedProperty> props = dsInfo.getProperties();

      for(String propName : propValues.keySet())
      {
         ManagedProperty prop = props.get(propName);
         // If the property does not exist on the template we don't set it
         if(prop == null)
            continue;
         
         log.debug("template property before: "+prop.getName()+","+prop.getValue());
         assertNotNull("property " + propName + " found in template " + templateName, prop);
         prop.setValue(propValues.get(propName));
         log.debug("template property after: "+prop.getName()+","+prop.getValue());
      }
      
      // Assert map composite
      if(dsInfo.getProperties().get("config-property") != null)
         assertTrue(dsInfo.getProperties().get("config-property").getMetaType() instanceof MapCompositeMetaType);
      
      mgtView.applyTemplate(deploymentName, dsInfo);

      // reload the view
      activeView = null;
      mgtView = getManagementView();
      ManagedComponent dsMC = getManagedComponent(mgtView, componentType, componentName);
      assertNotNull(dsMC);

      Set<String> mcPropNames = new HashSet<String>(dsMC.getPropertyNames());
      for(String propName : propValues.keySet())
      {
         ManagedProperty prop = dsMC.getProperty(propName);
         log.debug("Checking: "+prop);
         assertNotNull(prop);
         Object propValue = prop.getValue();
         Object expectedValue = propValues.get(propName);
         if(propValue instanceof MetaValue)
         {
            if (prop.getMetaType().isComposite())
            {
               // TODO / FIXME - compare composites
               log.warn("Not checking composite: "+propValue);
            }
            else
            {
               // Compare the MetaValues
               assertEquals(prop.getName(), expectedValue, propValue);
            }
         }
         else if(propValue != null)
         {
            fail(prop.getName()+" is not a MetaValue: "+propValue);
         }

         mcPropNames.remove(propName);
      }

      if(!mcPropNames.isEmpty())
      {
         log.warn(getName() + "> untested properties: " + mcPropNames);
         for(String propName : mcPropNames)
         {
            ManagedProperty prop = dsMC.getProperty(propName);
            log.info(prop);
         }
      }
   }

   public MapCompositeValueSupport compositeValueMap(Map<String,String> map)
   {
      // TODO: update MetaValueFactory for MapCompositeMetaType
      // MetaValue metaValue = getMetaValueFactory().create(xaPropValues, getMapType());
      MapCompositeValueSupport metaValue = new MapCompositeValueSupport(SimpleMetaType.STRING);
      for(String key : map.keySet())
      {
         MetaValue value = SimpleValueSupport.wrap(map.get(key));
         metaValue.put(key, value);
      }

      return metaValue;
   }

   protected void validatePropertyMetaValues(Map<String, ManagedProperty> props)
   {
      HashMap<String, Object> invalidValues = new HashMap<String, Object>();
      HashMap<String, Object> nullValues = new HashMap<String, Object>();
      for(ManagedProperty prop : props.values())
      {
         Object value = prop.getValue();
         if((value instanceof MetaValue) == false)
         {
            if(value == null)
               nullValues.put(prop.getName(), value);
            else
               invalidValues.put(prop.getName(), value);
         }
      }
      log.info("Propertys with null values: "+nullValues);
      assertEquals("InvalidPropertys: "+invalidValues, 0, invalidValues.size());

      // Validate more details on specific properties
      ManagedProperty securityDomain = props.get("security-domain");
      assertNotNull("security-domain", securityDomain);
      MetaType securityDomainType = securityDomain.getMetaType();
      assertTrue("security-domain type("+securityDomainType+") is a GenericMetaType", securityDomainType instanceof CompositeMetaType);
      log.debug("security-domain type: "+securityDomainType);
   }
}
