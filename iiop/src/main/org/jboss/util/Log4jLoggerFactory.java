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
package org.jboss.util;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.logger.Logger;
import org.apache.avalon.framework.logger.Log4JLogger;
import org.jacorb.config.LoggerFactory;
import org.jboss.logging.log4j.Log4jLoggerPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * JacORB logger factory that creates named Avalon loggers with log4j
 * as the underlying log mechanism.
 *
 *  @author <a href="mailto:reverbel@ime.usp.br">Francisco Reverbel</a>
 *  @version $Revision: 81018 $
 */
public class Log4jLoggerFactory
   implements LoggerFactory
{
   /** logging back-end mechanism used by all Log4jLoggerFactory instances */
   private final static String name = "log4j";
   
   /** cache of created loggers */
   private final Map namedLoggers = new HashMap();

   // Auxiliary static methods --------------------------------------

   /**
    * Gets a log4j logger by name.
    *
    * @param name the name of the logger
    * @return an <code>org.apache.log4j.Logger</code> instance
    */
   private static org.apache.log4j.Logger getLog4jLogger(String name)
   {
      org.jboss.logging.Logger l = org.jboss.logging.Logger.getLogger(name);
      org.jboss.logging.LoggerPlugin lp = l.getLoggerPlugin();
      if (lp instanceof Log4jLoggerPlugin)
         return ((Log4jLoggerPlugin)lp).getLogger();
      else
      {
         return null;
      }
   }
   
   
   // Implementation of org.apache.avalon.framework.configuration.Configuration

   public void configure(Configuration configuration)
      throws ConfigurationException
   {
   }
   
   // Implementation of org.jacorb.util.LoggerFactory ---------------

   /**
    * Gets the name of the logging back-end mechanism.
    *
    * @return the string <code>"log4j"</code> 
    */
   public final String getLoggingBackendName()
   {
      return name;
   }
   
   /**
    * Gets an Avalon logger by name.
    *
    * @param name the name of the logger
    * @return an <code>org.apache.avalon.framework.logger.Logger</code> 
    *         instance
    */
   public Logger getNamedLogger(String name)
   {
      Object o = namedLoggers.get(name);
      
      if (o != null)
         return (Logger)o;
      
      org.apache.log4j.Logger log4jLogger = getLog4jLogger(name);
      Logger logger = new Log4JLogger(log4jLogger);
      
      namedLoggers.put(name, logger);
      return logger;
   }
   
   /**
    * Gets an Avalon root logger by name.
    *
    * @param name the name of the logger
    * @return an <code>org.apache.avalon.framework.logger.Logger</code> 
    *         instance
    */
   public Logger getNamedRootLogger(String name)
   {
      return getNamedLogger(name);
   }
   
   /**
    * Creates a named Avalon logger with given <code>logFileName</code>
    * and <code>maxLogSize</code> parameters. This is a dummy implementation
    * that always return null.
    *
    * @param name the name of the logger
    * @param logFileName the name of the file to log to
    * @param maxLogSize maximum size of the log file. 
    *
    * @return the new logger (null in this implementation).
    */
   public Logger getNamedLogger(String name,
                                String logFileName, long maxLogSize)
      throws java.io.IOException
   {
      return null;
   }
   
   /**
    * set the file name and max file size for logging to a file
    */ 
   public void setDefaultLogFile(String fileName, long maxLogSize) 
      throws java.io.IOException 
   {
      // not implemented
   }

}
