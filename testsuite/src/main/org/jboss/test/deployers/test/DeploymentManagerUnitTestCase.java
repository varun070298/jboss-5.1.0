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
package org.jboss.test.deployers.test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collection;

import javax.naming.InitialContext;

import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.deployers.spi.management.deploy.DeploymentID;
import org.jboss.deployers.spi.management.deploy.DeploymentManager;
import org.jboss.deployers.spi.management.deploy.DeploymentProgress;
import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.DeploymentState;
import org.jboss.managed.api.ManagedComponent;
import org.jboss.managed.api.ManagedDeployment;
import org.jboss.metatype.api.values.SimpleValue;
import org.jboss.profileservice.spi.NoSuchDeploymentException;
import org.jboss.profileservice.spi.ProfileKey;
import org.jboss.profileservice.spi.ProfileService;
import org.jboss.test.JBossTestCase;

/**
 * Basic DeploymentManager test.
 *
 * @author <a href="mailto:emuckenh@redhat.com">Emanuel Muckenhuber</a>
 * @version $Revision$
 */
public class DeploymentManagerUnitTestCase extends JBossTestCase
{
   /** A basic failing deployment. */
   final static String FAILING_DEPLOYMENT = "deployers-failing-jboss-beans.xml";
   /** A empty deployment, this will deploy ok. */
   final static String EMTPY_DEPLOYMENT = "deployers-empty-jboss-beans.xml";
   /** A simple nested deployment. */
   final static String NESTED_DEPLOYMENT = "profileservice-datasource.ear";
   /** A deployment picked up by the HDScanner. */
   final static String HD_DEPLOYMENT = "hd-jboss-beans.xml";

   /** The deployers target profile. */
   final static ProfileKey deployersKey = new ProfileKey("deployers");

   /** The deployment manager. */
   private DeploymentManager deployMgr;
   private ManagementView mgtView;

   public DeploymentManagerUnitTestCase(String name)
   {
      super(name);
   }

   /**
    * Test the available profiles.
    *
    * @throws Exception
    */
   public void testAvaiableProfiles() throws Exception
   {
      Collection<ProfileKey> keys = getDeploymentManager().getProfiles();
      assertNotNull(keys);
      log.debug("available keys: " + keys);
      keys.contains(new ProfileKey("applications"));
      keys.contains(deployersKey);
   }

   /**
    * Test a override of the applications, without
    * removing/stopping them before.
    *
    * @throws Exception
    */
   public void testDistributeOverride() throws Exception
   {
      try
      {
         for(int i = 0; i < 5; i++)
         {
            //
            DeploymentProgress start = distributeAndStart(NESTED_DEPLOYMENT, true, false);
            assertComplete(start);
            
            // disable stopped check, as it was started before
            start = distributeAndStart(NESTED_DEPLOYMENT, true, false);
            assertComplete(start);

            Thread.sleep(50);
            
            redeployCheckComplete(NESTED_DEPLOYMENT);
         }
      }
      catch(Exception e)
      {
         log.debug("Failed ", e);
         throw e;
      }
      finally
      {
         stopAndRemove(new String[] { NESTED_DEPLOYMENT });
      }
   }

   /**
    * Basic copyContent test to the default location.
    *
    * @throws Exception
    */
   public void testCopyContent() throws Exception
   {
      try
      {
         // failed
         deployFailed(true);
         // complete
         deployEmpty(true);
         // Test redeploy
         assertTrue(getDeploymentManager().isRedeploySupported());
         redeployCheckComplete(EMTPY_DEPLOYMENT);
         // TODO implement prepare
         prepareCheckComplete(EMTPY_DEPLOYMENT);
      }
      catch(Exception e)
      {
         log.debug("Failed ", e);
         throw e;
      }
      stopAndRemove(new String[]
         { FAILING_DEPLOYMENT, EMTPY_DEPLOYMENT } );
   }

   /**
    * Basic noCopyContent test.
    *
    * @throws Exception
    */
   public void testNoCopyContent() throws Exception
   {
      try
      {
         // failed
         deployFailed(false);
         // complete
         deployEmpty(false);
         // test redeploy
         assertTrue(getDeploymentManager().isRedeploySupported());
         redeployCheckComplete(EMTPY_DEPLOYMENT);
         // TODO implement prepare
         prepareCheckComplete(EMTPY_DEPLOYMENT);
      }
      catch(Exception e)
      {
         log.error("Failed ", e);
         throw e;
      }
      stopAndRemove(new String[]
          { FAILING_DEPLOYMENT, EMTPY_DEPLOYMENT } );
   }

   /**
    * Test copyContent to the deployers target profile.
    *
    * @throws Exception
    */
   public void testDepoyersDir() throws Exception
   {
      getDeploymentManager().loadProfile(deployersKey);
      try
      {
         // failed
         deployFailed(true);
         // complete
         deployEmpty(true);
         // Test redeploy
         assertTrue(getDeploymentManager().isRedeploySupported());
         redeployCheckComplete(EMTPY_DEPLOYMENT);
         // stop and remove
         stopAndRemove(new String[]
             { FAILING_DEPLOYMENT, EMTPY_DEPLOYMENT } );
      }
      catch(Exception e)
      {
         log.debug("Failed ", e);
         throw e;
      }
      finally
      {
         // Make sure that we release the profile
         getDeploymentManager().releaseProfile();
      }
   }

   /**
    * Test the hd deployment. This deployment will get copied
    * to the deploy folder after the server is started. This
    * deployment needs to get picked up by the HDScanner and
    * should be available to the ManagementView.
    *
    * @throws Exception
    */
   public void testHotDeploymentBeans() throws Exception
   {
      ManagementView mgtView = getManagementView();
      ComponentType type = new ComponentType("MCBean", "ServerConfig");
      ManagedComponent mc = mgtView.getComponent("jboss.system:type=ServerConfig", type);
      assertNotNull(mc);
      String homeDir = (String) ((SimpleValue) mc.getProperty("serverHomeDir").getValue()).getValue();
      assertNotNull(homeDir);
      
      // Manually copy the deployment
      copyFile(new File(homeDir, "deploy/"), HD_DEPLOYMENT);
      // Wait for HDScanner
      Thread.sleep(10000);
      
      // Reload mgtView
      mgtView = getManagementView();
      ManagedDeployment md = mgtView.getDeployment(HD_DEPLOYMENT);
      assertNotNull("hd-beans not deployed", md);
      assertEquals("deployment started", DeploymentState.STARTED, md.getDeploymentState());

      stopAndRemove(new String[] { HD_DEPLOYMENT });
   }

   private void copyFile(File dir, String filename) throws Exception
   {
      InputStream is = getDeployURL(filename).openStream();
      try
      {
         File output = new File(dir, filename);
         FileOutputStream fos = new FileOutputStream(output);
         try
         {
            byte[] tmp = new byte[1024];
            int read;
            while((read = is.read(tmp)) > 0)
            {
               fos.write(tmp, 0, read);
            }
            fos.flush();
         }
         finally
         {
            fos.close();
         }         
      }
      finally
      {
         is.close();
      }
   }
   
   void deployFailed(boolean isCopyContent) throws Exception
   {
      DeploymentProgress start = distributeAndStart(FAILING_DEPLOYMENT, isCopyContent);
      assertFailed(start);
      assertDeploymentState(start.getDeploymentID(), DeploymentState.FAILED);
   }

   void deployEmpty(boolean isCopyContent) throws Exception
   {
      DeploymentProgress start = distributeAndStart(EMTPY_DEPLOYMENT, isCopyContent);
      assertComplete(start);
      assertDeploymentState(start.getDeploymentID(), DeploymentState.STARTED);
   }

   DeploymentProgress distributeAndStart(String deploymentName, boolean copyContent) throws Exception
   {
      return distributeAndStart(deploymentName, copyContent, true);
   }

   DeploymentProgress distributeAndStart(String deploymentName, boolean copyContent, boolean checkStopped) throws Exception
   {
      // The deployment manager
      DeploymentManager deployMgr = getDeploymentManager();

      // Distribute
      DeploymentProgress distribute = deployMgr.distribute(deploymentName,
            getDeployURL(deploymentName), copyContent);
      distribute.run();
      // Distribute always has to complete
      assertComplete(distribute);
      // check if the app is stopped
      if(checkStopped)
         assertDeploymentState(distribute.getDeploymentID(), DeploymentState.STOPPED);

      // Get the repository names
      String[] uploadedNames = distribute.getDeploymentID().getRepositoryNames();
      assertNotNull(uploadedNames);

      // Start
      DeploymentProgress start = deployMgr.start(uploadedNames);
      start.run();
      // Return the start deployment progress
      return start;
   }

   void redeployCheckComplete(String name) throws Exception
   {
      // The deployment manager
      DeploymentManager deployMgr = getDeploymentManager();

      // Redeploy
      DeploymentProgress redeploy = deployMgr.redeploy(name);
      redeploy.run();
      assertComplete(redeploy);
      assertDeploymentState(redeploy.getDeploymentID(), DeploymentState.STARTED);
   }

   void prepareCheckComplete(String name) throws Exception
   {
      // The deployment manager
      DeploymentManager deployMgr = getDeploymentManager();

      // Prepare
      DeploymentProgress prepare = deployMgr.prepare(name);
      prepare.run();
      assertComplete(prepare);
   }

   void stopAndRemove(String[] names) throws Exception
   {
      // The deployment manager
      DeploymentManager deployMgr = getDeploymentManager();

      try
      {
         DeploymentProgress stop = deployMgr.stop(names);
         stop.run();
         assertComplete(stop);
         assertDeploymentState(stop.getDeploymentID(), DeploymentState.STOPPED);
      }
      catch(Exception e)
      {
         log.debug("stopAndRemove Failed ", e);
         throw e;
      }
      finally
      {
         DeploymentProgress remove = deployMgr.remove(names);
         remove.run();
         assertComplete(remove);

         String name = remove.getDeploymentID().getNames()[0];
         ManagementView mgtView = getManagementView();
         try
         {
            mgtView.getDeployment(name);
            fail("Did not see NoSuchDeploymentException");
         }
         catch(NoSuchDeploymentException ok)
         {
            //
         }
      }
   }

   void assertFailed(DeploymentProgress progress) throws Exception
   {
      assertFalse(progress.getDeploymentStatus().isCompleted());
      assertTrue(progress.getDeploymentStatus().isFailed());
   }

   void assertDeploymentState(DeploymentID DtID, DeploymentState state) throws Exception
   {
      String name = DtID.getNames()[0];
      ManagementView mgtView = getManagementView();
      ManagedDeployment md = mgtView.getDeployment(name);
      assertNotNull(name, md);
      assertEquals("deployment: " + name, state, md.getDeploymentState());
      log.debug(md.getSimpleName() + " " + md.getTypes());
   }

   void assertComplete(DeploymentProgress progress) throws Exception
   {
      if(progress.getDeploymentStatus().isFailed())
      {
         throw new RuntimeException("deployment failed.", progress.getDeploymentStatus().getFailure());
      }
      //
      assertTrue(progress.getDeploymentStatus().isCompleted());
   }

   DeploymentManager getDeploymentManager() throws Exception
   {
      if(this.deployMgr == null)
      {
         this.deployMgr = getProfileService().getDeploymentManager();
      }
      return deployMgr;
   }

   ManagementView getManagementView() throws Exception
   {
      if(this.mgtView == null)
      {
         this.mgtView = getProfileService().getViewManager();
      }
      this.mgtView.load();
      return this.mgtView;
   }

   ProfileService getProfileService() throws Exception
   {
      InitialContext ctx = getInitialContext();
      return (ProfileService) ctx.lookup("ProfileService");
   }

}
