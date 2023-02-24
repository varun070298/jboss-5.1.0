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

import org.jboss.ha.framework.interfaces.HAPartition;
import org.jboss.ha.framework.interfaces.ClusterNode;
import org.jboss.system.server.ServerConfigLocator;
import org.jboss.logging.Logger;
import org.jboss.ha.framework.interfaces.HAPartition.AsynchHAMembershipListener;

import java.util.*;
import java.io.*;

/**
 * Handles transfering files on the cluster.  Files are sent in small chunks at a time (up to MAX_CHUNK_BUFFER_SIZE bytes per
 * Cluster call).
 *
 * @deprecated No longer used since 5.0.0; will be removed in 6.0.0
 * 
 * @author <a href="mailto:smarlow@novell.com">Scott Marlow</a>.
 * @version $Revision: 86549 $
 */
public class ClusterFileTransfer implements AsynchHAMembershipListener {

   // Specify max file transfer buffer size that we read and write at a time.
   // This influences the number of times that we will invoke disk read/write file
   // operations versus how much memory we will consume for a file transfer.
   private static final int MAX_CHUNK_BUFFER_SIZE = 512 * 1024;

   // collection of in-progress file push operations
   private Map mPushsInProcess = Collections.synchronizedMap(new HashMap());

   // collection of in-progress file pull operations
   private Map mPullsInProcess = Collections.synchronizedMap(new HashMap());

   private HAPartition mPartition;

   private static final File TEMP_DIRECTORY = ServerConfigLocator.locate().getServerTempDir();

   // Mapping between parent folder name and target destination folder
   // the search key is the parent folder name and value is the java.io.File.
   // We don't synchronize on the mParentFolders as we assume its safe to read it.
   private Map mParentFolders = null;

   private static final String SERVICE_NAME = ClusterFileTransfer.class.getName() + "Service";

   private static final Logger log = Logger.getLogger(ClusterFileTransfer.class.getName());

   /**
    * Constructor needs the cluster partition and the mapping of server folder names to the java.io.File instance
    * representing the physical folder.
    *
    * @param partition represents the cluster.
    * @param destinationDirectoryMap is the mapping between server folder name and physical folder representation.
    */
   public ClusterFileTransfer(HAPartition partition, Map destinationDirectoryMap)
   {
      this.mPartition = partition;
      this.mPartition.registerRPCHandler(SERVICE_NAME, this);
      this.mPartition.registerMembershipListener(this);
      mParentFolders = destinationDirectoryMap;
   }

   /**
    * Get specified file from the cluster.
    *
    * @param file identifies the file to get from the cluster.
    * @param parentName is the parent folder name for the file on both source and destination nodes.
    * @throws ClusterFileTransferException
    */
   public void pull(File file, String parentName) throws ClusterFileTransferException
   {
      String myNodeName = this.mPartition.getNodeName();
      ClusterNode myNodeAddress = this.mPartition.getClusterNode();
      FileOutputStream output = null;
      try
      {
         log.info("Start pull of file " + file.getName() + " from cluster.");
         ArrayList response = mPartition.callMethodOnCoordinatorNode(SERVICE_NAME,
            "remotePullOpenFile",
            new Object[]{file, myNodeName, myNodeAddress,  parentName}, new Class[]{java.io.File.class, java.lang.String.class,ClusterNode.class, java.lang.String.class},
            true);

         if (response == null || response.size() < 1)
         {
            throw new ClusterFileTransferException("Did not receive response from remote machine trying to open file '" + file + "'.  Check remote machine error log.");
         }

         FileContentChunk fileChunk = (FileContentChunk) response.get(0);
         if(null == fileChunk)
         {
            throw new ClusterFileTransferException("An error occured on remote machine trying to open file '" + file + "'.  Check remote machine error log.");
         }

         File tempFile = new File(ClusterFileTransfer.getServerTempDir(), file.getName());
         output = new FileOutputStream(tempFile);

         // get the remote file modification time and change our local copy to have the same time.
         long lastModification = fileChunk.lastModified();
         while (fileChunk.mByteCount > 0)
         {
            output.write(fileChunk.mChunk, 0, fileChunk.mByteCount);
            response = mPartition.callMethodOnCoordinatorNode(SERVICE_NAME,
               "remotePullReadFile",
               new Object[]{file, myNodeName}, new Class[]{java.io.File.class, java.lang.String.class},
               true);
            if (response.size() < 1)
            {
               if(! tempFile.delete())
                  throw new ClusterFileTransferException("An error occured on remote machine trying to read file '" + file + "'.  Is remote still running?  Also, we couldn't delete temp file "+ tempFile.getName());
               throw new ClusterFileTransferException("An error occured on remote machine trying to read file '" + file + "'.  Is remote still running?");
            }
            fileChunk = (FileContentChunk) response.get(0);
            if (null == fileChunk)
            {
               if( !tempFile.delete())
                  throw new ClusterFileTransferException("An error occured on remote machine trying to read file '" + file + "'.  Check remote machine error log.  Also, we couldn't delete temp file "+ tempFile.getName());
               throw new ClusterFileTransferException("An error occured on remote machine trying to read file '" + file + "'.  Check remote machine error log.");
            }
         }
         output.close();
         output = null;
         File target = new File(getParentFile(parentName), file.getName());
         if (target.exists()) {
            if(!target.delete())
               throw new ClusterFileTransferException("The destination file "+ target + " couldn't be deleted, the updated application will not be copied to this node");

         }
         tempFile.setLastModified(lastModification);
         if (!localMove(tempFile,target))
         {
            throw new ClusterFileTransferException("Could not move " + tempFile + " to " + target);
         }
         log.info("Finished cluster pull of file " + file.getName() + " to "+ target.getName());
      }
      catch(IOException e)
      {
         throw new ClusterFileTransferException(e);
      }
      catch(ClusterFileTransferException e)
      {
         throw e;
      }
      catch(Exception e)
      {
         throw new ClusterFileTransferException(e);
      }
      finally {
         if( output != null) {
            try {
               output.close();
            }
            catch(IOException e) {logException(e);}  // we are already in the middle of a throw if output isn't null.
         }
      }
   }

   /**
    * This is remotely called by {@link #pull(File , String )} to open the file on the machine that
    * the file is being copied from.
    *
    * @param file is the file to pull.
    * @param originNodeName is the cluster node that is requesting the file.
    * @param parentName     is the parent folder name for the file on both source and destination nodes.
    * @return FileContentChunk containing the first part of the file read after opening it.
    */
   public FileContentChunk remotePullOpenFile(File file, String originNodeName, ClusterNode originNode, String parentName)
   {
      try
      {
         File target = new File(getParentFile(parentName), file.getName());
         FileContentChunk fileChunk = new FileContentChunk(target, originNodeName,originNode);
         FilePullOperation filePullOperation = new FilePullOperation(fileChunk);
         // save the operation for the next call to remoteReadFile
         this.mPullsInProcess.put(CompositeKey(originNodeName, file.getName()), filePullOperation);
         filePullOperation.openInputFile();
         fileChunk.readNext(filePullOperation.getInputStream());
         return fileChunk;
      } catch (IOException e)
      {
         logException(e);
      } catch (Exception e)
      {
         logException(e);
      }
      return null;
   }

   /**
    * This is remotely called by {@link #pull(File, String )} to read the file on the machine that the file is being
    * copied from.
    *
    * @param file is the file to pull.
    * @param originNodeName is the cluster node that is requesting the file.
    * @return FileContentChunk containing the next part of the file read.
    */
   public FileContentChunk remotePullReadFile(File file, String originNodeName)
   {
      try
      {
         FilePullOperation filePullOperation = (FilePullOperation) this.mPullsInProcess.get(CompositeKey(originNodeName, file.getName()));
         filePullOperation.getFileChunk().readNext(filePullOperation.getInputStream());
         if (filePullOperation.getFileChunk().mByteCount < 1)
         {
            // last call to read, so clean up
            filePullOperation.getInputStream().close();
            this.mPullsInProcess.remove(CompositeKey(originNodeName, file.getName()));
         }
         return filePullOperation.getFileChunk();
      } catch (IOException e)
      {
         logException(e);
      }
      return null;
   }

   /**
    * Send specified file to cluster.
    *
    * @param file is the file to send.
    * @param leaveInTempFolder is true if the file should be left in the server temp folder.
    * @throws ClusterFileTransferException
    */
   public void push(File file, String parentName, boolean leaveInTempFolder) throws ClusterFileTransferException
   {
      File target = new File(getParentFile(parentName), file.getName());

      log.info("Start push of file " + file.getName() + " to cluster.");
      // check if trying to send explored archive (cannot send subdirectories)
      if (target.isDirectory())
      {
         // let the user know why we are skipping this file and return.
         logMessage("You cannot send the contents of directories, consider archiving folder containing" + target.getName() + " instead.");
         return;
      }
      ClusterNode myNodeAddress = this.mPartition.getClusterNode();
      FileContentChunk fileChunk = new FileContentChunk(target, this.mPartition.getNodeName(), myNodeAddress);
      try
      {
         InputStream input = fileChunk.openInputFile();
         while (fileChunk.readNext(input) >= 0)
         {
            mPartition.callMethodOnCluster(SERVICE_NAME, "remotePushWriteFile", new Object[]{fileChunk, parentName}, new Class[]{fileChunk.getClass(), java.lang.String.class}, true);
         }
         // tell remote(s) to close the output file
         mPartition.callMethodOnCluster(SERVICE_NAME, "remotePushCloseFile", new Object[]{fileChunk, new Boolean(leaveInTempFolder), parentName}, new Class[]{fileChunk.getClass(), Boolean.class, java.lang.String.class}, true);
         input.close();
         log.info("Finished push of file " + file.getName() + " to cluster.");
      }
      catch(FileNotFoundException e)
      {
         throw new ClusterFileTransferException(e);
      }
      catch(IOException e)
      {
         throw new ClusterFileTransferException(e);
      }
      catch(Exception e)
      {
         throw new ClusterFileTransferException(e);
      }
   }


   /**
    * Remote method for writing file a fragment at a time.
    *
    * @param fileChunk
    */
   public void remotePushWriteFile(FileContentChunk fileChunk, String parentName)
   {
      try
      {
         String key = CompositeKey(fileChunk.getOriginatingNodeName(), fileChunk.getDestinationFile().getName());
         FilePushOperation filePushOperation = (FilePushOperation) mPushsInProcess.get(key);

         // handle first call to write
         if (filePushOperation == null)
         {
            if (fileChunk.mChunkNumber != FileContentChunk.FIRST_CHUNK)
            {
               // we joined the cluster after the file transfer started
               logMessage("Ignoring file transfer of '" + fileChunk.getDestinationFile().getName() + "' from " + fileChunk.getOriginatingNodeName() + ", we missed the start of it.");
               return;
            }
            filePushOperation = new FilePushOperation(fileChunk.getOriginatingNodeName(), fileChunk.getOriginatingNode());
            File tempFile = new File(ClusterFileTransfer.getServerTempDir(), fileChunk.getDestinationFile().getName());
            filePushOperation.openOutputFile(tempFile);
            mPushsInProcess.put(key, filePushOperation);
         }
         filePushOperation.getOutputStream().write(fileChunk.mChunk, 0, fileChunk.mByteCount);
      } catch (FileNotFoundException e)
      {
         logException(e);
      } catch (IOException e)
      {
         logException(e);
      }
   }

   /**
    * Remote method for closing the file just transmitted.
    *
    * @param fileChunk
    * @param leaveInTempFolder is true if we should leave the file in the server temp folder
    */
   public void remotePushCloseFile(FileContentChunk fileChunk, Boolean leaveInTempFolder, String parentName)
   {
      try
      {
         FilePushOperation filePushOperation = (FilePushOperation) mPushsInProcess.remove(CompositeKey(fileChunk.getOriginatingNodeName(), fileChunk.getDestinationFile().getName()));

         if ((filePushOperation != null) && (filePushOperation.getOutputStream() != null))
         {
            filePushOperation.getOutputStream().close();
            if (!leaveInTempFolder.booleanValue())
            {
               File tempFile = new File(ClusterFileTransfer.getServerTempDir(), fileChunk.getDestinationFile().getName());
               File target = new File(getParentFile(parentName), fileChunk.getDestinationFile().getName());
               if (target.exists())
                  if(!target.delete())
                     logMessage("Could not delete target file " + target);

               tempFile.setLastModified(fileChunk.lastModified());
               if (!localMove(tempFile,target))
               {
                  logMessage("Could not move " + tempFile + " to " + target);
               }
            }
         }
      } catch (IOException e)
      {
         logException(e);
      }
   }

   /** Called when a new partition topology occurs. see HAPartition.AsynchHAMembershipListener
    *
    * @param deadMembers A list of nodes that have died since the previous view
    * @param newMembers A list of nodes that have joined the partition since the previous view
    * @param allMembers A list of nodes that built the current view
    */
   public void membershipChanged(Vector deadMembers, Vector newMembers, Vector allMembers)
   {
      // Are there any deadMembers contained in mPushsInProcess or in mPullsInProcess.
      // If so, cancel operations for them.
      // If contained in mPushsInProcess, then we can stop waiting for the rest of the file transfer.
      // If contained in mPullsInProcess, then we can stop supplying for the rest of the file transfer.

      if (mPushsInProcess.size() > 0)
      {
         synchronized(mPushsInProcess)
         {
            Collection values = mPushsInProcess.values();
            Iterator iter = values.iterator();
            while (iter.hasNext())
            {
               FilePushOperation push = (FilePushOperation)iter.next();
               if (deadMembers.contains(push.getOriginatingNode()))
               {
                  // cancel the operation and remove the operation from mPushsInProcess
                  push.cancel();
                  iter.remove();
               }
            }
         }
      }

      if (mPullsInProcess.size() > 0)
      {
         synchronized(mPullsInProcess)
         {
            Collection values = mPullsInProcess.values();
            Iterator iter = values.iterator();
            while(iter.hasNext())
            {
               FilePullOperation pull = (FilePullOperation)iter.next();
               if (deadMembers.contains(pull.getFileChunk().getOriginatingNode()))
               {
                  // cancel the operation and remove the operation from mPullsInProcess
                  pull.cancel();
                  iter.remove();
               }
            }
         }
      }
   }

   private static File getServerTempDir()
   {
      return TEMP_DIRECTORY;
   }

   private File getParentFile(String parentName)
   {
      return (File) mParentFolders.get(parentName);
   }

   private String CompositeKey(String originNodeName, String fileName)
   {
      return originNodeName + "#" + fileName;
   }

   private static void logMessage(String message)
   {
      log.info(message);
   }

   private static void logException(Throwable e)
   {
      //e.printStackTrace();
      log.error(e);
   }


   /**
    * Represents file push operation.
    */
   private static class FilePushOperation {


      public FilePushOperation(String originNodeName, ClusterNode originNode)
      {
         mOriginNodeName =originNodeName;
         mOriginNode = originNode;
      }

      public void openOutputFile(File file) throws FileNotFoundException
      {
         mOutput = new FileOutputStream(file);
         mOutputFile = file;
      }

      /**
       * Cancel the file push operation.  To be called locally on each machine that is
       * receiving the file.
       */
      public void cancel()
      {
         ClusterFileTransfer.logMessage("Canceling receive of file " + mOutputFile + " as remote server "+mOriginNodeName+" left the cluster.  Partial results will be deleted.");
         try
         {
            // close the output stream and delete the file.
            mOutput.close();
            if(!mOutputFile.delete())
               logMessage("Could not delete output file " + mOutputFile);
         }
         catch(IOException e) { logException(e); }
      }

      /**
       * Get the IPAddress of the cluster node that is pushing file to the cluster.
       * @return IPAddress
       */
      public ClusterNode getOriginatingNode()
      {
         return mOriginNode;
      }

      public OutputStream getOutputStream()
      {
         return mOutput;
      }

      private OutputStream mOutput;
      private String mOriginNodeName;
      private ClusterNode mOriginNode;
      private File mOutputFile;
   }

   /**
    * Represents file pull operation.
    */
   private static class FilePullOperation {
      public FilePullOperation(FileContentChunk fileChunk)
      {
         mFileChunk = fileChunk;
      }

      public void openInputFile() throws FileNotFoundException
      {
         mInput = mFileChunk.openInputFile();
      }

      public InputStream getInputStream()
      {
         return mInput;
      }

      /**
       * Cancel the file pull operation.  To be called locally on the machine that is supplying the file.
       */
      public void cancel()
      {
         logMessage("Canceling send of file " + mFileChunk.getDestinationFile() + " as remote server "+mFileChunk.getOriginatingNodeName()+" left the cluster.");
         try
         {
            mInput.close();
         }
         catch(IOException e) { logException(e); }
      }

      public FileContentChunk getFileChunk()
      {
         return mFileChunk;
      }

      private FileContentChunk mFileChunk;
      private InputStream mInput;
   }

   /**
    * For representing filetransfer state on the wire.
    * The inputStream or OutputStream is expected to be maintained by the sender/receiver.
    */
   private static class FileContentChunk implements Serializable {

      public FileContentChunk(File file, String originNodeName, ClusterNode originNode)
      {
         this.mDestinationFile = file;
         this.mLastModified = file.lastModified();
         this.mOriginNode = originNode;
         this.mOriginNodeName = originNodeName;
         mChunkNumber = 0;
         long size = file.length();
         if (size > MAX_CHUNK_BUFFER_SIZE)
            size = MAX_CHUNK_BUFFER_SIZE;
         else if (size <= 0)
            size = 1;
         mChunk = new byte[(int) size];     // set amount transferred at a time
         mByteCount = 0;
      }

      /**
       * Get the name of the cluster node that started the file transfer operation
       *
       * @return node name
       */
      public String getOriginatingNodeName()
      {
         return this.mOriginNodeName;
      }

      /**
       * Get the address of the cluster node that started the file transfer operation.
       * @return ClusterNode
       */
      public ClusterNode getOriginatingNode()
      {
         return mOriginNode;
      }

      public File getDestinationFile()
      {
         return this.mDestinationFile;
      }

      /**
       * Open input file
       *
       * @throws FileNotFoundException
       */
      public InputStream openInputFile() throws FileNotFoundException
      {
         return new FileInputStream(this.mDestinationFile);
      }

      /**
       * Open output file
       *
       * @return
       * @throws FileNotFoundException
       */
      public OutputStream openOutputFile() throws FileNotFoundException
      {
         File lFile = new File(ClusterFileTransfer.getServerTempDir(), this.mDestinationFile.getName());
         FileOutputStream output = new FileOutputStream(lFile);
         return output;
      }

      /**
       * @return number of bytes read
       * @throws IOException
       */
      public int readNext(InputStream input) throws IOException
      {
         this.mChunkNumber++;
         this.mByteCount = input.read(this.mChunk);
         return this.mByteCount;
      }

      public long lastModified()
      {
         return mLastModified;
      }

      static final long serialVersionUID = 3546447481674749363L;
      private File mDestinationFile;
      private long mLastModified;
      private String mOriginNodeName;
      private ClusterNode mOriginNode;
      private int mChunkNumber;
      private static final int FIRST_CHUNK = 1;
      private byte[] mChunk;
      private int mByteCount;
   }

   public static boolean localMove(File source, File destination) throws FileNotFoundException, IOException {
      if(source.renameTo(destination))  // if we can simply rename the file
         return true;                   // return success
      // otherwise, copy source to destination
      OutputStream out = new FileOutputStream(destination);
      InputStream in = new FileInputStream(source);
      byte buffer[] = new byte[32*1024];
      int bytesRead = 0;
      while(bytesRead > -1) {  // until we hit end of source file
         bytesRead = in.read(buffer);
         if(bytesRead > 0) {
            out.write(buffer,0, bytesRead);
         }
      }
      in.close();
      out.close();
      if(!source.delete())
         logMessage("Could not delete file "+ source);
      return true;
   }
}
