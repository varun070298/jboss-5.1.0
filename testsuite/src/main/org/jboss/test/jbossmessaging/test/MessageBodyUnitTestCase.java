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
package org.jboss.test.jbossmessaging.test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;

import junit.framework.Test;

import org.jboss.logging.Logger;
import org.jboss.test.jbossmessaging.JMSTestCase;

/**
 * Tests message bodies.
 *
 * @author <a href="mailto:richard.achmatowicz@jboss.com">Richard Achmatowicz</a>
 * @author Loren Rosen (submitted patch)
 * @author <a href="mailto:jason@planet57.com">Jason Dillon</a>
 * @version $Revision: 85945 $
 */
public class MessageBodyUnitTestCase extends JMSTestCase
{
   // Provider specific
   public static final String QUEUE_FACTORY = "ConnectionFactory";
   public static final String TEST_QUEUE = "queue/testQueue";

   Context context;
   QueueConnection queueConnection;
   QueueSession session;
   Queue queue;

   QueueReceiver receiver;
   QueueSender sender;

   public MessageBodyUnitTestCase(String name) throws Exception
   {
      super(name);
   }

   protected void setUp() throws Exception
   {
       // call setUp() in the superclass
       super.setUp() ;

      connect();
   }

   protected void tearDown() throws Exception
   {
      disconnect();
      
      // call tearDown() in the superclass to cleanup
      super.tearDown() ;
   }

   protected void connect() throws Exception
   {
      getLog().debug("connecting");
      if (context == null)
      {
         context = getInitialContext();
      }

      QueueConnectionFactory queueFactory = (QueueConnectionFactory) context.lookup(QUEUE_FACTORY);
      queueConnection = queueFactory.createQueueConnection();
      getLog().debug("connected");

      queueConnection.start();
      session = queueConnection.createQueueSession(false, Session.AUTO_ACKNOWLEDGE);
      getLog().debug("session established");

      queue = (Queue) context.lookup(TEST_QUEUE);

      receiver = session.createReceiver(queue);
      sender = session.createSender(queue);
      getLog().debug("sender established");

      drainQueue();
      getLog().debug("end of connect call");
   }

   protected void disconnect() throws Exception
   {
      queueConnection.close();
   }
   
   

   private void drainQueue() throws Exception
   {
      getLog().debug("draining queue");

      Message message = receiver.receive(2000);
      int c = 0;
      while (message != null)
      {
         message = receiver.receive(2000);
         c++;
      }

      if (c != 0)
         getLog().debug("Drained " + c + " messages from the queue");

      getLog().debug("drained queue");

   }

   protected void validate(String payload) throws Exception
   {
      getLog().debug("validating text |" + payload + "|");

      TextMessage outMessage = session.createTextMessage();
      outMessage.setText(payload);
      getLog().debug("sending |" + payload + "|");
      sender.send(outMessage);

      getLog().debug("receiving |" + payload + "|");
      TextMessage inMessage = (TextMessage) receiver.receive();
      getLog().debug("received |" + payload + "|");
      String inPayload = inMessage.getText();

      assertEquals("Message body text test", payload, inPayload);
      getLog().debug("validated text " + payload);
   }

   public void testTextMessageBody() throws Exception
   {
      getLog().debug("testing text");

      validate("ordinary text");
      validate(" ");
      validate("");
      // very long strings, non-printable ASCII strings
      char c[] = new char[1024 * 32];
      Arrays.fill(c, 'x');
      validate(new String(c));
      Arrays.fill(c, '\u0130'); // I with dot
      validate(new String(c));
      Arrays.fill(c, '\u0008');
      validate(new String(c));
      getLog().debug("tested text");
   }

   protected void validate(java.io.Serializable payload) throws Exception
   {
      ObjectMessage outMessage = session.createObjectMessage();
      outMessage.setObject(payload);
      sender.send(outMessage);

      ObjectMessage inMessage = (ObjectMessage) receiver.receive();
      Object inPayload = inMessage.getObject();

      assertEquals("Message body object test", payload, inPayload);
   }

   public void testObjectMessageBody() throws Exception
   {
      getLog().debug("testing object");
      validate(new Integer(0));
      validate(new Integer(1));
      validate(new Integer(-1));
      validate(new Integer(Integer.MAX_VALUE));
      validate(new Integer(Integer.MIN_VALUE));
      validate(new Integer(-1));
      validate(new Float(1.0));
      validate(new Float(0.0));
      validate(new Float(-1.0));
      validate(new Float(Float.MAX_VALUE));
      validate(new Float(Float.MIN_VALUE));
      validate(new Float(Float.NaN));
      validate(new Float(Float.POSITIVE_INFINITY));
      validate(new Float(Float.NEGATIVE_INFINITY));
      validate(new Float(1.0));
      HashMap m = new HashMap(); // Fill with serializable stuff
      m.put("file", new java.io.File("somefile.txt"));
      m.put("url", new java.net.URL("http://example.net"));
      validate(m);
      validate((java.io.Serializable)Collections.nCopies(10000, "Repeat"));
   }

   /**
    * Test null properties.
    */
   public void testNullProperties() throws Exception
   {
      TextMessage message = session.createTextMessage();

      message.setStringProperty("THE_PROP", null);
      message.setObjectProperty("THE_PROP2", null);

      try
      {
        message.setStringProperty("", null);
        fail("empty string property");
      }
      catch (IllegalArgumentException e) {}

      try
      {
        message.setStringProperty(null, null);
        fail("null property");
      }
      catch (IllegalArgumentException e) {}
   }

   public void testInvalidPropertyName() throws Exception
   {
      Message message = session.createMessage();

      String[] invalid = new String[]
      {
         "invalid-hyphen",
         "1digitfirst",
         "NULL",
         "TRUE",
         "FALSE",
         "NOT",
         "AND",
         "OR",
         "BETWEEN",
         "LIKE",
         "IN",
         "IS",
         "ESCAPE"
      };

      for (int i = 0; i < invalid.length; ++i)
      {
         try
         {
            message.setStringProperty(invalid[i], "whatever");
            fail("expected error for invalid property name " + invalid[i]);
         }
         catch (IllegalArgumentException expected)
         {
         }
      }

      String[] valid = new String[]
      {
         "identifier",
         "_",
         "$",
         "_xSx",
         "$x_x",
         "A1",
         "null",
         "true",
         "false",
         "not",
         "and",
         "or",
         "between",
         "like",
         "in",
         "is",
         "escape"
      };

      for (int i = 0; i < invalid.length; ++i)
         message.setStringProperty(valid[i], "whatever");
   }

   /**
    * Test vendor properties.
    * Test disabled for JBossMessaging
    */
   /*public void testVendorProperties() throws Exception
   {
      TextMessage message = session.createTextMessage();
      
      System.out.println("hello!!!");

      try
      {
        message.setStringProperty("JMS_JBOSS_SCHEDULED_DELIVERY", "whenever");
        fail("invalid type");
      }
      catch (JMSException e) {
    	  e.printStackTrace();
      }
      try
      {
        message.setObjectProperty("JMS_JBOSS_SCHEDULED_DELIVERY", "10234");
        fail("invalid type");
      }
      catch (JMSException e) {
    	  e.printStackTrace();
      }
      try
      {
        message.setStringProperty("JMS_JBOSS_REDELIVERY_COUNT", "fruity");
        fail("invalid type");
      }
      catch (JMSException e) {
    	  e.printStackTrace();
      }
      try
      {
        message.setStringProperty("JMS_JBOSS_REDELIVERY_LIMIT", "fruity");
        fail("invalid type");
      }
      catch (JMSException e) {
    	  e.printStackTrace();
      }

      message.setLongProperty("JMS_JBOSS_SCHEDULED_DELIVERY", 10234);
      message.setIntProperty("JMS_JBOSS_REDELIVERY_COUNT", 123);
      message.setShortProperty("JMS_JBOSS_REDELIVERY_LIMIT", (short)1);
   } */

   public static Test suite() throws Exception
   {
       ClassLoader loader = Thread.currentThread().getContextClassLoader();
       return getDeploySetup(MessageBodyUnitTestCase.class,
               loader.getResource("jbossmessaging/test-destinations-service.xml").toString());
   }

}
