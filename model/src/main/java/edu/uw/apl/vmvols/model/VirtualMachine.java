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
		throw new IllegalArgumentException
			( "Not a virtual machine directory "+ f );
	}

	abstract public String getName();

	abstract public List<VirtualDisk> getBaseDisks();
	abstract public List<VirtualDisk> getActiveDisks();
}

// eof
