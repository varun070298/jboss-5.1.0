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
package org.jboss.test.cluster.testutil;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.Statement;
import java.io.File;
import java.io.IOException;

import org.jboss.test.JBossTestClusteredSetup;

import junit.extensions.TestSetup;
import junit.framework.Test;
import junit.framework.TestSuite;

/** A TestSetup that starts hypersonic before the testcase with a tcp
 * listening port at 1701.
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revison:$
 */
public class DBSetup extends JBossTestClusteredSetup
{
   public DBSetup(Test test, String jarNames) throws Exception
   {
      super(test, jarNames);
   }

   public static Test getDeploySetup(final Test test, final String jarNames)
      throws Exception
   {
      return new DBSetup(test, jarNames);
   }

   public static Test getDeploySetup(final Class clazz, final String jarNames)
      throws Exception
   {
      TestSuite suite = new TestSuite();
      suite.addTest(new TestSuite(clazz));
      return getDeploySetup(suite, jarNames);
   }

   protected void setUp() throws Exception
   {
         File hypersoniDir = new File("output/hypersonic");
         if (!hypersoniDir.exists())
         {
            hypersoniDir.mkdirs();
         }

         if (!hypersoniDir.isDirectory())
         {
            throw new IOException("Failed to create directory: " + hypersoniDir);
         }
      
         File dbPath = new File(hypersoniDir, "cif-db");

         // Start DB in new thread, or else it will block us
         DBThread serverThread = new DBThread(dbPath);
         serverThread.start();
         
         int elapsed = 0;
         while (!serverThread.isStarted() && elapsed < 15000)
         {
            try 
            {
               Thread.sleep(100);
               elapsed += 100;
            }
            catch (InterruptedException ie)
            {
               System.out.println("Interrupted while waiting for Hypersonic");
            }
         }
         
         if (!serverThread.isStarted())
            System.out.println("Hypersonic failed to start in a timely fashion");
         
         super.setUp();
   }

   protected void tearDown() throws Exception
   {
      try
      {
         super.tearDown();
      }
      finally
      {
         Class.forName("org.hsqldb.jdbcDriver");
         String dbURL = "jdbc:hsqldb:hsql://localhost:1701";
         Connection conn = DriverManager.getConnection(dbURL, "sa", "");
         Statement statement = conn.createStatement();      
         statement.executeQuery("SHUTDOWN COMPACT");
      }
      
   }

   public static void main(String[] args) throws Exception
   {
      DBSetup setup = new DBSetup(null, null);
      setup.setUp();
      Thread.sleep(120*1000);
      setup.tearDown();
   }
   
   class DBThread extends Thread
   {
      boolean started;
      File dbPath;
      
      DBThread(File dbPath)
      {
         super("hypersonic");
         this.dbPath = dbPath;
      }
      
      boolean isStarted()
      {
         return started;
      }
      
      public void run()
      {
         try
         {
            // Create startup arguments
            // BES 2007/09/25 We use -silent true to avoid 
            // http://sourceforge.net/tracker/index.php?func=detail&aid=1673747&group_id=23316&atid=378131
            String[] args = {
                  "-database",
                  dbPath.toString(),
                  "-port",
                  String.valueOf(1701),
                  "-silent",
                  "true",
                  "-trace",
                  "false",
                  "-no_system_exit",
                  "true",
             };
            System.out.println("Starting hsqldb");
            org.hsqldb.Server.main(args);
            System.out.println("Done");
         }
         catch (Exception e)
         {
            e.printStackTrace();
         }
         finally
         {
            started = true;
         }
      }
   }
}
