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
package org.jboss.ha.framework.server;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import org.jboss.bootstrap.spi.ServerConfig;
import org.jboss.deployment.Deployer;
import org.jboss.deployment.scanner.URLDeploymentScanner;
import org.jboss.ha.framework.interfaces.HAPartition;
import org.jboss.system.server.ServerConfigLocator;

/**
 *
 * @author <a href="mailto:andreas@jboss.org">Andreas Schaefer</a>
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 86549 $
 *
 * <p><b>20021014 andreas schaefer:</b>
 * <ul>
 *   <li>Initial import
 * </ul>
 * <p><b>20020809 bill burke:</b>
 * <ul>
 *   <li>Rewrote as a Scanner instead.  Also on boot-up asks cluster for deployable files
 * </ul>
 *
 * @deprecated No longer used since 5.0.0; will be removed in 6.0.0
 */
public class FarmMemberService extends URLDeploymentScanner implements FarmMemberServiceMBean
{
   protected HAPartition mClusterPartition = null;
   private File mTempDirectory;
   private ClusterFileTransfer mFileTransfer;

   protected final static String SERVICE_NAME = "FarmMemberService";
   protected HashMap parentDUMap = new HashMap();
   
   protected ArrayList remotelyDeployed = new ArrayList ();
   protected ArrayList remotelyUndeployed = new ArrayList ();

   public String getPartitionName()
   {
      return mClusterPartition.getPartitionName();
   }

   public HAPartition getHAPartition()
   {
      return mClusterPartition;
   }

   public void setHAPartition(HAPartition clusterPartition)
   {
      if( ( getState () != STARTED ) && ( getState () != STARTING ) )   
      {         
         this.mClusterPartition = clusterPartition;
      }
   }

   /** Backward compatibility, mapped to the URLs attribute of URLDeploymentScannerMBean
    * @deprecated
    */
   public void setFarmDeployDirectory(String urls)
      throws MalformedURLException
   {
      super.setURLs(urls);
   }

   /** Backward compatibility, but ignored as it does nothing.
    * @deprecated
    */
   public void setScannerName(String name)
   {
      log.warn("ScannerName does nothing");
   }

   // Service implementation ----------------------------------------

   public String getName()
   {
      return "Farm Member Service";
   }
   
   /**
    * Looks up the Server Config instance to figure out the
    * temp-directory and the farm-deploy-directory
    **/
   protected void createService() throws Exception
   {
      super.createService();
      ServerConfig lConfig = ServerConfigLocator.locate();
      mTempDirectory = lConfig.getServerTempDir();
      
      createUnexistingLocalDir ();
   }
   /**
    * Register itself as RPC-Handler to the HA-Partition
    * and add the farm deployment directory to the scanner
    **/
   protected void startService()
      throws Exception
   {
      try
      {         
         if (mClusterPartition == null)
         {
            throw new IllegalStateException("HAPartition property must be set before starting FarmMember service");
         }
         
         log.debug( "registerRPCHandler" );
         mClusterPartition.registerRPCHandler( SERVICE_NAME, this );

         mFileTransfer = new ClusterFileTransfer(mClusterPartition, buildParentFolderMapping());

         ArrayList response = mClusterPartition.callMethodOnCoordinatorNode(
            SERVICE_NAME,
            "farmDeployments",
            new Object[] {}, new Class[] {},
            true
         );

         log.debug("Found "+response.size()+" farmDeployments responses");
         for (int i = 0; i < response.size(); i++)
         {
            Object map = response.get(i);
            if ( map != null && map instanceof HashMap )
            {
               HashMap farmed = (HashMap) map;
               pullNewDeployments(mClusterPartition, farmed);
            }
         }

         // scan before we enable the thread, so JBoss version shows up afterwards
         scannerThread.doScan();

         // enable scanner thread if we are enabled
         scannerThread.setEnabled(isScanEnabled());
      }
      catch(Exception e)
      {
         this.logException(e);
         throw e;
      }
   }
   

   protected void pullNewDeployments(HAPartition partition, HashMap farmed)
   {
      log.info("**** pullNewDeployments ****");
      Iterator it = farmed.keySet().iterator();
      while (it.hasNext())
      {
         String depName = (String)it.next();
         DeployedURL du = (DeployedURL)parentDUMap.get(depName);
         Date last = (Date)farmed.get(depName);
         if (du != null)
         {
            Date theLast = new Date(du.getFile().lastModified());
            if (!theLast.before(last))
            {
               continue;
            }
         }

         String parentName = depName.substring(0, depName.indexOf('/'));
         File destFile = new File(depName);
         try
         {
            mFileTransfer.pull(destFile,parentName);
            synchronized (remotelyDeployed)
            {
               remotelyDeployed.add (destFile.getName());
            }
         }
         catch(ClusterFileTransferException e)
         {
            // log the exception and continue with the next deployment
            this.logException(e);
         }
      }
   }

   // return mapping of Farming folder names to the File
   private Map buildParentFolderMapping()
   {
      Map map = new HashMap();
      URL[] urls = (URL[]) urlList.toArray( new URL[] {} );
      for (int i = 0; i < urlList.size(); i++)
      {
         if (urls[i].getProtocol().equals("file"))
         {
            File file = new File(urls[i].getFile());
            if (file.isDirectory())
            {
               map.put(file.getName(),file);
            }

         }
      }
      return map;
   }

   protected File findParent(String parentName)
   {
      URL[] urls = (URL[]) urlList.toArray( new URL[] {} );
      for (int i = 0; i < urlList.size(); i++)
      {
         if (urls[i].getProtocol().equals("file"))
         {
            File file = new File(urls[i].getFile());
            if (file.isDirectory())
            {
               if (file.getName().equals(parentName)) return file;
            }
         }
      }
      return null;
   }

   public HashMap farmDeployments()
   {
      log.debug("farmDeployments request, parentDUMap.size="+parentDUMap.size());
      Iterator it = parentDUMap.keySet().iterator();
      HashMap farmed = new HashMap();
      while(it.hasNext())
      {
         String key = (String)it.next();
         DeployedURL du = (DeployedURL)parentDUMap.get(key);
         farmed.put(key, new Date(du.getFile().lastModified()));
      }
      return farmed;
   }
   
   public void farmDeploy( String parentName, File destFile, Date date )
   {
      try 
      {
         File parent = findParent(parentName);
         if (parent == null) 
         {
            log.info("Could not find parent: " + parentName + " for deployment: " + destFile + ", data: " + date);
            return;
         }
         
         String fullName = parentName + "/" + destFile.getName();

         DeployedURL du = null;
         synchronized(parentDUMap)
         {
            du = (DeployedURL)parentDUMap.get(fullName);
         }
         boolean deployIt = false;
         if (du == null) 
         {
            deployIt = true;
         }
         else
         {
            Date lastChanged = new Date(du.getFile().lastModified());
            deployIt = lastChanged.before(date);
         }

         if (deployIt)
         {
            // we remember this deployment to avoid recursive farm calls!
            //
            synchronized (remotelyDeployed)
            {
               remotelyDeployed.add (fullName);
            }

            log.info( "farmDeployment(), deploy locally: " + fullName );
            // Adjust the date and move the file to /farm
            // but delete it first if already there
            File tempFile = new File(this.mTempDirectory, destFile.getName() );
            File lFarmFile = new File(parent,destFile.getName());
            if( lFarmFile.exists() ) {
               if(!lFarmFile.delete()) {
                  log.info("could not delete target file for farm deployment "+ lFarmFile.getName());
               }
            }
            tempFile.setLastModified( date.getTime() );

            if(! ClusterFileTransfer.localMove(tempFile,lFarmFile ))
            {
               log.info("Could not move "+tempFile+" to " + lFarmFile);
            }
         }
         else
         {
            log.info(fullName + " is already deployed by farm service on this node");
         }
      }
      catch( Exception e ) {
         logException( e );
      }
   }
   
   public void farmUndeploy(String parentName, String fileName)
   {
      try {
         // First check if file is already deployed
         log.info( "doUndeployment(), File: " + parentName + "/" + fileName);
         File parent = findParent(parentName);
         if (parent == null) 
         {
            log.info("Could not find parent: " + parentName + " for undeployment: " + fileName);
            return;
         }
         File deployed = new File(parent, fileName);
         if (deployed.exists())
         {            
            // we remember this undeployment to avoid recursive farm calls!
            //
            synchronized (remotelyUndeployed)
            {
               String fullName = parentName + "/" + fileName;
               remotelyUndeployed.add (fullName);
            }

            if(deployed.delete())
               log.info( "farmUndeployment(), removed file " + deployed );
            else
               log.info( "farmUndeployment(), could not remove file " + deployed );
         }
      }
      catch( Exception e ) {
         logException( e );
      }
   }
   
   protected void deploy(final DeployedURL du)
   {
      super.deploy(du);
      File file = du.getFile();
      File parent = file.getParentFile();
      if (parent == null) return;
      
      String fullName = parent.getName() + "/" + file.getName();
      synchronized (parentDUMap)
      {
         parentDUMap.put(fullName, du);
      }

      try
      {
         // We check if we must do a remote call or not; maybe the deploy 
         // is already the consequence of a farm call! (avoid recursivity!)
         //
         boolean consequenceOfRemoteCall = false;
         synchronized (remotelyDeployed)
         {
            consequenceOfRemoteCall = remotelyDeployed.remove (fullName);
         }
         
         if (getState() == STARTING) return;

         if (!consequenceOfRemoteCall)
         {
            Date fileDate = new Date(file.lastModified());
            
            this.mFileTransfer.push(file, parent.getName(), true);

            mClusterPartition.callMethodOnCluster(
               SERVICE_NAME,
               "farmDeploy",
               new Object[] {parent.getName(), file, fileDate},
               new Class[] {String.class, File.class, Date.class},
               true
               );
         }
      }
      catch (ClusterFileTransferException e)
      {
         logException(e);
      }
      catch (Exception ex)
      {
         logException(ex);
      }

   }

   protected void undeploy(final DeployedURL du)
   {
      
      File file = du.getFile();
      File parent = file.getParentFile();
      String parentName = parent.getName();
      String fileName = file.getName();
      super.undeploy(du);
      
      String fullName = parent.getName() + "/" + file.getName();
      synchronized (parentDUMap)
      {
         parentDUMap.remove(fullName);
      }
      
      if (getState() == STOPPING) return;

      try
      {
         // We check if we must do a remote call or not: maybe the undeploy 
         // is already the consequence of a farm call! (avoid recusivity!)
         //
         boolean consequenceOfRemoteCall = false;
         synchronized (remotelyUndeployed)
         {
            consequenceOfRemoteCall = remotelyUndeployed.remove (fullName);
         }
         
         if (!consequenceOfRemoteCall)
         {
            mClusterPartition.callMethodOnCluster(
               SERVICE_NAME,
               "farmUndeploy",
               new Object[] {parentName, fileName},
               new Class[] {String.class, String.class},
               true
               );
         }
      }
      catch (Exception ex)
      {
         logException(ex);
      }
   }

   /**
    * Go through the myriad of nested JMX exceptions to pull out the true
    * exception if possible and log it.
    *
    * @param e The exception to be logged.
    */
   private void logException( Throwable e )
   {
      if (e instanceof javax.management.RuntimeErrorException)
      {
         e = ((javax.management.RuntimeErrorException)e).getTargetError();
      }
      else if (e instanceof javax.management.RuntimeMBeanException)
      {
         e = ((javax.management.RuntimeMBeanException)e).getTargetException();
      }
      else if (e instanceof javax.management.RuntimeOperationsException)
      {
         e = ((javax.management.RuntimeOperationsException)e).getTargetException();
      }
      else if (e instanceof javax.management.MBeanException)
      {
         e = ((javax.management.MBeanException)e).getTargetException();
      }
      else if (e instanceof javax.management.ReflectionException)
      {
         e = ((javax.management.ReflectionException)e).getTargetException();
      }

      log.error(e);
   }
   
   protected void createUnexistingLocalDir()
   {
      if (this.urlList != null)
      {
         Iterator iter = this.urlList.iterator ();
         while (iter.hasNext ())
         {
            URL url = null;
            try
            {
               url = (URL)iter.next ();
               if (url.getProtocol().equals("file"))
               {
                  File targetDir = new File (url.getFile ());
                  if (!targetDir.exists ())
                     targetDir.mkdirs ();
               }
            }
            catch (Exception e)
            {
               log.info ("Problem while creating a farm directory: " + url, e);
            }
         }
      }
   }
}
