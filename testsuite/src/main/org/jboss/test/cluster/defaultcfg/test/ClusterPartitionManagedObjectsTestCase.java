/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.jboss.test.cluster.defaultcfg.test;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedObject;
import org.jboss.managed.api.ManagedOperation;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.profileservice.spi.ProfileService;
import org.jboss.test.JBossClusteredTestCase;
import org.jboss.virtual.VFS;

/**
 * Validates the expected HAPartition-related ManagedObjects are there
 * 
 * @author Brian Stansberry
 * @version $Revision: 88105 $
 */
public class ClusterPartitionManagedObjectsTestCase
   extends JBossClusteredTestCase
{
   protected ManagementView activeView;
   private String partitionName = System.getProperty("jbosstest.partitionName", "DefaultPartition");

   public ClusterPartitionManagedObjectsTestCase(String name)
   {
      super(name);
   }
   /**
    * Look at the HAPartition ManagedComponent
    * @throws Exception
    */
   public void testHAPartition()
      throws Exception
   {
      ManagementView mgtView = getManagementView(); 
      ComponentType type = new ComponentType("MCBean", "HAPartition");
      ManagedComponent mc = mgtView.getComponent(this.partitionName, type);
      validateHAPartitionManagedComponent(mc);
   }
   
   private void validateHAPartitionManagedComponent(ManagedComponent mc)
   {
      assertNotNull(mc);
      assertEquals("HAPartition", mc.getNameType());
      assertEquals("DefaultPartition", mc.getName());
      
      Set<String> operationNames = new HashSet<String>();
      getLog().debug(mc);
      for (ManagedOperation mo : mc.getOperations())
      {
         getLog().debug("name="+mo.getName()+",description="+mo.getDescription()+",impact="+mo.getImpact());
         operationNames.add(mo.getName());
      }
      
      assertTrue("HAPartition has showHistoryAsXML", operationNames.contains("showHistoryAsXML"));      
      assertTrue("HAPartition has showHistory", operationNames.contains("showHistory"));       
      // FIXME test for service lifecycle
      // FIXME test for DRM operations pseudo-name defined via @ManagementProperty.name  
//      assertTrue("HAPartition has getAllDRMServices", operationNames.contains("getAllDRMServices"));      
//      assertTrue("HAPartition has listDRMContentAsXml", operationNames.contains("listDRMContentAsXml")); 
//      assertTrue("HAPartition has getDRMServiceViewId", operationNames.contains("getDRMServiceViewId"));      
//      assertTrue("HAPartition has lookupDRMNodeNames", operationNames.contains("lookupDRMNodeNames"));
//      assertTrue("HAPartition has listDRMContent", operationNames.contains("listDRMContent"));     
//      assertTrue("HAPartition has isDRMMasterForService", operationNames.contains("isDRMMasterForService"));
      assertEquals("Correct number of operations", 8, operationNames.size());
      
      for (Map.Entry<String, ManagedProperty> entry : mc.getProperties().entrySet())
      {
         getLog().debug(entry.getKey() + " == " + entry.getValue());
         ManagedObject mo = entry.getValue().getTargetManagedObject();
         if (mo != null)
         {
            getLog().debug(entry.getKey() + " -- ManagedObject == " + mo);
         }
      }
      // FIXME validate the properties
   }

   /**
    * Obtain the ProfileService.ManagementView
    * @return
    * @throws Exception
    */
   protected ManagementView getManagementView()
      throws Exception
   {
      if( activeView == null )
      {
         String[] urls = getNamingURLs();
         Properties env = new Properties();
         env.setProperty(Context.INITIAL_CONTEXT_FACTORY,
            "org.jnp.interfaces.NamingContextFactory");
         env.setProperty(Context.PROVIDER_URL, urls[0]);
         Context ctx = new InitialContext(env);
         
         ProfileService ps = (ProfileService) ctx.lookup("ProfileService");
         activeView = ps.getViewManager();
         // Init the VFS to setup the vfs* protocol handlers
         VFS.init();
      }
      activeView.load();
      return activeView;
   }

   protected String getProfileName()
   {
      return "cluster-udp-0";
   }

}
