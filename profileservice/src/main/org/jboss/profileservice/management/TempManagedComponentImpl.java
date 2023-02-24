/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.profileservice.management;

import java.io.ObjectStreamException;

import org.jboss.managed.api.ComponentType;
import org.jboss.managed.api.ManagedCommon;
import org.jboss.managed.api.ManagedDeployment;
import org.jboss.managed.api.ManagedObject;
import org.jboss.managed.api.RunStateMapper;
import org.jboss.managed.plugins.ManagedComponentImpl;

/**
 * Temp managed component impl where getParent points to the defining
 * ManagedObject.
 * 
 * @author <a href="mailto:emuckenh@redhat.com">Emanuel Muckenhuber</a>
 * @version $Revision$
 */
public class TempManagedComponentImpl extends ManagedComponentImpl
{
   private static final long serialVersionUID = 1;
   
   public TempManagedComponentImpl(ComponentType type, ManagedDeployment owner, ManagedObject mo)
   {
      this(type, owner, mo, null);
   }
   public TempManagedComponentImpl(ComponentType type, ManagedDeployment owner, ManagedObject mo,
         RunStateMapper stateMapper)
   {
      super(type, owner, mo, stateMapper);
   }

   @Override
   public ManagedCommon getParent()
   {
      return getDelegate();
   }

   private Object writeReplace() throws ObjectStreamException
   {
      // Only expose the ManagedComponentImpl
      ManagedComponentImpl comp = new ManagedComponentImpl(getType(), getDeployment(), getDelegate(), getStateMapper()); 
      comp.setRunState(getRunState());
      return comp;
   }
}
