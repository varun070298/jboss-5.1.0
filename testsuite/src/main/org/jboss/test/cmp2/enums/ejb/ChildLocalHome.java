/*
 * Generated by XDoclet - Do not edit!
 */
package org.jboss.test.cmp2.enums.ejb;

/**
 * Local home interface for Child.
 */
public interface ChildLocalHome
   extends javax.ejb.EJBLocalHome
{
   public static final String COMP_NAME="java:comp/env/ejb/ChildLocal";
   public static final String JNDI_NAME="ChildLocal";

   public org.jboss.test.cmp2.enums.ejb.ChildLocal create(org.jboss.test.cmp2.enums.ejb.IDClass childId)
      throws javax.ejb.CreateException;

   public org.jboss.test.cmp2.enums.ejb.ChildLocal findByColor(org.jboss.test.cmp2.enums.ejb.ColorEnum color)
      throws javax.ejb.FinderException;

   public org.jboss.test.cmp2.enums.ejb.ChildLocal findByColorDeclaredSql(org.jboss.test.cmp2.enums.ejb.ColorEnum color)
      throws javax.ejb.FinderException;

   public java.util.Collection findLowColor(org.jboss.test.cmp2.enums.ejb.ColorEnum color)
      throws javax.ejb.FinderException;

   public org.jboss.test.cmp2.enums.ejb.ChildLocal findAndOrderByColor(org.jboss.test.cmp2.enums.ejb.ColorEnum color)
      throws javax.ejb.FinderException;

   public org.jboss.test.cmp2.enums.ejb.ChildLocal findByPrimaryKey(org.jboss.test.cmp2.enums.ejb.IDClass pk)
      throws javax.ejb.FinderException;

}
