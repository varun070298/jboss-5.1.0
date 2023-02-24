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
package org.jboss.ha.framework.server;

import javax.management.ObjectName;

import org.jboss.ha.framework.interfaces.HAPartition;
import org.jboss.mx.util.ObjectNameFactory;

/** 
 * MBean interface for FarmMemberService
 *
 * @author <a href="mailto:bill@jboss.org">Bill Burke</a>
 * @version $Revision: 86549 $
 *
 * <p><b>20020809 bill burke:</b>
 * <ul>
 *   <li>Initial import
 * </ul>
 *
 * @deprecated No longer used since 5.0.0; will be removed in 6.0.0
 */
public interface FarmMemberServiceMBean 
{
   /** The default object name. */
   ObjectName OBJECT_NAME = ObjectNameFactory.create("jboss:service=FarmMember");

   /** 
    * Gets the name of the partition used by this service.  This is a 
    * convenience method as the partition name is an attribute of HAPartition.
    * 
    * @return the name of the partition
    */
   String getPartitionName();
  
   /**
    * Get the underlying partition used by this service.
    * 
    * @return the partition
    */
   HAPartition getHAPartition();
   
   /**
    * Sets the underlying partition used by this service.
    * Can be set only when the MBean is not in a STARTED or STARTING state.
    * 
    * @param clusterPartition the partition
    */
   void setHAPartition(HAPartition clusterPartition);
}
