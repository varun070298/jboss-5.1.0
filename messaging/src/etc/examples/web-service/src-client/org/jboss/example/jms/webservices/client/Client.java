/*
   * JBoss, Home of Professional Open Source
   * Copyright 2005, JBoss Inc., and individual contributors as indicated
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

package org.jboss.example.jms.webservices.client;

import org.jboss.example.jms.common.ExampleSupport;
import org.jboss.example.jms.webservices.endpoint.JMSSample;
import org.jboss.ws.core.jaxrpc.client.ServiceFactoryImpl;
import org.jboss.ws.core.jaxrpc.client.ServiceImpl;
import javax.xml.rpc.ServiceFactory;
import javax.xml.rpc.ServiceException;
import javax.xml.namespace.QName;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.jms.Destination;
import javax.jms.ConnectionFactory;
import javax.jms.Session;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.File;
import java.rmi.RemoteException;

import javax.jms.*;
import javax.jms.IllegalStateException;

/**
 * This Client will use auto generated classes from WebServices. You need to compile this class using ant.
 * 
 * @author <a href="mailto:clebert.suconic@jboss.org">Clebert Suconic</a>
 * @version <tt>$Revision: 85945 $</tt>
 *          $Id: Client.java 85945 2009-03-16 19:45:12Z dimitris@jboss.org $
 */
public class Client extends ExampleSupport
{
   private static final String MESSAGE_TEXT = "Hello from a WebService!";

   public void example() throws Exception
   {
      sendMessage();

      receiveMessage();

      System.out.println("Message was received ok!");
   }

   private void sendMessage()
      throws ServiceException, MalformedURLException, RemoteException
   {
      ServiceFactoryImpl factory = (ServiceFactoryImpl) ServiceFactory.newInstance();

      URL wsdlLocation = new URL("http://127.0.0.1:8080/jms-web-service/JMSWebServiceExample?wsdl");
      QName serviceName = new QName("http://endpoint.webservices.jms.example.jboss.org/", "JMSSampleService");

      File fileMapping = new File("./output/client/jaxrpc-mapping.xml");

      ServiceImpl service = (ServiceImpl)factory.createService(wsdlLocation, serviceName, fileMapping.toURL());

      JMSSample proxy = (JMSSample)service.getPort(JMSSample.class);

      proxy.sendMessage(getDestinationJNDIName(), MESSAGE_TEXT);
   }

   private void receiveMessage()
      throws NamingException, JMSException
   {
      InitialContext ctx = new InitialContext();
      Destination dest = (Destination)ctx.lookup(getDestinationJNDIName());

      ConnectionFactory cf = (ConnectionFactory)ctx.lookup("/ConnectionFactory");
      Connection conn = cf.createConnection();
      conn.start();

      Session sess = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

      MessageConsumer consumer = sess.createConsumer(dest);
      TextMessage msg = (TextMessage)consumer.receive(10000);

      conn.close();

      if (msg == null)
      {
         throw new IllegalStateException("Couldn't receive message");
      }

      if (!msg.getText().equals(MESSAGE_TEXT))
      {
         throw new IllegalStateException("Couldn't receive message");
      }
   }

   protected boolean isQueueExample()
   {
      return true;
   }

   public static void main(String[] args) throws Exception
   {
      new Client().run();
   }
}