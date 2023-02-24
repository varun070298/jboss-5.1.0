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
package org.jboss.test.jca.ejb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.ejb.SessionBean;
import javax.ejb.SessionContext;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import javax.transaction.UserTransaction;

import org.jboss.logging.Logger;

/**
 * TransactionActiveBean.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 85945 $
 */
public class TransactionActiveBean implements SessionBean  
{
   /** The serialVersionUID */
   private static final long serialVersionUID = 1L;
   private SessionContext sessionCtx;
   private static final Logger log = Logger.getLogger(TransactionActiveBean.class);

   public void setupDatabase()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         DataSource ds = (DataSource) ctx.lookup("java:DefaultDS");
         Connection c = ds.getConnection();
         try
         {
            Statement stmt = c.createStatement();
            try
            {
               stmt.executeUpdate("create table JCA_TRANSACTION_ACTIVE (name varchar(100))");
            }
            catch (SQLException ignored)
            {
            }
            stmt.executeUpdate("delete from JCA_TRANSACTION_ACTIVE");
            stmt.executeUpdate("insert into JCA_TRANSACTION_ACTIVE values ('100')");
         }
         finally
         {
            c.close();
         }
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unexpected Error: ", e);
      }
   }

   public void changeDatabase()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         UserTransaction ut = sessionCtx.getUserTransaction();
         ut.setTransactionTimeout(5);
         ut.begin();
         try
         {
            DataSource ds = (DataSource) ctx.lookup("java:DefaultDS");
            Connection c = ds.getConnection();
            try
            {
               Statement stmt = c.createStatement();
               stmt.executeUpdate("insert into JCA_TRANSACTION_ACTIVE values ('101')");
               try
               {
                  Thread.sleep(10000);
               }
               catch (InterruptedException ignored)
               {
               }
               try
               {
                  stmt.executeUpdate("delete from JCA_TRANSACTION_ACTIVE where name='100'");
               }
               catch (SQLException expected)
               {
                  log.debug("Got expected exception: " + expected);
               }
            }
            finally
            {
               try
               {
                  c.close();
               }
               catch (Exception ignored)
               {
               }
            }
         }
         finally
         {
            try
            {
               ut.rollback();
            }
            catch (Exception ignored)
            {
            }
         }
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unexpected Error: ", e);
      }
   }

   public void checkDatabase()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         UserTransaction ut = sessionCtx.getUserTransaction();
         ut.begin();
         try
         {
            DataSource ds = (DataSource) ctx.lookup("java:DefaultDS");
            Connection c = ds.getConnection();
            try
            {
               Statement stmt = c.createStatement();
               ResultSet rs = stmt.executeQuery("select name from JCA_TRANSACTION_ACTIVE");
               if (rs.next() == false)
                  throw new RuntimeException("Expected a first row");
               String value = rs.getString(1);
               if ("100".equals(value) == false)
                  throw new RuntimeException("Expected first row to be 100 got " + value);
               if (rs.next())
                  throw new RuntimeException("Expected only one row");
            }
            finally
            {
               try
               {
                  c.close();
               }
               catch (Exception ignored)
               {
               }
            }
         }
         finally
         {
            try
            {
               ut.commit();
            }
            catch (Exception ignored)
            {
            }
         }
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unexpected Error: ", e);
      }
   }

   public void setupQueue()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         Queue queue = (Queue) ctx.lookup("queue/testQueue");
         UserTransaction ut = sessionCtx.getUserTransaction();
         ut.begin();
         try
         {
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("java:JmsXA");
            javax.jms.Connection c = cf.createConnection();
            try
            {
               c.start();
               Session s = c.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer mc = s.createConsumer(queue);
               while (mc.receive(1000) != null);
               mc.close();
               
               MessageProducer p = s.createProducer(queue);
               Message m = s.createTextMessage("101");
               p.send(m);
            }
            finally
            {
               try
               {
                  c.close();
               }
               catch (Exception ignored)
               {
               }
            }
         }
         finally
         {
            try
            {
               ut.commit();
            }
            catch (Exception ignored)
            {
            }
         }
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unexpected Error: ", e);
      }
   }

   public void changeQueue()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         Queue queue = (Queue) ctx.lookup("queue/testQueue");
         UserTransaction ut = sessionCtx.getUserTransaction();
         ut.setTransactionTimeout(5);
         ut.begin();
         try
         {
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("java:JmsXA");
            javax.jms.Connection c = cf.createConnection();
            try
            {
               c.start();
               Session s = c.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer mc = s.createConsumer(queue);
               mc.receive(1000);
               mc.close();
               
               try
               {
                  Thread.sleep(10000);
               }
               catch (InterruptedException ignored)
               {
               }
               
               try
               {
                  MessageProducer p = s.createProducer(queue);
                  Message m = s.createTextMessage("100");
                  p.send(m);
               }
               catch (JMSException expected)
               {
               }
            }
            finally
            {
               try
               {
                  c.close();
               }
               catch (Exception ignored)
               {
               }
            }
         }
         finally
         {
            try
            {
               ut.commit();
            }
            catch (Exception ignored)
            {
            }
         }
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unexpected Error: ", e);
      }
   }

   public void checkQueue()
   {
      try
      {
         InitialContext ctx = new InitialContext();
         Queue queue = (Queue) ctx.lookup("queue/testQueue");
         UserTransaction ut = sessionCtx.getUserTransaction();
         ut.begin();
         try
         {
            ConnectionFactory cf = (ConnectionFactory) ctx.lookup("java:JmsXA");
            javax.jms.Connection c = cf.createConnection();
            try
            {
               c.start();
               Session s = c.createSession(true, Session.SESSION_TRANSACTED);
               MessageConsumer mc = s.createConsumer(queue);
               Message m = mc.receive(1000);
               if (m == null || m instanceof TextMessage == false)
                  throw new RuntimeException("Expected one text message: " + m);
               String value = ((TextMessage) m).getText();
               if ("101".equals(value) == false)
                  throw new RuntimeException("Message should have text 101 got: " + value);
               if (mc.receive(1000) != null)
                  throw new RuntimeException("Did not expect two messages");
            }
            finally
            {
               try
               {
                  c.close();
               }
               catch (Exception ignored)
               {
               }
            }
         }
         finally
         {
            try
            {
               ut.commit();
            }
            catch (Exception ignored)
            {
            }
         }
      }
      catch (RuntimeException e)
      {
         throw e;
      }
      catch (Exception e)
      {
         throw new RuntimeException("Unexpected Error: ", e);
      }
   }

   public void ejbCreate() 
   {
   }

   public void ejbActivate()
   {
    }

   public void ejbPassivate()
   {
   }

   public void ejbRemove()
   {
   }

   public void setSessionContext(SessionContext ctx)
   {
      this.sessionCtx = ctx;
   }

   public void unsetSessionContext()
   {
      this.sessionCtx = null;
   }

}
