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
 * For more info on virtual disk snapshotting, and how it leads to a
 * hierarchy of disks in a parent/child tree, see e.g.
 *
 * http://searchvmware.techtarget.com/tip/How-VMware-snapshots-work
 */
abstract public class VirtualDisk {
	
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
	 */
	public void setChild( VirtualDisk vd ) {
		child = vd;
		vd.setParent( this );
	}

	/**
	 * Set the parent of this disk to the supplied disk p.  A check is
	 * made on the linkage between the two, using getUUID and
	 * getUUIDParent.
	 */
	void setParent( VirtualDisk p ) {
		UUID linkage = p.getUUID();
		if( !linkage.equals( getUUIDParent() ) ) {
			throw new IllegalArgumentException
				( "Linkage mismatch setting parent " + source + "," +
				  p.getPath() );
		}
		parent = p;
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

	abstract public UUID getUUID();

	abstract public UUID getUUIDParent();
	
	abstract public InputStream getInputStream() throws IOException;

	abstract public RandomAccessVirtualDisk getRandomAccess()
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
	 * @return The disk in the parent-child disk tree which is the
	 * 'live' disk and would be read/written were the VM containing
	 * that disk to be powered on.
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
	 * taken, or this disk the first created in a snapshot family.
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

	static public final UUID NULLUUID = new UUID( 0L, 0L );

}

// eof
