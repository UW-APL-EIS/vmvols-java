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
   An implementation of fuse.FuseFS (via the higher level
   fuse.Filesystem3) where the 'filesystem' is simply a list of
   VirtualDisks (currently we have implemented virtualbox.vdi and
   vmware.vmdk formats) under a single directory.  So a directory
   listing presents a list of what looks like devices (.vdi files) and
   partitions within those devices (.vdi:N files)
   
   All we really care about is getattr,open,read.  We have stub/noops
   for the other parts of the fuse api.

   To actually make such an fs visible at the OS-level, we do:

   VirtualMachineFS fs = new VirtualMachineFS();
   fs.add( some VMs );
   String[] args = { "mountpoint", "-f" };
   Log log = LogFactory.getLog( My.class );
   FuseMount.mount(args, fs, log );

   By making virtual disks (e.g. VirtualBox .vdi) visible to fuse, we
   make the underlying .dd bit streams inside the virtual disks
   available at the file system level, and thus visible to
   e.g. Sleuthkit.

   Further, we have added the capability to see snapshot disks too.  The
   paths under the file system root are as follows:

   /vm1name
   /vm2name

   /vmname/0/ -- shows all disks and partitions with generation 0

   /vmname/sda 
   /vmname/sda1 
   /vmname/sdb 
   /vmname/sdb1 -- shows all ACTIVE disks and partitions, so generation omitted

   Normally you would want to access the 'active' disk, i.e. the one
   that the VM engine uses for disk reads/writes, so for that one, we
   omit the generation in the path, so you use the expected
   /vmname/sda, just like you would use /dev/sda

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
			String extName = vm.getName() + "/sd" + ("" + diskNum);
			volumesByPath.put( extName, vd );
			if( includeSnapshots ) {
				for( VirtualDisk an : vd.getAncestors() ) {
					int g = an.getGeneration();
					extName = vm.getName() + "/" + g + "/sd" + ("" + diskNum);
					volumesByPath.put( extName, an );
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
	public String getPath( VirtualDisk vd ) {
		for( Map.Entry<String,VirtualDisk> me : volumesByPath.entrySet() ) {
			if( me.getValue() == vd )
				return me.getKey();
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
		Matcher m1 = NAMEP.matcher( details );
		if( m1.matches() ) {
			if( log.isDebugEnabled() )
				log.debug( "Is vmname: " + details );
			String vmName = m1.group(1);
			if( !vms.containsKey( vmName ) )
				return Errno.ENOENT;
			VirtualMachine vm = vms.get( vmName );
			List<? extends VirtualDisk> vds = vm.getActiveDisks();
			int time = startTime;
			getattrSetter.set
				(vmName.hashCode(), FuseFtypeConstants.TYPE_DIR | 0755, 2,
				 0, 0, 0,
				 // size, blocks lifted from example FakeFilesystem...
				 vds.size() * 128, (vds.size() * 128 + 512 - 1) / 512,
				 time, time, time);
			return 0;
		}

		Matcher m2 = NAMEGENP.matcher( details );
		if( m2.matches() && includeSnapshots ) {
			if( log.isDebugEnabled() )
				log.debug( "Is vmname+gen: " + details );
			String vmName = m2.group(1);
			int g = Integer.parseInt( m2.group(2) );
			if( !vms.containsKey( vmName ) )
				return Errno.ENOENT;
			VirtualMachine vm = vms.get( vmName );
			List<VirtualDisk> vds = vm.getBaseDisks();
			int n = 0;
			for( VirtualDisk vd : vds ) {
				try {
					VirtualDisk tmp = vd.getGeneration( g );
					n++;
				} catch( IllegalStateException ise ) {
					continue;
				}
			}
			if( n == 0 )
				return Errno.ENOENT;
			int time = startTime;
			getattrSetter.set
				(vmName.hashCode(), FuseFtypeConstants.TYPE_DIR | 0755, 2,
				 0, 0, 0,
				 // size, blocks lifted from example FakeFilesystem...
				 n * 128, (n * 128 + 512 - 1) / 512, time, time, time);
			return 0;
		}
		
		Matcher m3 = DISKP.matcher( details );
		Matcher m4 = ACTIVEDISKP.matcher( details );
		if( m3.matches() || m4.matches() ) {
			if( log.isDebugEnabled() )
				log.debug( "Matches disk: " + details );
			VirtualDisk vd = volumesByPath.get( details );
			if( vd == null )
				return Errno.ENOENT;
			VirtualDisk initial = vd.getGeneration(0);
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
		Matcher m1 = NAMEP.matcher( details );
		if( m1.matches() ) {
			String needle = m1.group(0);
			Set<String> matchingFiles = new HashSet<String>();
			Set<String> matchingDirs = new HashSet<String>();
			for( String s : volumesByPath.keySet() ) {
				if( s.startsWith( needle ) ) {
					/*
					  If the input is /name, the matching volumes can be

					  /sda
					  /sda1
					  /N0/sda
					  /N0/sda1
					  /N1/sda
					  /N1/sda1
					  
					  for some generations Ni.  In these cases, the output
					  must be a directory named /N and not /N/sda*.  If
					  no generation in the suffix, output is a file
					*/
					String suffix = s.substring( needle.length() + 1 );
					int sep = suffix.indexOf( "/" );
					if( sep > -1 ) {
						suffix = suffix.substring( 0, sep );
						matchingDirs.add( suffix );
					} else {
						matchingFiles.add( suffix );
					}
				}
			}
			if( matchingFiles.isEmpty() && matchingDirs.isEmpty() )
				return Errno.ENOENT;
			for( String s : matchingDirs )
				filler.add( s, s.hashCode(),
							FuseFtypeConstants.TYPE_DIR| 0755 );
			for( String s : matchingFiles ) {
				filler.add( s, s.hashCode(),
							FuseFtypeConstants.TYPE_FILE| 0644 );
			}
			return 0;
		}
		
		Matcher m2 = NAMEGENP.matcher( details );
		if( m2.matches() && includeSnapshots ) {
			String needle = m2.group(0);
			Set<String> matching = new HashSet<String>();
			for( String s : volumesByPath.keySet() ) {
				if( s.startsWith( needle ) ) {
					String suffix = s.substring( needle.length() + 1 );
					matching.add( suffix );
				}
			}
			if( matching.isEmpty() )
				return Errno.ENOENT;
			for( String s : matching ) {
				filler.add( s, s.hashCode(),
							FuseFtypeConstants.TYPE_FILE| 0644 );
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
	static final String NAMERE = "([\\p{Alnum}_\\-\\.]+)";
	
	static final String GENRE = "(\\d+)";
	static final String DISKRE = "(sd[a-z])";

	// a vmName e.g. /winxp1
	static final Pattern NAMEP =
		Pattern.compile( "^" + NAMERE + "$" );

	// a vmName plus disk generation e.g. /winxp1/0
	static final Pattern NAMEGENP =
		Pattern.compile( "^" + NAMERE + "/" + GENRE + "$" );
	
	// a disk with generation within a vm, e.g. /winxp1/0/sda.
	static final Pattern DISKP =
		Pattern.compile( "^" + NAMERE + "/" + GENRE + "/" + DISKRE + "$" );

	// an active disk within a vm, e.g. /winxp1/sda.  Note no generation
	static final Pattern ACTIVEDISKP =
		Pattern.compile( "^" + NAMERE + "/" + DISKRE + "$" );
}

// eof
