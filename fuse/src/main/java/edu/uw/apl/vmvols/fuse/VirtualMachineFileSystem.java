package edu.uw.apl.vmvols.fuse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.BufferOverflowException;

import fuse.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;
import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;

/**
 * @author Stuart Maclean

   An implementation of fuse.FuseFS (via the higher level
   fuse.Filesystem3) where the 'filesystem' is simply a list of
   VirtualDisks (currently we have implemented virtualbox.vdi and
   vmware.vmdk formats) under a single mount-point directory.  So a
   directory listing presents a list of what looks like device files
   (like /dev/sda is a device file in Linux representing your whole
   hard drive).
   
   All we really care about is getattr,getdir,open,read.  We have stub/noops
   for the other parts of the fuse api.

   To actually make such an fs visible at the host OS-level, we do:

   VirtualMachineFS fs = new VirtualMachineFS();

   fs.add( vmDir1 );
   fs.add( vmDir2 );

   File mountPoint = new File( "mount" );
   mountPoint.mkdirs();

   boolean ownThread = true|false;

   fs.mount( mountPoint, ownThread );

   We can later either unmount the mount point externally, e.g.

   $ fusermount -u mount

   or from within code (assuming ownThread set to true)

   fs.umount()

   The umount() method call simply invokes fusermount anyway.

   By making virtual disks (e.g. VirtualBox .vdi) visible to fuse, we
   make the underlying .dd bit streams inside the virtual disks
   available at the file system level, and thus visible to
   e.g. Sleuthkit.

   Further, we have added the capability to see snapshot disks too.
   The paths under the file system root are as follows.  The list of
   vms added to the VMFS become the first level directories under the
   mount point, e.g.

   /vm1name
   /vm2name

   Under each vm directory are listed all the disks for that vm, named
   sda for the first disk found, sdb for the second disk, etc:
   
   /vm1name/sda 
   /vm1name/sdb 
   /vm2name/sda

   These names correspond to the 'active' disk contents, i.e.  the one
   that would be read/written were the vm to be powered on.
   
   If snapshots are enabled (a simple boolean), then every generation
   of every disk appears too.  Generation 0 is the disk from
   creation up to any first snapshot, generation 1 is up to any second
   snapshot, etc.  The highest generation corresponds to the 'active'
   disk,

   /vm1name/sda 
   /vm1name/sda1 
   /vm1name/sda2
   /vm1name/sdb 
   /vm1name/sdb1

   In the example above, vm1name/sda and vm1name/sda2 have same
   content (since disk sda2 is the active disk, the one the VM would
   write to if it were powered on).  sda1 would contain the disk
   content frozen by the snapshot.  Similarly for vm1name/sdb and
   vm2name/sdb1.  Disk sdb would have been added AFTER the sole
   snapshot was taken, explaining why the generation sequencing of
   disk a is distinct from that of disk b. Disk a has two distinct
   contents, disk b has just one.

   LOOK: More commentary in ./Main.java.  See especially the
   discussion about 'sdaN' meaning the N'th state of the disk over
   time and not the state of any partition, this vmfs does not expose
   partitions, only whole device content.

   @see Main
*/
   
public class VirtualMachineFileSystem extends AbstractFilesystem3 {

	public VirtualMachineFileSystem() {
		readOnly = true;
		vms = new HashMap<String,VirtualMachine>();
		volumesByPath = new HashMap<String,VirtualDisk>();
		startTime = (int) (System.currentTimeMillis() / 1000L);
		log = LogFactory.getLog( getClass() );
		readBuffers = new HashMap<Object,byte[]>();
		writeBuffers = new HashMap<Object,byte[]>();
	}

	public void setIncludeSnapshots( boolean b ) {
		includeSnapshots = b;
	}

	public void setReadOnly( boolean b ) {
		readOnly = b;
	}
	
	public void add( VirtualMachine vm ) {
		vms.put( vm.getName(), vm );
		List<VirtualDisk> ads = vm.getActiveDisks();
		for( int i = 0; i < ads.size(); i++ ) {
			char diskNum = (char)('a' + i);
			VirtualDisk vd = ads.get(i);
			// vmName/sdN for all disks N
			String extName = vm.getName() + "/sd" + ("" + diskNum);
			volumesByPath.put( extName, vd );
			log.debug( "VMFS.put: " + extName + " -> " + vd );
			if( includeSnapshots ) {
				// /vmName/sdNG for all disks N and generations G of that disk
				extName = vm.getName() + "/sd" + ("" + diskNum) +
					( "" + vd.getGeneration() );
				volumesByPath.put( extName, vd );
				log.debug( "VMFS.put: " + extName + " -> " + vd );
				for( VirtualDisk an : vd.getAncestors() ) {
					int g = an.getGeneration();
					extName = vm.getName() + "/sd" + ("" + diskNum) + ("" + g);
					volumesByPath.put( extName, an );
					log.debug( "VMFS.put: " + extName + " -> " + an );
				}
			}
		}
	}
	
	public int volumeCount() {
		return volumesByPath.size();
	}

	/**
	 * The reverse lookup of volumesByPath, when the Volume handle is known
	 * and we want the path.  Used by e.g. Elvis for external commands
	 */
	public File pathTo( VirtualDisk vd ) {
		for( Map.Entry<String,VirtualDisk> me : volumesByPath.entrySet() ) {
			if( me.getValue() == vd )
				return new File( mountPoint, me.getKey() );
		}
		throw new IllegalArgumentException( "Unknown volume: " + vd );
	}
	
	public void mount( File mountPoint, boolean ownThread ) throws Exception {
		if( !mountPoint.isDirectory() )
			throw new IllegalArgumentException( "Mountpoint not a dir: " +
												mountPoint );
		this.mountPoint = mountPoint;
		/*
		  The -f says no fork, we need this!!
		  The -s says single-threaded, we need this!!
		*/
		String[] args = { mountPoint.getPath(), "-f", "-s"  };
		if( ownThread ) {
			ThreadGroup tg = new ThreadGroup( "VMFS.Threads" );
			FuseMount.mount( args, this, tg, log );
		} else {
			FuseMount.mount( args, this, log );
		}
	}
	
	public void umount() throws Exception {
		Process p = Runtime.getRuntime().exec( "fusermount -u " + mountPoint );
		p.waitFor();
	}
	
	/******************* fuse callback implementation ***************/
	
	@Override
	public int getattr( String path, FuseGetattrSetter getattrSetter )
		throws FuseException {
		if( log.isTraceEnabled() ) {
			log.trace( "getattr " + path );
		}
		if( path.equals( "/" ) ) {
			int count = vms.size();
			int time = startTime;
			getattrSetter.set
				("/".hashCode(), FuseFtypeConstants.TYPE_DIR | 0755, 2,
				 0, 0, 0,
				 // size, blocks lifted from example FakeFilesystem...
				 count * 128, (count * 128 + 512 - 1) / 512,
				 time, time, time);
			return 0;
		}

		String details = path.substring(1);

		// Path identifies just a vm by name, e.g. /someVMName
		Matcher m1 = NAMEP.matcher( details );
		if( m1.matches() ) {
			if( log.isDebugEnabled() )
				log.debug( "Is vmname: " + details );
			String vmName = m1.group(1);
			if( !vms.containsKey( vmName ) )
				return Errno.ENOENT;
			VirtualMachine vm = vms.get( vmName );
			List<VirtualDisk> vds = vm.getActiveDisks();
			int time = startTime;
			getattrSetter.set
				(vmName.hashCode(), FuseFtypeConstants.TYPE_DIR | 0755, 2,
				 0, 0, 0,
				 // size, blocks lifted from example FakeFilesystem...
				 vds.size() * 128, (vds.size() * 128 + 512 - 1) / 512,
				 time, time, time);
			return 0;
		}

		/*
		  Path identifies an active disk, e.g. /someVMName/sda
		  or a generation-identified disk, e.g. /someVMName/sda1
		*/
		Matcher m2 = ACTIVEDISKP.matcher( details );
		Matcher m3 = DISKGENP.matcher( details );
		if( m2.matches() || m3.matches() ) {
			if( log.isDebugEnabled() )
				log.debug( "Is disk: " + details );
			VirtualDisk vd = volumesByPath.get( details );
			if( log.isTraceEnabled() )
				log.trace( "Located disk: " + vd );
			if( vd == null )
				return Errno.ENOENT;
			VirtualDisk initial = vd.getGeneration(1);
			File f = initial.getPath();
			int time = (int)(f.lastModified() / 1000L);
			int mode = readOnly ? 0444 : 0644;
			getattrSetter.set
				( details.hashCode(), FuseFtypeConstants.TYPE_FILE | mode,
				  1, 0, 0, 0, vd.size(), (vd.size() + 512 - 1) / 512,
				  time, time, time );
			return 0;
		}

		return Errno.ENOENT;
	}

	@Override
	public int getdir( String path, FuseDirFiller filler )
		throws FuseException {

		if( log.isTraceEnabled() )
			log.trace( "getdir: " + path );

		if( "/".equals( path ) ) {
			for( String vmName : vms.keySet() ) {
				filler.add( vmName, vmName.hashCode(),
							FuseFtypeConstants.TYPE_DIR| 0755 );
			}
			return 0;
		}
		String details = path.substring(1);

		// Path identifies just a vm by name, e.g. /someVMName
		Matcher m1 = NAMEP.matcher( details );
		if( m1.matches() ) {
			String needle = m1.group(0);
			Set<String> matching = new HashSet<String>();
			for( String s : volumesByPath.keySet() ) {
				if( s.startsWith( needle ) ) {
					String suffix = s.substring( needle.length() + 1 );
					matching.add( suffix );
				}
			}
			if( matching.isEmpty() )
				return Errno.ENOENT;
			// LOOK: Only an active disk could be writable !!
			int mode = readOnly ? 0444 : 0644;
			for( String s : matching ) {
				filler.add( s, s.hashCode(),
							FuseFtypeConstants.TYPE_FILE | mode );
			}
			return 0;
		}

		return Errno.ENOENT;
	}

	/*
	  if open returns a filehandle by calling FuseOpenSetter.setFh()
	  method, it will be passed to every method that supports 'fh'
	  argument
	*/
	@Override
	public int open(String path, int flags, FuseOpenSetter openSetter)
		throws FuseException {

		if( readOnly ) {
			if( (flags & FilesystemConstants.O_WRONLY) ==
				FilesystemConstants.O_WRONLY ||
				(flags & FilesystemConstants.O_RDWR) ==
				FilesystemConstants.O_RDWR ) {
				return Errno.EROFS;
			}
		}
		
		if( log.isDebugEnabled() )
			log.debug( "Open: " + path + " " + flags );

		VirtualDisk vd = null;
		try {
			vd = locateVolume( path );
		} catch( IOException ioe ) {
			throw new FuseException( ioe );
		}
		if( vd == null )
			return Errno.ENOENT;

		try {
			RandomAccessVirtualDisk ravd = vd.getRandomAccess();
			if( log.isInfoEnabled() )
				log.info( path + ": fh = " + ravd );
			openSetter.setFh( ravd );
			return 0;
		} catch( IOException ioe ) {
			throw new FuseException( ioe );
		} catch( IllegalStateException corruptedFile ) {
			throw new FuseException( corruptedFile );
		}

	}

	// fh is filehandle passed from open
	@Override
	public int read(String path, Object fh, ByteBuffer buf, long offset)
		throws FuseException {

		if( log.isDebugEnabled() ) {
			log.debug( "read " + path + " " + fh + " " +
					   offset + " " + buf.remaining());
		}

		RandomAccessVirtualDisk ravd = (RandomAccessVirtualDisk)fh;
		try {
			ravd.seek( offset );

			/*
			  We keep building a bigger read buffer for each open 'file'.
			  Remember that any read may be for a smaller byte count
			  than the previous one, so use the 3-arg version of read
			*/
			byte[] ba;
			int nin;
			if( true ) {
				ba = readBuffers.get( fh );
				if( ba == null || buf.remaining() > ba.length ) {
					ba = new byte[buf.remaining()];
					readBuffers.put( fh, ba );
					if( log.isInfoEnabled() )
						log.info( "New read buffer for " + path +
								  " = " + ba.length );
				}
				nin = ravd.read( ba, 0, buf.remaining() );
			} else {
				ba = new byte[buf.remaining()];
				nin = ravd.read( ba );
			}
			
			if( log.isDebugEnabled() ) {
				log.debug( "ravd.read " + nin );
			}
			
			if( nin > -1 )
				buf.put( ba, 0, nin );
			else
				// need this??  does this tells fuse we are at eof???
				buf.put( ba, 0, 0 );
			/*
			  the fuse4j api says we return 0, NOT the byte count written
			  to the ByteBuffer
			*/
			return 0;
		} catch( Exception e ) {
			log.warn( e, e );
			log.warn( path + " " + offset + " " + buf.remaining() );
			throw new FuseException( e );
		}
		
	}

	// fh is filehandle passed from open,
   // isWritepage indicates that write was caused by a writepage
	@Override
	public int write(String path, Object fh, boolean isWritepage,
					 ByteBuffer buf, long offset) throws FuseException {

		if( log.isDebugEnabled() ) {
			log.debug( "write " + path + " " + offset + " " + buf.remaining());
			log.debug( "fh " + fh );
		}

		/*
		  Extra guard on writes to a supposed read-only fs.  The open
		  call should return same error, but be sure...
		*/
		if( readOnly )
			return Errno.EROFS;

		RandomAccessVirtualDisk ravd = (RandomAccessVirtualDisk)fh;
		try {
			ravd.seek( offset );
			
			/*
			  We keep building a bigger write buffer for each open 'file'
			*/
			byte[] ba;
			if( true ) {
				ba = writeBuffers.get( fh );
				if( ba == null || buf.remaining() > ba.length ) {
					ba = new byte[buf.remaining()];
					writeBuffers.put( fh, ba );
					if( log.isInfoEnabled() )
						log.info( "Write buffer for " + path + " = "
								  + ba.length );
				}
			} else {
				ba = new byte[buf.remaining()];
			}
			int nout = buf.remaining();
			buf.get( ba, 0, nout );

			if( log.isDebugEnabled() ) {
				log.debug( "sos.write " + nout );
			}
			ravd.write( ba, 0, nout );
			return 0;
		} catch( Exception e ) {
			log.warn( e, e );
			log.warn( path + " " + offset + " " + buf.remaining() );
			throw new FuseException( e );
		}
	}
	
   // called when last filehandle is closed, fh is filehandle passed from open
	@Override
	public int release(String path, Object fh, int flags) throws FuseException {
		log.trace( "release" );
		readBuffers.remove( fh );
		writeBuffers.remove( fh );
		return 0;
	}

	/**
	   @return a null return here will manifest in an
	   Errno.ENOENT return to fuse
	*/
	private VirtualDisk locateVolume( String path )
		throws IOException, FuseException {
		String details = path.substring( 1 );
		VirtualDisk result = volumesByPath.get( details );
		return result;
	}


	// allow access by Main.java in this package
	final Map<String,VirtualDisk> volumesByPath;

	private final Map<String,VirtualMachine> vms;
	private boolean includeSnapshots, readOnly;
	private File mountPoint;
	private final int startTime;
	private final Log log;
	private final Map<Object,byte[]> readBuffers;
	private final Map<Object,byte[]> writeBuffers;

	// Allow access from unit tests...

	// LOOK: really ANY char that is not '/' is valid in a vm name
	//	static final String NAMERE = "([\\p{Alnum}_\\-\\.]+)";
	static final String NAMERE = "([^/]+)";
	
	static final String DISKRE = "sd([a-z])";

	static final String GENRE = "(\\d+)";

	/**
	   Recall that there is an arbitrary sequence of operations
	   regarding taking vm snapshots and adding disks to a vm.  When
	   we 'snapshot' a VM, it freezes ALL existing disks.  But were we
	   to do this:

	   create vm, with single disk, call it disk 1
	   snapshot1, freezes disk 1
	   add disk 2
	   snapshot2, freezes both disk 1, disk 2

	   then after snapshot2, disk 1 has 3 different states, while disk
	   2 has only 2 different states.  So generation identification
	   has to be on a per-disk level, not on a vm level.
	*/
	
	// a vmName e.g. /winxp1
	static final Pattern NAMEP =
		Pattern.compile( "^" + NAMERE + "$" );

	// an active disk within a vm, e.g. /winxp1/sda.  Note no generation info
	static final Pattern ACTIVEDISKP =
		Pattern.compile( "^" + NAMERE + "/" + DISKRE + "$" );

	// an generation-identified disk within a vm, e.g. /winxp1/sda1
	static final Pattern DISKGENP =
		Pattern.compile( "^" + NAMERE + "/" + DISKRE + GENRE + "$" );
}

// eof
