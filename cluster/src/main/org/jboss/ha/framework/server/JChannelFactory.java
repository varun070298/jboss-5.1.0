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

import java.net.InetAddress;
import java.rmi.dgc.VMID;
import java.rmi.server.UID;
import java.security.AccessController;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.jboss.bootstrap.spi.util.ServerConfigUtil;
import org.jboss.logging.Logger;
import org.jboss.system.ServiceMBean;
import org.jboss.util.loading.ContextClassLoaderSwitcher;
import org.jgroups.Channel;
import org.jgroups.ChannelException;
import org.jgroups.ChannelListenerAdapter;
import org.jgroups.Event;
import org.jgroups.Global;
import org.jgroups.JChannel;
import org.jgroups.conf.ConfiguratorFactory;
import org.jgroups.conf.ProtocolData;
import org.jgroups.conf.ProtocolStackConfigurator;
import org.jgroups.jmx.JmxConfigurator;
import org.jgroups.protocols.TP;
import org.jgroups.stack.IpAddress;
import org.jgroups.stack.Protocol;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.DefaultThreadFactory;
import org.jgroups.util.LazyThreadFactory;
import org.jgroups.util.ThreadDecorator;
import org.jgroups.util.ThreadFactory;
import org.jgroups.util.ThreadManager;
import org.jgroups.util.Util;

/**
 * Extension to the JGroups JChannelFactory that supports a number of 
 * JBoss AS-specific behaviors:
 * <p>
 * <ul>
 * <li>Passing a config event to newly created channels containing 
 * "additional_data" that will be associated with the JGroups 
 * <code>IpAddress</code> for the peer. Used to provide logical addresses
 * to cluster peers that remain consistent across channel and server restarts.</li>
 * <li>Never returns instances of {@link org.jgroups.mux.MuxChannel} from
 * the <code>createMultiplexerChannel</code> methods.  Instead always returns
 * a channel with a shared transport protocol.</li>
 * <li>Configures the channel's thread pools and thread factories to ensure
 * that application thread context classloaders don't leak to the channel
 * threads.</li>
 * </ul>
 * </p>
 * 
 * @author <a href="mailto://brian.stansberry@jboss.com">Brian Stansberry</a>
 * @author <a href="mailto:galder.zamarreno@jboss.com">Galder Zamarreno</a>
 * 
 * @version $Revision: 86596 $
 */
public class JChannelFactory extends org.jgroups.JChannelFactory
      implements JChannelFactoryMBean, MBeanRegistration
{
   protected static final Logger log = Logger.getLogger(JChannelFactory.class);
   
   public static final String UNSHARED_TRANSPORT_NAME_BASE = "unnamed_";
   
   private static final int CREATED = ServiceMBean.CREATED;
   private static final int STARTING = ServiceMBean.STARTING;
   private static final int STARTED = ServiceMBean.STARTED;
   private static final int STOPPING = ServiceMBean.STOPPING;
   private static final int STOPPED = ServiceMBean.STOPPED;
   private static final int DESTROYED = ServiceMBean.DESTROYED;
   private static final int FAILED = ServiceMBean.FAILED;
   
   private InetAddress nodeAddress;
   private String nodeName;
   private int namingServicePort = -1;
   private int state = ServiceMBean.UNREGISTERED;
   private boolean assignLogicalAddresses = true;
   private boolean manageNewThreadClassLoader = true;
   private boolean manageReleasedThreadClassLoader = false;
   private boolean addMissingSingletonName = true;
   private boolean domainSet;
   private final ContextClassLoaderSwitcher classLoaderSwitcher;
   private final Set<String> registeredChannels = new HashSet<String>();

   public JChannelFactory()
   {
      this.classLoaderSwitcher = (ContextClassLoaderSwitcher) AccessController.doPrivileged(ContextClassLoaderSwitcher.INSTANTIATOR);
   }   

   /**
    * Always throws <code>ChannelException</code>; this method is not supported.
    */   
   @Override
   public Channel createChannel() throws ChannelException
   {
      throw new ChannelException("No-arg createChannel() is not supported");
   }

   @Override
   public Channel createChannel(Object properties) throws ChannelException
   {
      checkStarted();
      
      @SuppressWarnings("deprecation")      
      Channel channel = super.createChannel(properties);
      
      if (manageNewThreadClassLoader || manageReleasedThreadClassLoader)
      {
         fixChannelThreadManagement(channel);
      }
      
      if (assignLogicalAddresses)
      {
         setChannelUniqueId(channel);
      }
      
      // can't register in JMX as we don't have a channel name
      
      return channel;
   }

   /**
    * Create a {@link Channel} using the specified stack. Channel will use a 
    * shared transport if the <code>singleton-name</code> attribute is
    * set on the stack's transport protocol.
    * 
    * @param stack_name the name of the stack
    * @return the channel
    * 
    * @throws Exception
    */
   @Override
   public Channel createChannel(String stack_name) throws Exception
   {
      checkStarted();
      
      String props=stack_name != null? getConfig(stack_name) : null;
      if (props == null)
      {
         log.warn("No protocol stack found with name " + stack_name +
                  "; creating default channel");
         return createChannel();
      }
      
      JChannel channel = new JChannel(props);
      
      if (manageNewThreadClassLoader || manageReleasedThreadClassLoader)
      {
         fixChannelThreadManagement(channel);
      }
      
      if (assignLogicalAddresses)
      {
         setChannelUniqueId(channel);
      }
      
      // can't register in JMX as we don't have a channel name
      
      return channel;
   }
   
   /**
    * Creates and returns a shared transport Channel configured with the specified 
    * {@link #getConfig(String) protocol stack configuration}.
    * <p>
    * <emphasis>NOTE:</emphasis> The implementation of this method is somewhat
    * different from what is described in 
    * {@link org.jgroups.ChannelFactory#createMultiplexerChannel(String, String)}.
    * The returned channel will not be an instance of 
    * <code>org.jgroups.mux.MuxChannel</code>; rather a channel that uses a
    * shared transport will be returned.  This will be the case whether or
    * not the protocol stack specified by <code>stack_name</code> includes
    * a <code>singleton_name</code> attribute in its 
    * {@link org.jgroups.protocols.TP transport protocol} configuration. If no 
    * <code>singleton_name</code> attribute is present, this factory will create
    * a synthetic one by prepending "unnamed_" to the provided
    * <code>id</code> param and will use that for the returned channel's 
    * transport protocol. (Note this will not effect the protocol stack
    * configuration named by <code>stack_name</code>; i.e. another request
    * that passes the same <code>stack_name</code> will not inherit the
    * synthetic singleton name.) 
    * 
    * @param stack_name
    *            The name of the stack to be used. All stacks are defined in
    *            the configuration with which the factory is configured (see
    *            {@link #setMultiplexerConfig(Object)} for example. If
    *            clients attempt to create a Channel for an undefined stack 
    *            name an Exception will be thrown.
    * @param id  Only used if the transport protocol configuration for the
    *            specified stack does not include the <code>singleton_name</code>
    *            attribute; then it is used to create a synthetic singleton-name
    *            for the channel's protocol stack.
    *            
    * @return An implementation of Channel configured with a shared transport.
    *         
    * @throws IllegalStateException if the specified protocol stack does not
    *                               declare a <code>singleton_name</code> and
    *                               {@link #getAddMissingSingletonName()} returns
    *                               <code>false</code>.
    * @throws ChannelException
    */
   @Override
   public Channel createMultiplexerChannel(String stack_name, String id) throws Exception
   {
      checkStarted();
      
      String configStr = getConfig(stack_name);
      
      if (configStr == null)
         throw new IllegalStateException("Unknown stack_name " + stack_name);
      
      ProtocolStackConfigurator config = ConfiguratorFactory.getStackConfigurator(configStr);
      Map<String, String> tpProps = getTransportProperties(config);
      
      if (!tpProps.containsKey(Global.SINGLETON_NAME))
      {
         if (addMissingSingletonName)
         {
            String singletonName = UNSHARED_TRANSPORT_NAME_BASE + stack_name;
            
            log.warn("Config for " + stack_name + " does not include " +
                      "singleton_name; adding a name of " + singletonName +
                      ". You should configure a singleton_name for this stack.");
            
            config = addSingletonName(config, singletonName);
            log.debug("Stack config after adding singleton_name is " + config.getProtocolStackString());
            tpProps = getTransportProperties(config);                       
         }
         else
         {
            throw new IllegalStateException("Config for " + stack_name + " does not include " +
                      "singleton_name and MuxChannels are not supported.");
         }
      }
      
      JChannel channel = new JChannel(config);
      
      if (manageNewThreadClassLoader || manageReleasedThreadClassLoader)
      {
         fixChannelThreadManagement(channel);
      }
      
      if (assignLogicalAddresses)
      {
         setChannelUniqueId(channel);
      }
      
      if (isExposeChannels() && id != null && id.length() > 0)
      {
         registerChannel(channel, id);
      }
      
      return channel;
   }  

   
   /**
    * Creates and returns a shared transport Channel configured with the specified 
    * {@link #getConfig(String) protocol stack configuration}.
    * 
    * See {@link #createMultiplexerChannel(String, String)}; the additional
    * attributes specified in this overloaded version of that method are ignored.
    *
    * @param register_for_state_transfer ignored in JBoss AS. Treated as <code>false</code>.
    * 
    * @param substate_id ignored in JBoss AS
    *            
    * @return An implementation of Channel configured with a shared transport.
    *         
    *         
    * @throws IllegalStateException if the specified protocol stack does not
    *                               declare a <code>singleton_name</code> and
    *                               {@link #getAddMissingSingletonName()} returns
    *                               <code>false</code>.
    * @throws ChannelException
    */
   @Override
   public Channel createMultiplexerChannel(String stack_name, String id, boolean register_for_state_transfer, String substate_id) throws Exception
   {
      return createMultiplexerChannel(stack_name, id);
   }
   
   @Override
   public void setDomain(String domain)
   {
      super.setDomain(domain);
      domainSet = true;
   }

   /**
    * Get any logical name assigned to this server; if not null this value
    * will be the value of the 
    * {@link #setAssignLogicalAddresses(boolean) logical address} assigned
    * to the channels this factory creates.
    * 
    * @return the logical name for this server, or <code>null</code>.
    */
   public String getNodeName()
   {
      return nodeName;
   }

   /**
    * Sets the logical name assigned to this server; if not null this value
    * will be the value of the 
    * {@link #setAssignLogicalAddresses(boolean) logical address} assigned
    * to the channels this factory creates.
    * 
    * @param nodeName the logical name for this server, or <code>null</code>.
    */
   public void setNodeName(String nodeName)
   {
      this.nodeName = nodeName;
   }
   
   /**
    * Gets the address to which this server is bound; typically the value
    * passed to <code>-b</code> when JBoss is started. Used in combination 
    * with {@link #getNamingServicePort() the naming service port} to create
    * a logical name for this server if no {@link #SetNodeName(String) node name}
    * is specified.
    * 
    * @return the address to which this server is bound, or <code>null</code>
    *         if not set
    */
   public InetAddress getNodeAddress()
   {
      return nodeAddress;
   }
   
   /**
    * Sets the address to which this server is bound; typically the value
    * passed to <code>-b</code> when JBoss is started. Used in combination 
    * with {@link #getNamingServicePort() the naming service port} to create
    * a logical name for this server if no {@link #SetNodeName(String) node name}
    * is specified.
    * 
    * @param nodeAddress the address to which this server is bound, 
    *                    or <code>null</code>
    */
   public void setNodeAddress(InetAddress nodeAddress)
   {
      this.nodeAddress = nodeAddress;
   }

   /**
    * Gets the port on which this server's naming service is listening. Used in 
    * combination with {@link #getNodeAddress() the server bind address} to create
    * a logical name for this server if no {@link #SetNodeName(String) node name}
    * is specified.
    * 
    * @return the port on which JNDI is listening, or <code>-1</code> if not set.
    */
   public int getNamingServicePort()
   {
      return namingServicePort;
   }

   /**
    * Sets the port on which this server's naming service is listening. Used in 
    * combination with {@link #getNodeAddress() the server bind address} to create
    * a logical name for this server if no {@link #SetNodeName(String) node name}
    * is specified.
    * 
    * @param jndiPort the port on which JNDI is listening.
    */
   public void setNamingServicePort(int jndiPort)
   {
      this.namingServicePort = jndiPort;
   }
   
   /**
    * Gets whether this factory should create a "logical address" (or use
    * one set via {@link #setNodeName(String)} and assign it to
    * any newly created <code>Channel</code> as JGroups "additional_data".
    * 
    * @see #setAssignLogicalAddresses(boolean)
    */
   public boolean getAssignLogicalAddresses()
   {
      return assignLogicalAddresses;
   }

   /**
    * Sets whether this factory should create a "logical address" (or use
    * one set via {@link #setNodeName(String)} and assign it to
    * any newly created <code>Channel</code> as JGroups "additional_data".
    * <p>
    * Any such logical address will be used by <code>HAPartition</code>
    * to assign a name to the <code>ClusterNode</code> object representing 
    * this node. If a logical address is not set, the <code>ClusterNode</code> 
    * will use the address and port JGroups is using to receive messages to
    * create its name.
    * </p>
    * <p>
    * Default is <code>true</code>.
    * </p>
    */
   public void setAssignLogicalAddresses(boolean logicalAddresses)
   {
      this.assignLogicalAddresses = logicalAddresses;
   }

   /**
    * Gets whether this factory should update the standard JGroups
    * thread factories to ensure application classloaders do not leak to 
    * newly created channel threads.
    * 
    * @return <code>true</code> if the factories should be updated.
    *         Default is <code>true</code>.
    */
   public boolean getManageNewThreadClassLoader()
   {
      return manageNewThreadClassLoader;
   }

   /**
    * Sets whether this factory should update the standard JGroups
    * thread factories to ensure application classloaders do not leak to 
    * newly created channel threads. This should only be set to <code>false</code>
    * if a JGroups release is used that itself prevents such classloader leaks.
    * 
    * @param manage <code>true</code> if the factories should be updated.
    */
   public void setManageNewThreadClassLoader(boolean manage)
   {
      this.manageNewThreadClassLoader = manage;
   }

   /**
    * Gets whether this factory should update the standard JGroups
    * thread pools to ensure application classloaders have not leaked to 
    * threads returned to the pool.
    * 
    * @return <code>true</code> if the pools should be updated.
    *         Default is <code>false</code>.
    */
   public boolean getManageReleasedThreadClassLoader()
   {
      return manageReleasedThreadClassLoader;
   }

   /**
    * Sets whether this factory should update the standard JGroups
    * thread pools to ensure application classloaders have not leaked to 
    * threads returned to the pool.
    * <p>
    * There is a small performance cost to enabling this, and applications
    * can prevent any need to enable it by properly restoring the thread
    * context classloader if they change it.  Therefore, by default this
    * is set to <code>false</code>.
    * </p>
    * 
    * @param manage <code>true</code> if the factories should be updated.
    */
   public void setManageReleasedThreadClassLoader(boolean manage)
   {
      this.manageReleasedThreadClassLoader = manage;
   }

   /**
    * Gets whether {@link #createMultiplexerChannel(String, String)} should 
    * create a synthetic singleton name attribute for a channel's transport
    * protocol if one isn't configured.  If this is <code>false</code> and
    * no <code>singleton_name</code> is configured, 
    * {@link #createMultiplexerChannel(String, String)} will throw an
    * <code>IllegalStateException</code>. 
    * 
    * @return <code>true</code> if synthetic singleton names should be created.
    *         Default is <code>true</code>.
    */
   public boolean getAddMissingSingletonName()
   {
      return addMissingSingletonName;
   }

   /**
    * Sets whether {@link #createMultiplexerChannel(String, String)} should 
    * create a synthetic singleton name attribute for a channel's transport
    * protocol if one isn't configured.
    * 
    * @param addMissingSingletonName <code>true</code> if synthetic singleton 
    *                                names should be created.
    */
   public void setAddMissingSingletonName(boolean addMissingSingletonName)
   {
      this.addMissingSingletonName = addMissingSingletonName;
   }

   @Override
   public void create() throws Exception
   {

      if (state == CREATED || state == STARTING || state == STARTED
         || state == STOPPING || state == STOPPED)
      {
         log.debug("Ignoring create call; current state is " + getStateString());
         return;
      }
      
      log.debug("Creating JChannelFactory");
      
      try
      {
         super.create();
         state = CREATED;
      }
      catch (Exception e)
      {
         log.debug("Initialization failed JChannelFactory", e);
         throw e;
      }
      
      log.debug("Created JChannelFactory");
   }

   @Override
   public void start() throws Exception
   {
      if (state == STARTING || state == STARTED || state == STOPPING)
      {
         log.debug("Ignoring start call; current state is " + getStateString());
         return;
      }
      
      if (state != CREATED && state != STOPPED && state != FAILED)
      {
         log.debug("Start requested before create, calling create now");         
         create();
      }
      
      state = STARTING;
      log.debug("Starting JChannelFactory");

      try
      {
         super.start();
      }
      catch (Exception e)
      {
         state = FAILED;
         log.debug("Starting failed JChannelFactory", e);
         throw e;
      }

      state = STARTED;
      log.debug("Started JChannelFactory");
      
   }

   @Override
   public void stop()
   {
      if (state != STARTED)
      {
         log.debug("Ignoring stop call; current state is " + getStateString());
         return;
      }
      
      state = STOPPING;
      log.debug("Stopping JChannelFactory");

      try
      {
         super.stop();
      }
      catch (Throwable e)
      {
         state = FAILED;
         log.warn("Stopping failed JChannelFactory", e);
         return;
      }
      
      state = STOPPED;
      log.debug("Stopped JChannelFactory");
   }

   @Override
   public void destroy()
   {
      if (state == DESTROYED)
      {
         log.debug("Ignoring destroy call; current state is " + getStateString());
         return;
      }
      
      if (state == STARTED)
      {
         log.debug("Destroy requested before stop, calling stop now");
         stop();
      }
      
      log.debug("Destroying JChannelFactory");
      
      // DON'T call super.destroy() as that may deregister the JMX proxy
      // to this pojo service, leading to ugliness when the proxy is destroyed
      try
      {
         
         Set<String> toUnregister = null;
         synchronized (registeredChannels)
         {
            toUnregister = new HashSet<String>(registeredChannels);
         }
         
         for (String channelId : toUnregister)
         {
            unregister(channelId);
         }
      }
      catch (Throwable t)
      {
         log.warn("Destroying failed JChannelFactory", t);
      }
      state = DESTROYED;
      log.debug("Destroyed JChannelFactory");
   }

   // ------------------------------------------------------- MBeanRegistration

   public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception
   {
      setServer(server);
      if (!domainSet || getDomain() == null)
      {
         setDomain(name.getDomain());
      }
      return name;
   }


   public void postRegister(Boolean registrationDone)
   {
      if (registrationDone != null && registrationDone.booleanValue()
            && state == ServiceMBean.UNREGISTERED)
      {
         state = ServiceMBean.REGISTERED;
      }
   }


   public void preDeregister() throws Exception
   { 
   }

   public void postDeregister()
   { 
      setServer(null);
      if (state == ServiceMBean.DESTROYED)
         state = ServiceMBean.UNREGISTERED;
   }
   
   /**
    * Gets the classloader that channel threads should be set to if
    * {@link #getManageNewThreadClassloader()} or {@link #getManageReleasedThreadClassLoader()}
    * are <code>true</code>.
    * <p>
    * This implementation returns this class' classloader.
    * 
    * @return the classloader.
    */
   protected ClassLoader getDefaultChannelThreadContextClassLoader()
   {
      return getClass().getClassLoader();
   }

   // ----------------------------------------------------------------- Private


   private void checkStarted()
   {
      if (state != ServiceMBean.STARTED)
         throw new IllegalStateException("Cannot use factory; state is " + getStateString());
   }
   
   private void setChannelUniqueId(Channel channel)
   {
      IpAddress address = (IpAddress) channel.getLocalAddress();
      if (address == null)
      {
         // We push the independent name in the protocol stack before connecting to the cluster
         if (this.nodeName == null || "".equals(this.nodeName)) {
            this.nodeName = generateUniqueNodeName();
         }
         
         log.debug("Passing unique node id " + nodeName + " to the channel as additional data");
         
         HashMap<String, byte[]> staticNodeName = new HashMap<String, byte[]>();
         staticNodeName.put("additional_data", this.nodeName.getBytes());
         channel.down(new Event(Event.CONFIG, staticNodeName));
         
      }
      else if (address.getAdditionalData() == null)
      {
         if (channel.isConnected())
         {
            throw new IllegalStateException("Underlying JChannel was " +
                    "connected before additional_data was set");
         }
      }
      else if (this.nodeName == null || "".equals(this.nodeName))
      {         
         this.nodeName = new String(address.getAdditionalData());
         log.warn("Field nodeName was not set but mux channel already had " +
                "additional data -- setting nodeName to " + nodeName);
      }
   }
   
   private String getStateString()
   {
      return ServiceMBean.states[state];
   }

   private String generateUniqueNodeName ()
   {
      // we first try to find a simple meaningful name:
      // 1st) "local-IP:JNDI_PORT" if JNDI is running on this machine
      // 2nd) "local-IP:JMV_GUID" otherwise
      // 3rd) return a fully GUID-based representation
      //

      // Before anything we determine the local host IP (and NOT name as this could be
      // resolved differently by other nodes...)

      // But use the specified node address for multi-homing

      String hostIP = null;
      InetAddress address = ServerConfigUtil.fixRemoteAddress(nodeAddress);
      if (address == null)
      {
         log.debug ("unable to create a GUID for this cluster, check network configuration is correctly setup (getLocalHost has returned an exception)");
         log.debug ("using a full GUID strategy");
         return new VMID().toString();
      }
      else
      {
         hostIP = address.getHostAddress();
      }

      // 1st: is JNDI up and running?
      //
      if (namingServicePort > 0)
      {
         // we can proceed with the JNDI trick!
         return hostIP + ":" + namingServicePort;
      }
      else
      {
         log.warn("JNDI has been found but the service wasn't started. Most likely, " +
               "HAPartition bean is missing dependency on JBoss Naming. " +
               "Instead using host based UID strategy for defining a node " +
               "GUID for the cluster.");
      }

      // 2nd: host-GUID strategy
      //
      String uid = new UID().toString();
      return hostIP + ":" + uid;
   }
   
   private Map<String, String> getTransportProperties(ProtocolStackConfigurator config)
   {
      Map<String, String> tpProps = null;
      try
      {
         ProtocolData[] protocols=config.getProtocolStack();
         ProtocolData transport=protocols[0];
         tpProps = transport.getParameters();
      }
      catch (UnsupportedOperationException uoe)
      {
         // JGroups version hasn't implemented ProtocolStackConfigurator.getProtocolStack()
         // So we do it ourselves
         String configStr = config.getProtocolStackString();
         String tpConfigStr = configStr.substring(configStr.indexOf('(') + 1, configStr.indexOf(')'));
         String[] params = tpConfigStr.split(";");
         tpProps = new HashMap<String, String>();
         for (String param : params)
         {
            String[] keyVal = param.split("=");
            if (keyVal.length != 2)
               throw new IllegalStateException("Invalid parameter " + param + " in stack " + configStr);
            tpProps.put(keyVal[0], keyVal[1]);
         }
      }
      
      return tpProps;
   }
  
   private ProtocolStackConfigurator addSingletonName(ProtocolStackConfigurator orig, String singletonName)
      throws ChannelException
   {
      ProtocolStackConfigurator result = null;
      try
      {
         ProtocolData[] protocols=orig.getProtocolStack();
         ProtocolData transport=protocols[0];
         Map<String, String> tpProps = transport.getParameters();
         tpProps.put(Global.SINGLETON_NAME, singletonName);
         // we've now updated the state of orig; just return it
         result = orig;
      }
      catch (UnsupportedOperationException uoe)
      {
         // JGroups version hasn't implemented ProtocolStackConfigurator.getProtocolStack()
         // So we do things manually via string manipulation         
         String config = orig.getProtocolStackString();
         int idx = config.indexOf('(') + 1;
         StringBuilder builder = new StringBuilder(config.substring(0, idx));
         builder.append(Global.SINGLETON_NAME);
         builder.append('=');
         builder.append(singletonName);
         builder.append(';');
         builder.append(config.substring(idx));
         
         result = ConfiguratorFactory.getStackConfigurator(builder.toString());
      }
      
      return result;
   }
   
   private void fixChannelThreadManagement(Channel channel) throws ChannelException
   {
      if (!(channel instanceof JChannel))
      {
         log.debug("Cannot fix thread pools for unknown Channel type " + channel.getClass().getName());
         return;
      }
      
      JChannel jchannel = (JChannel) channel;
      
      ProtocolStack stack = jchannel.getProtocolStack();
      List<Protocol> protocols = stack.getProtocols();
      TP tp = null;
      for (int i = protocols.size() - 1; i >= 0; i--)
      {
         if (protocols.get(i) instanceof TP)
         {
            tp = (TP) protocols.get(i);
            break;
         }
      }
      
      ClassLoader defaultTCCL = getDefaultChannelThreadContextClassLoader();
      ThreadDecoratorImpl threadDecorator = new ThreadDecoratorImpl(defaultTCCL);
      if (manageNewThreadClassLoader)
      {
         fixProtocolThreadFactories(tp, threadDecorator);
      }
      
      if (manageReleasedThreadClassLoader)
      {
         fixTransportThreadPools(tp, threadDecorator);
      }
   }

   private void fixProtocolThreadFactories(TP tp, ThreadDecoratorImpl threadDecorator)
   {
      ThreadFactory stackFactory = tp.getThreadFactory();
      if (stackFactory == null)
      {
         stackFactory = new DefaultThreadFactory(Util.getGlobalThreadGroup(), "", false);
         tp.setThreadFactory(stackFactory);
      }
      fixThreadManager(stackFactory, threadDecorator, "TP.getThreadFactory()");
      
      log.debug("Fixed thread factory for " + tp);
      
      ThreadFactory timerFactory = tp.getTimerThreadFactory();
      if (timerFactory == null)
      {
         timerFactory = new LazyThreadFactory(Util.getGlobalThreadGroup(), "Timer", true, true);
         tp.setTimerThreadFactory(timerFactory);            
      }
      fixThreadManager(timerFactory, threadDecorator, "TP.getTimerThreadFactory()");
      
      log.debug("Fixed timer thread factory for " + tp);
      
      ThreadGroup pool_thread_group = null;
      if (tp.isDefaulThreadPoolEnabled())
      {
         ThreadFactory defaultPoolFactory = tp.getDefaultThreadPoolThreadFactory();
         if (defaultPoolFactory == null)
         {
            pool_thread_group=new ThreadGroup(Util.getGlobalThreadGroup(), "Thread Pools");
            defaultPoolFactory = new DefaultThreadFactory(pool_thread_group, "Incoming", false, true);
            tp.setThreadFactory(defaultPoolFactory);
         }
         fixThreadManager(defaultPoolFactory, threadDecorator, "TP.getDefaultThreadPoolThreadFactory()");
         
         log.debug("Fixed default pool thread factory for " + tp);
      }
      
      if (tp.isOOBThreadPoolEnabled())
      {
         ThreadFactory oobPoolFactory = tp.getOOBThreadPoolThreadFactory();
         if (oobPoolFactory == null)
         {
            if (pool_thread_group == null)
               pool_thread_group=new ThreadGroup(Util.getGlobalThreadGroup(), "Thread Pools");
            oobPoolFactory = new DefaultThreadFactory(pool_thread_group, "OOB", false, true);
            tp.setThreadFactory(oobPoolFactory);
         }
         fixThreadManager(oobPoolFactory, threadDecorator, "TP.getOOBThreadPoolThreadFactory()");
         
         log.debug("Fixed oob pool thread factory for " + tp);
      }
      
      Map<ThreadFactory, Protocol> factories= new HashMap<ThreadFactory, Protocol>();
      Protocol tmp=tp.getUpProtocol();
      while(tmp != null) {
        ThreadFactory f=tmp.getThreadFactory();
         if(f != null && !factories.containsKey(f))
         {
            factories.put(f, tmp);
         }
         tmp=tmp.getUpProtocol();
      }
      
      for (Map.Entry<ThreadFactory, Protocol> entry : factories.entrySet())
      {
         fixThreadManager(entry.getKey(), threadDecorator, entry.getValue().getClass().getSimpleName() + ".getThreadFactory()");
      }
      
      log.debug("Fixed Protocol thread factories");
   }

   private void fixTransportThreadPools(TP tp, ThreadDecoratorImpl threadDecorator)
   {
      Executor threadPool = tp.getDefaultThreadPool();
      if (tp.isDefaulThreadPoolEnabled())
      {
         fixThreadManager(threadPool, threadDecorator, "TP.getDefaultThreadPool()");
         
         log.debug("Fixed default thread pool for " + tp);
      }
      
      threadPool = tp.getOOBThreadPool();
      if (tp.isOOBThreadPoolEnabled())
      {
         fixThreadManager(threadPool, threadDecorator, "TP.getOOBThreadPool()"); 
         
         log.debug("Fixed OOB thread pool for " + tp);
      }
   }
   
   private void fixThreadManager(Object manager, ThreadDecoratorImpl decorator, String managerSource)
   {
      if (manager instanceof ThreadManager)
      {
         ThreadManager threadManager = (ThreadManager) manager;
         
         ThreadDecorator existing = threadManager.getThreadDecorator();
         if (existing instanceof ThreadDecoratorImpl)
         {
            // already been handled
            return;
         }
         else if (existing != null)
         {
            // someone else has added one; integrate with it
            decorator.setParent(existing);
         }
         threadManager.setThreadDecorator(decorator);
      }
      else
      {
         log.warn(managerSource + " is not a ThreadManager");
      }
   }
   
   /** 
    * Sets the context class loader on <code>thread</code> to the classloader
    * in effect when this factory was instantiated.
    * 
    * @param thread the thread to set
    */
   private void setDefaultThreadContextClassLoader(Thread thread, ClassLoader classLoader)
   {
      classLoaderSwitcher.setContextClassLoader(thread, classLoader);
   }
   
   private void registerChannel(JChannel ch, String channelId) throws Exception 
   {
      if(getServer() != null)
      {
         // Register for channel closed notification so we can unregister
         JmxDeregistrationChannelListener listener = new JmxDeregistrationChannelListener(channelId);
         ch.addChannelListener(listener);
         JmxConfigurator.registerChannel(ch, getServer(), getDomain(), channelId, isExposeProtocols());
         synchronized (registeredChannels)
         {
            registeredChannels.add(channelId);
         }
      }
   }
   
   private void unregister(String channelId) 
   {
      if(getServer() != null && registeredChannels.contains(channelId))
      {
         String oname = getDomain() + ":type=channel,cluster=" + channelId;
         try
         {
            getServer().unregisterMBean(new ObjectName(oname));
            oname = getDomain() + ":type=protocol,cluster=" + channelId + ",*";
            JmxConfigurator.unregister(getServer(), oname);
            synchronized (registeredChannels)
            {
               registeredChannels.remove(channelId);
            }
         }
         catch(Exception e)
         {
            log.error("failed unregistering " + oname, e);
         }
      }
   }
   
   private class ThreadDecoratorImpl implements ThreadDecorator
   {
      private final ClassLoader classloader;
      private ThreadDecorator parent;
      
      private ThreadDecoratorImpl(ClassLoader classloader)
      {
         this.classloader = classloader;
      }

      public void threadCreated(Thread thread)
      {
         if (parent != null)
            parent.threadCreated(thread);
         setDefaultThreadContextClassLoader(thread, classloader);
      }

      public void threadReleased(Thread thread)
      {
         if (parent != null)
            parent.threadCreated(thread);
         setDefaultThreadContextClassLoader(thread, classloader);
      }

      public ThreadDecorator getParent()
      {
         return parent;
      }

      public void setParent(ThreadDecorator parent)
      {
         this.parent = parent;
      }
      
   }

   private class JmxDeregistrationChannelListener extends ChannelListenerAdapter
   {
      private final String channelId;
      
      JmxDeregistrationChannelListener(String channelId)
      {
         this.channelId = channelId;
      }
      
      public void channelClosed(Channel channel) 
      {
         unregister(channelId);
      }            
   }
   
}
