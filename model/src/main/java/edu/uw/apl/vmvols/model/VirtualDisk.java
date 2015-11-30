package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author Stuart Maclean
 *
 * Base class for all virtual disks.  Currently we have two subclass
 * families: one for VirtualBox disks (contained normally in a file
 * with a .vdi suffix) and one for VMWare disks (contained normally in
 * a file with a .vmdk suffix).
 *
 * VM engines often support 'Snapshotting' of virtual machines.
 * VirtualBox and VMware do.  Snapshotting freezes VM content,
 * including disk content, in time.  We understand these snapshot
 * features and expose them as 'generations' of a virtual disk.
 *
 * Generation 1 is the disk when created.  We call this the
 * <em>base</em> disk, and it corresponds to the .vdi/.vmdk file
 * created by the VM engine when the disk is first created (including
 * if imported from an OVF/OVA file).
 *
 * Generation 2 would be the disk once one snapshot had be taken, etc.
 * The <em>active</em> disk is the one with the highest generation
 * number, and it corresponds to the version of the disk that would be
 * read/written were the VM to be powered up.
 *
 * Note how generationing applies to each virtual disk and
 * <em>not</em> each virtual machine.  Imagine this sequence of events:
 *
 * VM created, with a single virtual hard drive (disk).  This is our
 * base disk.
 *
 * Later, snapshot of the VM taken, the single disk has its content
 * frozen.  No further writes go to this version of the disk.  VM
 * creates a new file to which further writes are done.  The new file
 * represents generation 2 of the disk, the original disk is still
 * generation 1.
 *
 * Later, a second hard drive added to the VM.  It is again a base
 * disk, and has generation 1, not 2.
 *
 * Later, a second snapshot of the VM taken.  Our first hard drive now
 * has 3 generations, and our second disk has two generations.
 *
 * After the second snapshot, the active disks are generation 3 for
 * first disk and generation 2 for second disk.  The base disks are
 * generation 1 for both disks.  Our VM api supports access to
 *
 * activeDisks
 * baseDisks
 * identified generations
 *
 * The basic API for accessing the data of virtual disk is very small, just
 *
 * getInputStream
 * getRandomAccess
 *
 * Example usage:
 *
 <code>
 // No generation, retrieves ACTIVE disk, highest generation
 VirtualDisk vd = VirtualDisk.create( new File( "foo.vdi" ) );

 // Some identified generation...
 VirtualDisk vd = VirtualDisk.create( new File( "foo.vdi" ), 2 );

 java.io.InputStream is = vd.getInputStream();

 RandomAccessVirtualDisk ravd = vd.getRandomAccess();
 </code>

 * Combined with our 'fuse' module these are however enough to provide
 * complete access to whole disk data at the host system call level,
 * i.e. by tools like Sleuthkit.  In fact just RandomAccessVirtualDisk
 * suffices in the fuse 'backend'.
 *
 * For more info on virtual disk snapshotting, and how it leads to a
 * hierarchy of disks in a parent/child tree, see e.g.
 *
 * http://searchvmware.techtarget.com/tip/How-VMware-snapshots-work
 */
abstract public class VirtualDisk {

	/**
	 * When we care just about a single disk and not all the disks
	 * associated with a full VM.  Allows user to skip use of
	 * the VirtualMachine class altogether.
	 *
	 * Convenience class for the full create method, with caller
	 * expecting/getting the active disk derived from the one
	 * identified by f
	 *
	 * @param f a File on the host system identifying the
	 * <em>base</em> disk of one of the hard disks in a VM.  By base,
	 * we mean the first-created file representing a virtual disk, and
	 * not any file representing a Snapshot delta.
	 *
	 * The parameter f can also identity a VM directory on the host
	 * file system, but only if that VM has a single disk. Otherwise f
	 * is ambiguous and an exact base virtual disk host file must be
	 * supplied instead.
	 */
	static public VirtualDisk create( File f ) throws IOException {
		return create( f, ACTIVE );
	}

	/**
	 * @param f a File on the host system identifying the
	 * <em>base</em> disk of one of the hard disks in a VM.  By base,
	 * we mean the first-created file representing a virtual disk, and
	 * not any file representing a Snapshot delta.
	 *
	 * The parameter f can also identity a VM directory on the host
	 * file system, but only if that VM has a single disk. Otherwise f
	 * is ambiguous and an exact base virtual disk host file must be
	 * supplied instead.
	 *
	 * @param generation, where 1 is a base disk, or
	 * VirtualDisk.ACTIVE for the active disk (so user need not have
	 * to know/calculate the active disk's generation).
	 */
	static public VirtualDisk create( File f, int generation )
		throws IOException {
		
		VirtualMachine vm = VirtualMachine.create( f );
		if( vm == null )
			throw new IllegalArgumentException( "Unknown host disk file: " +
												f );

		List<VirtualDisk> bases = vm.getBaseDisks();

		System.out.println( bases );

		if( f.isDirectory() ) {
			if( bases.size() > 1 ) {
				List<File> fs = new ArrayList<File>();
				for( VirtualDisk vd : bases )
					fs.add( vd.getPath() );
				throw new IllegalArgumentException
					( "Ambiguous request, VM dir " + f + " has " +
					  fs.size() + " disks: " + fs );
			}
			VirtualDisk vd = bases.get(0);
			return ( generation == ACTIVE ) ? vd.getActive() :
				vd.getGeneration( generation );
		}

		VirtualDisk vd = null;
		for( VirtualDisk base : bases ) {
			System.out.println( "B : "+ base.getPath() );
			System.out.println( "F : "+ f );
			if( base.getPath().equals( f ) ) {
				vd = base;
				break;
			}
		}
		/*
		  if vm != null, we should be able to locate the disk associated
		  with f, so not locating it is a logic error
		*/
		if( vd == null )
			throw new IllegalStateException
				( "Cannot locate disk in VM built from " + f );

		return ( generation == ACTIVE ) ? vd.getActive() :
			vd.getGeneration( generation );
	}

	
	protected VirtualDisk( File source ) {
		this.source = source;
		log = LogFactory.getLog( getClass() );
	}

	public File getPath() {
		return source;
	}

	public long sectorCount() {
		return size() / Constants.SECTORLENGTH;
	}

	/**
	 * Snapshots result in a parent-child relationships between disks.
	 * The frozen disk is the parent, the varying disk is the child,
	 * and changes as that disk is written.  Which of these is 'the
	 * snapshot' is a matter of interpretation!
	 *
	 * This method only called the 'link disks' procedure of VMs
	 *
	 * @see virtualbox.VBoxVM
	 * @see vmware.VMwareVM
	 */
	public void setChild( VirtualDisk vd ) {
		child = vd;
	}

	/**
	 * As per setChild
	 *
	 * @see setChild
	 */
	public void setParent( VirtualDisk vd ) {
		parent = vd;
	}
	
	/**
	 * Mimic what a physical disk would return for a low-level
	 * ATA/SCSI inquiry of its 'serial number'.  We'll likely
	 * use/include the UUIDs that VM engines use to manage virtual
	 * disks.
	 */
	abstract public String getID();

	/**
	 * Size of this virtual disk, in bytes
	 */
	abstract public long size();

	/**
	 * VMs always label their virtual disks with a UUID, and use it
	 * for identifying/maintaining parent-child content relationships
	 * across snapshots.  We expose this feature too.  It is really
	 * only used to again identify disk generations.
	 */
	abstract public UUID getUUID();

	abstract public UUID getUUIDParent();

	/**
	 * @return The familiar java.io.InputStream, so whole disk content
	 * can be read in a 'forward-only' fashion.  More useful is
	 * getRandomAccess, which then supports seekable access (and
	 * writes!)
	 */
	abstract public InputStream getInputStream() throws IOException;

	/**
	 * @return Our local RandomAccessVirtualDisk which partially mimics
	 * java.io.RandomAccessFile, given read,write and seekable access to
	 * the virtual disk content.
	 */
	abstract public RandomAccessVirtualDisk getRandomAccess( boolean writeable )
		throws IOException;

	/**
	 * @return Disk generation, where a newly created, never
	 * snapshotted-disk is assigned a generation of 1.  Each snapshot
	 * introduces a new child, and a corresponding increment of the
	 * generation value.
	 */
	public int getGeneration() {
		if( parent == null )
			return 1;
		return 1 + parent.getGeneration();
	}

	/**
	 * @return The disk in the parent-child disk tree which includes
	 * this disk and has generation 1.  For disk which has never been
	 * snapshotted, generation is 1 and getBase and getActive return
	 * this.
	 *
	 * @see getGeneration
	 */
	public VirtualDisk getBase() {
		if( parent == null )
			return this;
		return parent.getBase();
	}

	/**
	 * @return The disk in the parent-child disk tree which includes
	 * this disk and which is the 'live' disk and would be
	 * read/written were the VM containing that disk to be powered on.
	 */
	public VirtualDisk getActive() {
		if( child == null )
			return this;
		return child.getActive();
	}

	/**
	 * @return the virtual disk in the hierarchy which includes this
	 * disk and has the specified generation value.
	 *
	 * @throws IllegalArgumentException if i denotes some non-existent
	 * generation.
	 */
	public VirtualDisk getGeneration( int i ) {
		int g = getGeneration();
		if( g == i )
			return this;
		if( i < g ) {
			if( parent == null )
				throw new IllegalArgumentException
					( source + ": No generation " + i );
			return parent.getGeneration( i );
		}
		// g > i 
		if( child == null )
			throw new IllegalArgumentException
				( source + ": No generation " + i );
		return child.getGeneration( i );
	}

	/**
	 * @return All the ancestors of this disk, i.e. all parents,
	 * across all snapshots, recursively.  Empty if no snapshots ever
	 * taken, or this disk the first created in a snapshot family,
	 * i.e. is a base disk.
	 */
	public List<VirtualDisk> getAncestors() {
		List<VirtualDisk> result = new ArrayList<VirtualDisk>();
		getAncestors( result );
		return result;
	}

	// Private helper routine for getAncestors(), using recursion of course!
	private void getAncestors( List<VirtualDisk> accumulator ) {
		if( parent == null )
			return;
		accumulator.add( parent );
		parent.getAncestors( accumulator );
	}

	protected final File source;
	protected VirtualDisk parent, child;
	protected final Log log;

	static public final int BASE = 1;
	static public final int ACTIVE = -1;
	
	static public final UUID NULLUUID = new UUID( 0L, 0L );
}

// eof
