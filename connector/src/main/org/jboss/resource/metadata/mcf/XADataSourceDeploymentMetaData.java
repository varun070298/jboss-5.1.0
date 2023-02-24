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
package org.jboss.resource.metadata.mcf;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.CollapsedStringAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.jboss.managed.api.annotation.ManagementComponent;
import org.jboss.managed.api.annotation.ManagementObject;
import org.jboss.managed.api.annotation.ManagementProperty;
import org.jboss.managed.api.annotation.ManagementPropertyFactory;
import org.jboss.resource.deployers.management.ConnectionFactoryProperty;
import org.jboss.resource.deployers.management.XAConnectionFactoryProperty;


/**
 * A XADataSourceDeploymentMetaData.
 * 
 * @author <a href="weston.price@jboss.org">Weston Price</a>
 * @version $Revision: 88155 $
 */
@XmlType(name="xa-datasource")
@XmlAccessorType(XmlAccessType.FIELD)
@ManagementObject(componentType=@ManagementComponent(type="DataSource",subtype="XA"))
public class XADataSourceDeploymentMetaData extends DataSourceDeploymentMetaData
{

   /** The serialVersionUID */
   private static final long serialVersionUID = -6919645811610960978L;
   
   private static final String RAR_NAME = "jboss-xa-jdbc.rar";
   
   @XmlElement(name="xa-datasource-class")
   @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
   private String xaDataSourceClass;
   
   @XmlElement(name="xa-datasource-property")
   private List<XAConnectionPropertyMetaData> xaDataSourceProperties = new ArrayList<XAConnectionPropertyMetaData>();
   
   @XmlElement(name="url-property")
   @XmlJavaTypeAdapter(CollapsedStringAdapter.class)
   private String urlProperty;

   @XmlElement(name="xa-resource-timeout")
   private int xaResourceTimeout;

   public XADataSourceDeploymentMetaData()
   {
      setRarName(RAR_NAME);
      setTransactionSupportMetaData(ManagedConnectionFactoryTransactionSupportMetaData.XA);
   }

   @ManagementProperty(name="xa-datasource-class",
         description="The XADataSource class",
         mandatory=true, includeInTemplate=true)
   public String getXaDataSourceClass()
   {
      return xaDataSourceClass;
   }

   public void setXaDataSourceClass(String xaDataSourceClass)
   {
      this.xaDataSourceClass = xaDataSourceClass;
   }

   @ManagementProperty(name="url-property",
         description="The url-property",
         includeInTemplate=true)
   public String getURLProperty()
   {
      return urlProperty;
   }

   public void setURLProperty(String urlProperty)
   {
      this.urlProperty = urlProperty;
   }

   @ManagementProperty(name="xa-resource-timeout",
         description="The XAResource timeout",
         includeInTemplate=true)
   public int getXaResourceTimeout()
   {
      return xaResourceTimeout;
   }

   public void setXaResourceTimeout(int xaResourceTimeout)
   {
      this.xaResourceTimeout = xaResourceTimeout;
   }
   
   @ManagementProperty(name="xa-datasource-properties",
         description="The xa datasource properties",
         managed=true, includeInTemplate=true)
   @ManagementPropertyFactory(XAConnectionFactoryProperty.class)
   public List<XAConnectionPropertyMetaData> getXADataSourceProperties()
   {
      return this.xaDataSourceProperties;      
   }
   
   public void setXADataSourceProperties(List<XAConnectionPropertyMetaData> xaDataSourceProperties)
   {
      this.xaDataSourceProperties = xaDataSourceProperties;
   }
   
   @Override
   @ManagementProperty(name="config-property",
         description="The connection factory property info",
         managed=true, readOnly = true)
   @ManagementPropertyFactory(ConnectionFactoryProperty.class)
   public List<ManagedConnectionFactoryPropertyMetaData> getManagedConnectionFactoryProperties()
   {
      List<ManagedConnectionFactoryPropertyMetaData> properties = super.getManagedConnectionFactoryProperties();
      ManagedConnectionFactoryPropertyMetaData property = null;
      
      if(getXaDataSourceClass() != null)
      {
         property = new ManagedConnectionFactoryPropertyMetaData();
         property.setName("XADataSourceClass");
         property.setValue(getXaDataSourceClass());
         properties.add(property);
      }
      
      List<XAConnectionPropertyMetaData> dsProps = getXADataSourceProperties();
      
      StringBuffer dsBuff = new StringBuffer();

      if (dsProps != null)
      {
         for (XAConnectionPropertyMetaData data : dsProps)
         {
            dsBuff.append(data.getName() + "=" + data.getValue() + "\n");
         }
      }
      
      property = new ManagedConnectionFactoryPropertyMetaData();
      property.setName("XADataSourceProperties");
      property.setValue(dsBuff.toString());
      properties.add(property);
      
      if(getURLProperty() != null)
      {
         property = new ManagedConnectionFactoryPropertyMetaData();
         property.setName("URLProperty");
         property.setValue(getURLProperty());
         properties.add(property);
      }
   
      if(getIsSameRMOverrideValue() != null)
      {
         property = new ManagedConnectionFactoryPropertyMetaData();
         property.setName("IsSameRMOverrideValue");
         property.setType("java.lang.Boolean");
         property.setValue(String.valueOf(getIsSameRMOverrideValue()));
         properties.add(property);         
      }

      return properties;
   }
}
