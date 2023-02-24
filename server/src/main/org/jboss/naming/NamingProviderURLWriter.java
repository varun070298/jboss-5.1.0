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

package org.jboss.naming;

import java.io.File;
import java.io.PrintWriter;
import java.net.URI;

import org.jboss.bootstrap.spi.Server;
import org.jboss.logging.Logger;

/**
 *
 *
 * @author Brian Stansberry
 * 
 * @version $Revision: $
 */
public class NamingProviderURLWriter
{
   public static final String DEFAULT_PERSIST_FILE_NAME = "jnp-service.url";
   
   private static final Logger log = Logger.getLogger(NamingProviderURLWriter.class);
   
   private String bootstrapUrl;
   private Server server;
   private URI outputDir;
   private String filename = DEFAULT_PERSIST_FILE_NAME;
   private File outputFile;

   public URI getOutputDirURI()
   {
      return outputDir;
   }

   public void setOutputDirURL(URI dir)
   {
      this.outputDir = dir;
   }

   public String getOutputFileName()
   {
      return filename == null ? DEFAULT_PERSIST_FILE_NAME : filename;
   }

   public void setOutputFileName(String name)
   {
      this.filename = name;
   }

   public void setServer(Server server)
   {
      this.server = server;
   }

   public String getBootstrapURL()
   {
      return bootstrapUrl;
   }
   
   public void setBootstrapURL(String url)
   {
      this.bootstrapUrl = url;      
   }

   public void start() throws Exception
   {
      if (bootstrapUrl != null)
      {
         File base = null;
         if (outputDir == null)
         {
            if (server != null)
            {
               base =  server.getConfig().getServerDataDir();
               outputDir = base.toURI();
            }
         }
         else
         {
            base = new File(outputDir);
         }
         
         if (base != null)
         {
            base.mkdirs();
            
            outputFile = new File(base, getOutputFileName());
            if (outputFile.exists())
            {
               outputFile.delete();
            }
            if (log.isTraceEnabled())
            {
               log.trace("Creating file " + outputFile);
            }
            outputFile.createNewFile();            
            PrintWriter writer = new PrintWriter(outputFile);
            try
            {
               writer.println(bootstrapUrl);
               writer.flush();
            }
            finally
            {
               outputFile.deleteOnExit();
               writer.close();
            }
            
            outputFile.deleteOnExit();
         }
         else
         {
            log.warn("No directory specified for " + getOutputFileName() + 
                  " cannot write the naming service url. Please configure either " +
                  "the 'server' property or the 'outputDir' property.");
         }
      }
      else
      {
         log.debug("No URLs to write");
      }
   }

   public void stop() throws Exception
   {
      if (outputFile != null)
      {
         outputFile.delete();
      }
   }

}
