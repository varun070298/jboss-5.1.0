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
package org.jboss.ejb.plugins.cmp.ejbql;


/**
 * This abstract syntax node represents AVG function.
 *
 * @author <a href="mailto:alex@jboss.org">Alex Loubyansky</a>
 * @version $Revision: 81030 $
 */
public final class ASTAvg
   extends AggregateFunction
   implements SelectFunction
{
   public ASTAvg(int id)
   {
      super(id);
   }

   /**
    * Accept the visitor. *
    */
   public Object jjtAccept(JBossQLParserVisitor visitor, Object data)
   {
      return visitor.visit(this, data);
   }
}
