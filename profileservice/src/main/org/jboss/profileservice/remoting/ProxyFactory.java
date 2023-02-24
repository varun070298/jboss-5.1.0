/*
 * JBoss, Home of Professional Open Source
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.profileservice.remoting;

import java.util.ArrayList;
import java.util.List;

import javax.naming.InitialContext;

import org.jboss.aop.Dispatcher;
import org.jboss.aop.advice.Interceptor;
import org.jboss.aop.proxy.Proxy;
import org.jboss.aspects.remoting.InvokeRemoteInterceptor;
import org.jboss.aspects.remoting.MergeMetaDataInterceptor;
import org.jboss.aspects.remoting.Remoting;
import org.jboss.aspects.security.SecurityClientInterceptor;
import org.jboss.deployers.spi.management.ManagementView;
import org.jboss.deployers.spi.management.deploy.DeploymentManager;
import org.jboss.logging.Logger;
import org.jboss.profileservice.spi.ProfileService;
import org.jboss.remoting.InvokerLocator;
import org.jboss.util.naming.Util;

/**
 * An aop/remoting proxy factory bean that exposes the ProfileService
 * interfaces.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revision: 87850 $
 */
public class ProxyFactory
{
   private static final Logger log = Logger.getLogger(ProxyFactory.class);
   private String dispatchName = "ProfileService";
   private String jndiName = "ProfileService";
   private String mgtViewJndiName = "java:ManagementView";
   private String deployMgrJndiName = "java:DeploymentManager";
   private InvokerLocator locator;
   private ProfileService ps;
   private ManagementView mgtView;
   private DeploymentManager deployMgr;
   private Proxy psProxy;
   private Proxy mgtViewProxy;
   private Proxy deployMgrProxy;
   private List<Interceptor> proxyInterceptors;

   public String getDispatchName()
   {
      return dispatchName;
   }

   public void setDispatchName(String dispatchName)
   {
      this.dispatchName = dispatchName;
   }

   public String getJndiName()
   {
      return jndiName;
   }

   public void setJndiName(String jndiName)
   {
      this.jndiName = jndiName;
   }

   
   public String getMgtViewJndiName()
   {
      return mgtViewJndiName;
   }

   public void setMgtViewJndiName(String mgtViewJndiName)
   {
      this.mgtViewJndiName = mgtViewJndiName;
   }

   public String getDeployMgrJndiName()
   {
      return deployMgrJndiName;
   }

   public void setDeployMgrJndiName(String deployMgrJndiName)
   {
      this.deployMgrJndiName = deployMgrJndiName;
   }

   public InvokerLocator getLocator()
   {
      return locator;
   }

   public void setLocator(InvokerLocator locator)
   {
      this.locator = locator;
   }

   public ProfileService getProfileService()
   {
      return ps;
   }

   public void setProfileService(ProfileService ps)
   {
      this.ps = ps;
   }

   public Proxy getProfileServiceProxy()
   {
      return psProxy;
   }

   public ManagementView getViewManager()
   {
      return mgtView;
   }
   public void setViewManager(ManagementView mgtView)
   {
      this.mgtView = mgtView;
   }

   public Proxy getManagementViewProxy()
   {
      return mgtViewProxy;
   }

   public DeploymentManager getDeploymentManager()
   {
      return deployMgr;
   }
   public void setDeploymentManager(DeploymentManager deployMgr)
   {
      this.deployMgr = deployMgr;
   }

   public Proxy getDeployMgrProxy()
   {
      return deployMgrProxy;
   }

   
   public List<Interceptor> getProxyInterceptors()
   {
      return proxyInterceptors;
   }
   public void setProxyInterceptors(List<Interceptor> proxyInterceptors)
   {
      this.proxyInterceptors = proxyInterceptors;
   }

   public void start()
      throws Exception
   {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      Class[] ifaces = {ProfileService.class};

      // Create the ProfileService proxy
      Dispatcher.singleton.registerTarget(dispatchName, ps);

      if(proxyInterceptors == null)
      {
         proxyInterceptors = new ArrayList<Interceptor>();
         proxyInterceptors.add(SecurityClientInterceptor.singleton);
         proxyInterceptors.add(MergeMetaDataInterceptor.singleton);
         proxyInterceptors.add(InvokeRemoteInterceptor.singleton);
      }

      psProxy = Remoting.createRemoteProxy(dispatchName, loader, ifaces, locator, proxyInterceptors, "ProfileService");
      InitialContext ctx = new InitialContext();
      Util.bind(ctx, jndiName, psProxy);
      log.debug("Bound ProfileService proxy under: "+jndiName);

      // Create the ManagementView proxy
      Class[] mvIfaces = {ManagementView.class};
      String mvDispatchName = dispatchName+".ManagementView";
      Dispatcher.singleton.registerTarget(mvDispatchName, mgtView);
      mgtViewProxy = Remoting.createRemoteProxy(mvDispatchName, loader, mvIfaces, locator, proxyInterceptors, "ProfileService");
      log.debug("Created ManagementView proxy");
      if(mgtViewJndiName != null && mgtViewJndiName.length() > 0)
      {
         Util.bind(ctx, mgtViewJndiName, mgtViewProxy);
         log.debug("Bound ManagementView proxy under: "+mgtViewJndiName);
      }

      // Create the DeploymentManager proxy
      Class[] dmIfaces = {DeploymentManager.class};
      String dmDispatchName = dispatchName+".DeploymentManager";
      Dispatcher.singleton.registerTarget(dmDispatchName, deployMgr);
      deployMgrProxy = Remoting.createRemoteProxy(dmDispatchName, loader, dmIfaces, locator, proxyInterceptors, "DeploymentManager");
      log.debug("Created DeploymentManager proxy");      
      if(deployMgrJndiName != null && deployMgrJndiName.length() > 0)
      {
         Util.bind(ctx, deployMgrJndiName, deployMgrProxy);
         log.debug("Bound DeploymentManager proxy under: "+deployMgrJndiName);
      }
   }

   public void stop()
      throws Exception
   {
      Dispatcher.singleton.unregisterTarget(dispatchName);
      String mvDispatchName = dispatchName+".ManagementView";
      Dispatcher.singleton.unregisterTarget(mvDispatchName);
      InitialContext ctx = new InitialContext();
      Util.unbind(ctx, jndiName);
      log.debug("Unbound ProfileService proxy");
      if(mgtViewJndiName != null && mgtViewJndiName.length() > 0)
      {
         Util.unbind(ctx, mgtViewJndiName);
         log.debug("Unbound ManagementView proxy");
      }
      if(deployMgrJndiName != null && deployMgrJndiName.length() > 0)
      {
         Util.unbind(ctx, deployMgrJndiName);
         log.debug("Unbound DeploymentManager proxy");
      }
   }
}
