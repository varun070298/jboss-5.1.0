/*
 * Generated by XDoclet - Do not edit!
 */
package org.jboss.test.cluster.cache.bean;

/**
 * Home interface for test/TreeCacheTester.
 */
public interface TreeCacheTesterHome
   extends javax.ejb.EJBHome
{
   public static final String COMP_NAME="java:comp/env/ejb/test/TreeCacheTester";
   public static final String JNDI_NAME="ejb/test/TreeCacheTester";

   public TreeCacheTester create()
      throws javax.ejb.CreateException,java.rmi.RemoteException;

   public TreeCacheTester create(java.lang.String cluster_name , java.lang.String props , int caching_mode)
      throws javax.ejb.CreateException,java.rmi.RemoteException;

}