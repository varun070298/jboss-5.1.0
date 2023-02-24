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
package org.jboss.test.system.controller.configure.value.element.test;

import javax.management.ObjectName;

import org.jboss.test.system.controller.AbstractControllerTest;
import org.jboss.test.system.controller.support.Simple;
import org.jboss.test.system.controller.support.SimpleMBean;
import org.w3c.dom.Element;

/**
 * TextValueTest.
 * 
 * @author <a href="adrian@jboss.com">Adrian Brock</a>
 * @version $Revision: 85945 $
 */
public abstract class ElementValueTest extends AbstractControllerTest
{
   public ElementValueTest(String name)
   {
      super(name);
   }

   public void testElement() throws Exception
   {
      ObjectName name = SimpleMBean.OBJECT_NAME;
      
      assertInstall(name);
      try
      {
         Simple test = getSimple();
         
         Element element = test.getElement();
         assertNotNull(element);
         
         String tagName = element.getTagName();
         assertNotNull(tagName);
         
         assertEquals("dummy", tagName);
      }
      finally
      {
         assertUninstall(name);
      }
   }
}
