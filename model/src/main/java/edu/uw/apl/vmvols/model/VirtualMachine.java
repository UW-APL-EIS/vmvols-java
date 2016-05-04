package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.uw.apl.vmvols.model.virtualbox.VDIDisk;
import edu.uw.apl.vmvols.model.virtualbox.VDIException;
import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;
import edu.uw.apl.vmvols.model.vmware.VMDKException;
import edu.uw.apl.vmvols.model.vmware.VMwareVM;

/**
 * @author Stuart Maclean
 *
 * A base class for all (two!) the virtual machine managers whose disk
 * formats we support: VirtualBox and VMWare (host-based, not esxi/vmfs,
 * etc) .
 *
 * Example: for a virtual machine (perhaps a VirtualBox one) in dir
 * 'M' with a single virtual hard disk, this code gets us access to
 * the disk as a 'device file', i.e. whole disk (not any partitions,
 * use Sleuthkit for volumeSystem/partition traversal)).
 *
 <code>
 VirtualMachine vm = VirtualMachine.create( "/path/to/M" );
 List<VirtualDisk> vds = vm.getActiveDisks();
 VirtualDisk vd = vds.get(0);
 </code>
 *
 * Note that this is a 'pure-Java' implementation.  We have pure-Java
 * file format parsers for VirtualBox and VMware (host-based products
 * like Workstation, not any vmfs/ESX-originating disks).  There is no
 * JNI portion binding to any VM engine libraries.  In fact to use
 * this Java API, the VM software need not even be present.  All we
 * need are the .vdi/.vmdk files themselves.
 *
 * @see virtualbox.VBoxVM
 * @see vmware.VMwareVM
 */

abstract public class VirtualMachine {

	/**
	 * This should be the sole api method for generating
	 * VirtualMachines and accessing virtual disks from those
	 * VirtualMachines.  The user should never have to call any VBoxVM
	 * or VMwareVM methods directly.
	 *
	 * Update as and when we might ever accommodate more VM types
	 * (ha!)
	 */
	static public VirtualMachine create( File f ) throws IOException {
		if( VBoxVM.isVBoxVM( f ) )
			return new VBoxVM( f );
		if( VMwareVM.isVMwareVM( f ) )
			return new VMwareVM( f );
		throw new IllegalStateException( "Cannot create VirtualMachine from "+
										 f );
	}

	abstract public String getName();

	abstract public List<VirtualDisk> getBaseDisks();
	abstract public List<VirtualDisk> getActiveDisks();
}

// eof
