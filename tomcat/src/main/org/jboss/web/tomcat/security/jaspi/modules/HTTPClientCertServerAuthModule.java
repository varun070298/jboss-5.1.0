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
package org.jboss.web.tomcat.security.jaspi.modules;

import java.io.IOException;
import java.security.Principal;
import java.security.cert.X509Certificate;

import javax.security.auth.Subject;
import javax.security.auth.message.AuthException;
import javax.security.auth.message.AuthStatus;
import javax.security.auth.message.MessageInfo;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.util.StringManager;
import org.apache.coyote.ActionCode;
import org.jboss.logging.Logger;

/**
 * Server Auth Module for HTTP CLIENT-CERT
 * @author Anil.Saldhana@redhat.com
 * @since May 18, 2009
 */
public class HTTPClientCertServerAuthModule extends TomcatServerAuthModule
{
   private static Logger log = Logger.getLogger(HTTPClientCertServerAuthModule.class);

   protected Context context; 

   protected boolean cache = false;

   private String delgatingLoginContextName;

   public static final String CERTIFICATES_ATTR =
      "javax.servlet.request.X509Certificate";

   protected static final StringManager sm =
      StringManager.getManager(Constants.Package);


   public HTTPClientCertServerAuthModule()
   {
      super(); 
   }

   public HTTPClientCertServerAuthModule(String delgatingLoginContextName)
   {
      super();
      this.delgatingLoginContextName = delgatingLoginContextName;
   }

   @Override
   public AuthStatus secureResponse(MessageInfo messageInfo, Subject serviceSubject) throws AuthException
   {
      throw new RuntimeException("Not Applicable");
   }

   @Override
   public AuthStatus validateRequest(MessageInfo messageInfo, Subject clientSubject, Subject serviceSubject)
   throws AuthException
   {
      Request request = (Request) messageInfo.getRequestMessage();
      Response response = (Response) messageInfo.getResponseMessage();

      Principal principal;
      context = request.getContext(); 

      X509Certificate certs[] = (X509Certificate[])
      request.getAttribute(CERTIFICATES_ATTR);
      if ((certs == null) || (certs.length < 1)) {
         request.getCoyoteRequest().action
         (ActionCode.ACTION_REQ_SSL_CERTIFICATE, null);
         certs = (X509Certificate[])
         request.getAttribute(CERTIFICATES_ATTR);
      }
      if ((certs == null) || (certs.length < 1)) {
         log.debug("  No certificates included with this request");
         try
         {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                  sm.getString("authenticator.certificates"));
         }
         catch (IOException e)
         {
            log.error(e.getLocalizedMessage(),e);
         }
         return (AuthStatus.FAILURE);
      }

      // Authenticate the specified certificate chain
      principal = context.getRealm().authenticate(certs);
      if (principal == null) {
         log.debug("  Realm.authenticate() returned false");
         try
         {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                  sm.getString("authenticator.unauthorized"));
         }
         catch (IOException e)
         {
            log.error(e.getLocalizedMessage(),e);
         }
         return (AuthStatus.FAILURE);
      }

      registerWithCallbackHandler(principal, 
            principal.getName(), 
            null);
      // Cache the principal (if requested) and record this authentication
      /*register(request, response, principal, Constants.CERT_METHOD,
            null, null);*/
      return (AuthStatus.SUCCESS); 
   }  
}