/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.profileservice.management;

import org.jboss.deployers.spi.management.DelegatingComponentDispatcher;
import org.jboss.deployers.spi.management.RuntimeComponentDispatcher;
import org.jboss.managed.api.ManagedOperation;
import org.jboss.managed.api.ManagedProperty;
import org.jboss.metatype.api.values.MetaValue;

/**
 * A delegating runtime component dispatcher, used as the proxy for
 * ManagedProperty gets and ManagedOperation invokes.
 * 
 * @author <a href="mailto:emuckenh@redhat.com">Emanuel Muckenhuber</a>
 * @author Scott.Stark@jboss.org
 * @version $Revision: 87673 $
 */
public class DelegatingComponentDispatcherImpl
   implements DelegatingComponentDispatcher
{
  
   private RuntimeComponentDispatcher dispatcher;
   private ProxyRegistry registry;

   public DelegatingComponentDispatcherImpl(ProxyRegistry registry, RuntimeComponentDispatcher dispatcher)
   {
      this.registry = registry;
      this.dispatcher = dispatcher;
   }

   public MetaValue get(Long propID, Object componentName, String propertyName)
   {
      ManagedProperty mp = this.registry.getManagedProperty(propID);
      AbstractRuntimeComponentDispatcher.setActiveProperty(mp);
      return dispatcher.get(componentName, propertyName);
   }

   public MetaValue invoke(Long opID, Object componentName, String methodName, MetaValue... param)
   {
      ManagedOperation op = this.registry.getManagedOperation(opID);
      AbstractRuntimeComponentDispatcher.setActiveOperation(op);

      if(param == null)
         param = new MetaValue[0];
      
      MetaValue result = null;
      if (componentName != null)
      {
         result = (MetaValue) dispatcher.invoke(componentName, methodName, param);
      }
      return result; 
   }

   public static interface ProxyRegistry
   {
    
      ManagedProperty getManagedProperty(Long propID);
      ManagedOperation getManagedOperation(Long opID);

   }
   
}
