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
package org.jboss.test.naming.test;

import java.net.URL;
import java.util.Properties;
import junit.framework.Test;

import org.jboss.test.JBossTestCase;
import org.jboss.test.JBossTestSuite;
import org.jboss.test.naming.ejb.NamingTests;

/** Stress tests for the JNDI naming layer
 *
 *  @author Scott.Stark@jboss.org
 *  @version $Revision: 81036 $
 */
public class NamingStressTestCase extends JBossTestCase
{
   public NamingStressTestCase(String name)
   {
      super(name);
   }

   public static Test suite() throws Exception
   {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      URL url = loader.getResource("messaging/test-destinations-full-service.xml");
      if (url == null)
         throw new RuntimeException("test queues not found");

      Properties props = new Properties();
      props.setProperty("ejbRunnerJndiName", "EJBTestRunnerHome");
      props.setProperty("encBeanJndiName", "ENCBean");
      props.setProperty("encIterations", "1000");
      JBossTestSuite testSuite = new JBossTestSuite(props);
      testSuite.addTestSuite(NamingTests.class);
      return getDeploySetup(testSuite, url+",naming.jar");
   }

}
