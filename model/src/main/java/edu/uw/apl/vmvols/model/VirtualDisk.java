/**
 * Copyright © 2015, University of Washington
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the University of Washington nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE UNIVERSITY
 * OF WASHINGTON BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.virtualbox.VDIDisk;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;

/**
 *
 * @author Stuart Maclean
 *
 * Base class for all virtual disks.  Currently we have two subclass
 * families: one for VirtualBox disks (contained normally in a file
 * with a .vdi suffix) and one for VMWare disks (contained normally in
 * a file with a .vmdk suffix).
 *
 * Contains also important static methods for VirtualDisk creation, given
 * host files (.vdi, .vmdk).
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
 *
 * The <em>active</em> disk is the one with the highest generation
 * number, and it corresponds to the version of the disk that would be
 * read/written were the VM to be powered up.
 *
 * Note how generationing applies to each virtual disk independently,
 * and <em>not</em> each virtual machine.  Imagine this sequence of
 * events:
 *
 * VM created, with a single virtual hard drive (disk).  This is our
 * (first) base disk.
 *
 * Later, a snapshot of the VM is taken, and the single disk has its
 * content frozen.  No further writes go to this version of the disk.
 * VM creates a new file to which further writes are done.  The new
 * file represents generation 2 of the disk, the original disk is
 * still generation 1.
 *
 * Later, a second hard drive added to the VM.  It is again a base
 * disk, and by definition has generation 1.
 *
 * Later, a second snapshot of the VM taken.  Our first hard drive now
 * has 3 generations, and our second disk has two generations.
 *
 * After the second snapshot, the active disks are generation 3 for
 * the first disk and generation 2 for the later-added second disk.
 * The base disks are generation 1 for both disks.  Our VM api
 * supports access to
 *
 * activeDisks
 * baseDisks
 * identified generations
 *
 * Example usage:
 *
 <code>
 // No generation given, retrieves generation of virtual disk whose source
 // is supplied file
 VirtualDisk vd = VirtualDisk.create( new File( "foo.vdi" ) );

 // Some identified generation...
 VirtualDisk vd = VirtualDisk.create( new File( "foo.vdi" ), 2 );

 // Base disk, generation 1 ...
 VirtualDisk vd = VirtualDisk.create( new File( "foo.vdi" ), BASE );

 // Active disk, highest generation...
 VirtualDisk vd = VirtualDisk.create( new File( "foo.vdi" ), ACTIVE );

 // Virtual Machine enclosing any VD is available
 VirtualMachine vm = vd.getVirtualMachine();
</code>

 * The basic API for accessing the data of virtual disks is very small, just
 *
 * getInputStream
 * getRandomAccess

 <code>
 java.io.InputStream is = vd.getInputStream();

 RandomAccessVirtualDisk ravd = vd.getRandomAccess();
 </code>

 * Combined with our 'fuse' module, the API above is enough to provide
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
	 * associated with a full VM. Allows user to skip use of
	 * the VirtualMachine class altogether.
	 *
	 * Convenience call for the full create method, with caller
	 * expecting/getting the actual VirtualDisk generation whose
	 * source host file is the file passed in to the create.  See also
	 * other create call where an explicit generation is supplied.
	 *
	 */
	static public VirtualDisk create( File f ) throws IOException {
		return create( f, SELF );
	}

	/**
	 * @param f a File on the host system containing some snapshot of
	 * some virtual disk within some virtual machine, OR perhaps a
	 * standalone/orphaned .vmdk file as used/generated by
	 * e.g. packer/vagrant or that is bundled with an ovf/ova file.
	 *
	 * File f is <em>not</em> to be a VM directory.  For directories
	 * that host VMs, use the VirtualMachine.create() api instead.
	 *
	 * @param generation, where 1 is a base disk, or
	 * VirtualDisk.ACTIVE for the active disk (so user need not have
	 * to know/calculate the active disk's generation), or
	 * VirtualDisk.SELF meaning that the VirtualDisk's source file
	 * should be f itself.
	 */
	static public VirtualDisk create( File f, int generation )
		throws IOException {

		if( !f.exists() )
			throw new IllegalArgumentException( "No such file: " + f );
			
		if( f.isDirectory() ) {
			throw new IllegalArgumentException( "Is a VM directory? " + f );
		}

		File dir = f.getParentFile();
		try {
			VirtualMachine vm = VirtualMachine.create( dir );
			return create( vm, f, generation );
		} catch( IllegalArgumentException notVMDirWeUnderstand ) {
			/*
			  If f were a Snapshot, it could be in a sub-dir (Snapshots?)
			  of the VM home, so try this too...
			*/
			dir = dir.getParentFile();
			try {
				VirtualMachine vm = VirtualMachine.create( dir );
				return create( vm, f, generation );
			} catch( IllegalArgumentException notVMDirWeUnderstandEither ) {
				return createStandalone( f, generation );
			}
		}
	}

	static private VirtualDisk create( VirtualMachine vm,
									   File f, int generation )
		throws IOException {

		/*
		  Given the VM, we can compose ALL its VirtualDisks, meaning
		  all snapshots of all drives.  Somewhere in that set is a
		  VirtualDisk whose source file should be the supplied file f.
		  Once matched, go on to derive the generation of that VD
		  matching the supplied generation.
		*/
		List<VirtualDisk> allDisks = new ArrayList();
		List<VirtualDisk> actives = vm.getActiveDisks();
		allDisks.addAll( actives );
		for( VirtualDisk vd : actives ) {
			List<VirtualDisk> ancs = vd.getAncestors();
			allDisks.addAll( ancs );
		}
		VirtualDisk match = null;
		File fCanon = f.getCanonicalFile();
		for( VirtualDisk vd : allDisks ) {
			if( vd.getPath().getCanonicalFile().equals( fCanon ) ) {
				match = vd;
				break;
			}
		}
		if( match == null )
			throw new IllegalArgumentException( "Cannot associate " +
												f + " with VM " +
												vm.getName() );
		match.vm = vm;
		switch( generation ) {
		case SELF:
			return match;
		case BASE:
			return match.getBase();
		case ACTIVE:
			return match.getActive();
		default:
			return match.getGeneration( generation );
		}
	}

	static private VirtualDisk createStandalone( File f, int generation )
		throws IOException {

		if( generation > BASE )
			throw new NoSuchGenerationException( generation );
		
		VirtualDisk vd = null;
		String name = f.getName();
		if( false ) {
		} else if( name.endsWith( VDIDisk.FILESUFFIX ) ) {
			vd = VDIDisk.readFrom( f );
		} else if( name.endsWith( VMDKDisk.FILESUFFIX ) ) {
			vd = VMDKDisk.readFrom( f );
		} else {
			throw new IllegalArgumentException
				( "Cannot create virtual disk from: " + f );
		}

		/*
		  Built a valid 'orphaned' VirtualDisk.  Need to next create
		  an 'anonymous' VM to host it
		*/
		final List<VirtualDisk> vds = new ArrayList();
		vds.add( vd );
		final String namef = name;
		vd.vm = new VirtualMachine() {
				@Override
				public String getName() {
					return namef;
				}
				@Override
				public List<VirtualDisk> getBaseDisks() {
					return vds;
				}
				@Override
				public List<VirtualDisk> getActiveDisks() {
					return getBaseDisks();
				}
			};
		return vd;
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

	public VirtualMachine getVirtualMachine() {
		return vm;
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
	abstract public RandomAccessVirtualDisk getRandomAccess
		( boolean writeable ) throws IOException;

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
	 * @throws NoSuchGenerationException if i denotes some non-existent
	 * generation.
	 */
	public VirtualDisk getGeneration( int i ) {
		int g = getGeneration();

		if( g == i )
			return this;
		if( i < g ) {
			if( parent == null )
				throw new NoSuchGenerationException( i );
			return parent.getGeneration( i );
		}
		// i > g 
		if( child == null )
			throw new NoSuchGenerationException( i );
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
	protected VirtualMachine vm;
	protected final Log log;

	static public final int SELF = 0;
	static public final int BASE = 1;
	static public final int ACTIVE = -1;
	
	static public final UUID NULLUUID = new UUID( 0L, 0L );
}

// eof
