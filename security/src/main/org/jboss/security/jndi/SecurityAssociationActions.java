/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2006, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.security.jndi;

import java.security.PrivilegedAction;
import java.security.Principal;
import java.security.AccessController; 

import javax.security.auth.Subject;
import org.jboss.security.SecurityAssociation;

/** A PrivilegedAction implementation for setting the SecurityAssociation
 * principal and credential
 * 
 * @author Scott.Stark@jboss.org
 * @version $Revison:$
 */
class SecurityAssociationActions
{
   private static class SetPrincipalInfoStackAction implements PrivilegedAction
   {
      Principal principal;
      Object credential;
      Subject subject;
      SetPrincipalInfoStackAction(Principal principal, Object credential, Subject subject)
      {
         this.principal = principal;
         this.credential = credential;
         this.subject = subject;
      }
      public Object run()
      {
         SecurityAssociation.pushSubjectContext(subject, principal, credential);
         credential = null;
         principal = null;
         subject = null;
         return null;
      }
   }

   private static class SetPrincipalInfoAction implements PrivilegedAction
   {
      Principal principal;
      Object credential;
      SetPrincipalInfoAction(Principal principal, Object credential)
      {
         this.principal = principal;
         this.credential = credential;
      }
      public Object run()
      {
         SecurityAssociation.setCredential(credential);
         credential = null;
         SecurityAssociation.setPrincipal(principal);
         principal = null;
         return null;
      }
   }
   private static class PopPrincipalInfoAction implements PrivilegedAction
   {
      public Object run()
      {
         SecurityAssociation.popSubjectContext();
         return null;
      }
   }

   private static class GetTCLAction implements PrivilegedAction
   {
      static PrivilegedAction ACTION = new GetTCLAction();
      public Object run()
      {
         ClassLoader loader = Thread.currentThread().getContextClassLoader();
         return loader;
      }
   }

   private static class SetServerAction implements PrivilegedAction
   {
      static PrivilegedAction ACTION = new SetServerAction();
      public Object run()
      {
         SecurityAssociation.setServer();
         return null;
      }
   }
   static void setPrincipalInfo(Principal principal, Object credential)
   {
      SetPrincipalInfoAction action = new SetPrincipalInfoAction(principal, credential);
      AccessController.doPrivileged(action);
   }
   static void setPrincipalInfo(Principal principal, Object credential, Subject subject)
   {
      SetPrincipalInfoStackAction action = new SetPrincipalInfoStackAction(principal, credential, subject);
      AccessController.doPrivileged(action);
   }
   static void popPrincipalInfo()
   {
      PopPrincipalInfoAction action = new PopPrincipalInfoAction();
      AccessController.doPrivileged(action);
   }

   static void setServer()
   {
      AccessController.doPrivileged(SetServerAction.ACTION);
   }
   static ClassLoader getContextClassLoader()
   {
      ClassLoader loader = (ClassLoader) AccessController.doPrivileged(GetTCLAction.ACTION);
      return loader;
   }

}
