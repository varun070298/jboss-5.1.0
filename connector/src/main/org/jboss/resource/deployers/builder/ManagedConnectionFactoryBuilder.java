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
package org.jboss.resource.deployers.builder;

import java.util.ArrayList;
import java.util.List;

import javax.management.ObjectName;

import org.jboss.mx.util.ObjectNameFactory;
import org.jboss.resource.metadata.ConnectorMetaData;
import org.jboss.resource.metadata.mcf.ManagedConnectionFactoryDeploymentMetaData;
import org.jboss.resource.metadata.repository.DefaultJCAMetaDataRepository;
import org.jboss.system.metadata.ServiceAttributeMetaData;
import org.jboss.system.metadata.ServiceConstructorMetaData;
import org.jboss.system.metadata.ServiceDependencyMetaData;

/**
 * A ManagedConnectionFactoryBuilder.
 * 
 * @author <a href="weston.price@jboss.org">Weston Price</a>
 * @version $Revision: 85945 $
 */
public class ManagedConnectionFactoryBuilder extends AbstractBuilder
{
   
   private static final String MCF = "org.jboss.resource.connectionmanager.ManagedConnectionFactoryDeployment";
   private static final String RAR_JMX = "jboss.jca:service=RARDeployment,name='";
   private static final String MCF_JMX = "jboss.jca:service=ManagedConnectionFactory,name=";

   private DefaultJCAMetaDataRepository repository;
   
   public DefaultJCAMetaDataRepository getMetaDataRepository()
   {
      return this.repository;
   }
   public void setMetaDataRepository(DefaultJCAMetaDataRepository repository)
   {
      this.repository = repository;      
   }

   @Override
   public ObjectName buildObjectName(ManagedConnectionFactoryDeploymentMetaData md)
   {
      return ObjectNameFactory.create(MCF_JMX + md.getJndiName());      
   }
   
   @Override
   public List<ServiceAttributeMetaData> buildAttributes(ManagedConnectionFactoryDeploymentMetaData deployment)
   {
      List<ServiceAttributeMetaData> attributes = new ArrayList<ServiceAttributeMetaData>();
      ServiceAttributeMetaData attribute = buildDependencyAttribute("OldRarDeployment", RAR_JMX + deployment.getRarName() + "'");      
      attributes.add(attribute);            
      return attributes;
   }
   
   @Override
   public ServiceConstructorMetaData buildConstructor(ManagedConnectionFactoryDeploymentMetaData mcfmd)
   {
      ServiceConstructorMetaData constructor = new ServiceConstructorMetaData();

      ConnectorMetaData md = repository.getConnectorMetaData(mcfmd.getRarName());
      if( md == null )
         throw new IllegalStateException("No ConnectorMetaData found for mdf rarName: "+mcfmd.getRarName());
      constructor.setParameters(new Object[]{md, mcfmd});
      constructor.setSignature(new String[]{md.getClass().getName(), ManagedConnectionFactoryDeploymentMetaData.class.getName()});
      return constructor;       
   }
   
   @Override
   public List<ServiceDependencyMetaData> buildDependencies(ManagedConnectionFactoryDeploymentMetaData md)
   {
      List<String> dependsNames = md.getDependsNames();
      List<ServiceDependencyMetaData> dependencies = new ArrayList<ServiceDependencyMetaData>();
      
      for (String string : dependsNames)
      {
         ServiceDependencyMetaData depends = buildDependency(string);
         dependencies.add(depends);         
      }
    
      return dependencies;
  
   }
   
   @Override   
   public String getCode(ManagedConnectionFactoryDeploymentMetaData md)
   {
      return MCF;
   }
 
}
