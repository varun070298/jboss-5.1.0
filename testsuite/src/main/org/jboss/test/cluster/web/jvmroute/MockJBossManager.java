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

package org.jboss.test.cluster.web.jvmroute;

import java.beans.PropertyChangeListener;
import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Container;
import org.apache.catalina.Session;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.jboss.ReplicationTrigger;
import org.jboss.web.tomcat.service.session.ClusteredManager;
import org.jboss.web.tomcat.service.session.distributedcache.spi.ClusteringNotSupportedException;
import org.jboss.web.tomcat.service.session.distributedcache.spi.DistributedCacheManager;
import org.jboss.web.tomcat.service.session.distributedcache.spi.OutgoingDistributableSessionData;
import org.jboss.web.tomcat.service.session.notification.ClusteredSessionNotificationPolicy;
import org.jboss.web.tomcat.service.session.notification.LegacyClusteredSessionNotificationPolicy;

/**
 * @author Brian Stansberry
 *
 */
public class MockJBossManager implements ClusteredManager<OutgoingDistributableSessionData>
{
   private String jvmRoute = null;
   private String newCookieIdSession = null;
   private Session session = null;
   
   /**
    * Create a new MockJBossManager.
    * 
    */
   public MockJBossManager()
   {      
   }

   public String getJvmRoute()
   {
      return jvmRoute;
   }
   
   public void setJvmRoute(String jvmRoute)
   {
      this.jvmRoute = jvmRoute;
   }

   public void setNewSessionCookie(String sessionId, HttpServletResponse response)
   {
      newCookieIdSession = sessionId;
   }
   
   public String getNewCookieIdSession()
   {
      return newCookieIdSession;
   }

   public Session createSession(String s)
   {
      Session session = new MockSession(this);
      session.setId(s);
      return session;
   }

   public Session findSession(String s) throws IOException
   {
      return session;
   }
   
   public void add(Session session)
   {
      this.session = session;
   }

   public ReplicationTrigger getReplicationTrigger()
   {
      return ReplicationTrigger.SET_AND_NON_PRIMITIVE_GET;
   }

   public void init(String name, JBossWebMetaData webMetaData) throws ClusteringNotSupportedException
   {      

   }

   public void removeLocal(Session session)
   {
   }

   public boolean storeSession(Session session)
   {
      return false;
   }

   public void addPropertyChangeListener(PropertyChangeListener propertychangelistener)
   {
   }

   public void backgroundProcess()
   {
   }

   public Session createEmptySession()
   {
      return null;
   }

   public Session createSession()
   {
      return null;
   }

   public Session[] findSessions()
   {
      return null;
   }

   public int getActiveSessions()
   {
      return 0;
   }

   public Container getContainer()
   {
      return null;
   }

   public boolean getDistributable()
   {
      return false;
   }

   public int getExpiredSessions()
   {
      return 0;
   }

   public String getInfo()
   {
      return null;
   }

   public int getMaxActive()
   {
      return 0;
   }

   public int getMaxInactiveInterval()
   {
      return 0;
   }

   public int getRejectedSessions()
   {
      return 0;
   }

   public int getSessionAverageAliveTime()
   {
      return 0;
   }

   public int getSessionCounter()
   {
      return 0;
   }

   public int getSessionIdLength()
   {
      return 0;
   }

   public int getSessionMaxAliveTime()
   {
      return 0;
   }

   public void load() throws ClassNotFoundException, IOException
   {
   }

   public void remove(Session session)
   {
   }

   public void removePropertyChangeListener(PropertyChangeListener propertychangelistener)
   {
   }

   public void setContainer(Container container)
   {
   }

   public void setDistributable(boolean flag)
   {
   }

   public void setExpiredSessions(int i)
   {
   }

   public void setMaxActive(int i)
   {
   }
   
   public void setMaxInactiveInterval(int i)
   {
   }

   public void setRejectedSessions(int i)
   {
   }

   public void setSessionAverageAliveTime(int i)
   {
   }

   public void setSessionCounter(int i)
   {
   }

   public void setSessionIdLength(int i)
   {
   }

   public void setSessionMaxAliveTime(int i)
   {
   }

   public void unload() throws IOException
   {
   }

   public DistributedCacheManager getDistributedCacheManager()
   {
      return MockDistributedCacheManager.INSTANCE;
   }

   public int getMaxUnreplicatedInterval()
   {
      return -1;
   }

   public ClusteredSessionNotificationPolicy getNotificationPolicy()
   {
      return new LegacyClusteredSessionNotificationPolicy();
   }

   public boolean getUseJK()
   {
      return true;
   }
   
   

}
