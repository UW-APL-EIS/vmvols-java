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
package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Collections;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.FileUtils;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;

/**
 * @author Stuart Maclean
 *
 * To locate the virtual disk(s) in a VMWare vm directory, do this:
 *
 <code> 
 File theDir = ....
 boolean b = VMwareVM.isVMware( theDir );
 if( b ) {
  VMwareVM vm = new VMwareVM( theDir );
  List<VirtualDisk> disks = vm.getActiveDisks();
 }
 </code>
 *
 * Alternatively, and better, is the VM-agnostic api:
 *
 <code>
 VirtualMachine vm = VirtualMachine.create( theDir );
 List<VirtualDisk> disks = vm.getActiveDisks();
 </code>
 *
 * An 'active' disk is one which would be written to were the VM to be
 * running.  It is the 'current snapshot'.  This API also supports
 * accessing any 'generation' (Snapshot) of any disk, all the way up
 * the 'base disk' (which has generation 1), which is held in the file
 * the VMWare application (Workstation?) created when the VM was first
 * built.  If a VM has never had a Snapshot taken, its active disk(s)
 * and base disk(s) are the same thing.
 *
 * @see edu.uw.apl.vmvols.model.VirtualMachine
 * @see edu.uw.apl.vmvols.model.virtualbox.VBoxVM
 */
public class VMwareVM extends VirtualMachine {

	/**
	 * Test to see if a directory in the filesystem looks like
	 * it is home to a VMware virtual machine
	 */
	static public boolean isVMwareVM( File dir ) {
		if( !dir.isDirectory() )
			return false;

		File[] fs = dir.listFiles( VMXFILE );
		// If we find a .vmx file, we assert yes ????
		if( fs.length > 0 )
			return true;
		return false;
	}

	public VMwareVM( File f ) throws IOException {
		if( !isVMwareVM( f ) )
			throw new IllegalArgumentException
				( "Not a VMware VM directory: " + f );
		dir = f;
		baseDisks = new ArrayList<VirtualDisk>();
		log = LogFactory.getLog( getClass() );
		List<VMDKDisk> children = new ArrayList<VMDKDisk>();

		// A VMware VM can manage VMware .vmdk disks...
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { VMDKDisk.FILESUFFIX }, true );
		for( File el : fs ) {
			VMDKDisk vmdk = null;
			try {
				vmdk = VMDKDisk.readFrom( el );
			} catch( VMDKException e ) {
				log.warn( el + ": " + e );
				continue;
			}
			if( vmdk == null )
				// LOOK: log e ??
				continue;
			if( vmdk.getParentFileNameHint() == null )
				baseDisks.add( vmdk );
			else
				children.add( vmdk );
		}

		// Link base disks to child disks...
		for( VirtualDisk d : baseDisks )
			link( d, children );
	}

	@Override
	public String getName() {
		return dir.getName();
	}
	
	// the base disks are what we actually maintain...
	@Override
	public List<VirtualDisk> getBaseDisks() {
		return baseDisks;
	}

	// any active disks are derivable from their base disk counterpart...
	@Override
	public List<VirtualDisk> getActiveDisks() {
		List<VirtualDisk> result = new ArrayList<VirtualDisk>();
		for( VirtualDisk vd : baseDisks ) {
			result.add( vd.getActive() );
		}
		return result;
	}

	/**
	 * @param childDisks - All non-base disks found when identifying
	 * all .vmdk files in a VMware directory.  Not necessarily true
	 * that this baseDisk is the parent of any/all of the childDisks.
	 * In fact, when called recursively (see recursive call), baseDisk
	 * need not be a true baseDisk (generation=1) at all.
	 */
	private void link( VirtualDisk baseDisk,
					   List<VMDKDisk> childDisks ) {
		File linkage = baseDisk.getPath();
		try {
			linkage = linkage.getCanonicalFile();
		} catch( IOException ioe ) {
			log.warn( ioe );
			// Any childDisks with baseDisk as parent will be orphaned!
			return;
		}
		for( VMDKDisk vd : childDisks ) {
			/*
			  Due to recursive nature of the link process, the needle
			  and haystack member could be same object.
			*/
			if( vd == baseDisk )
				continue;

			File pfnh = vd.getParentFileNameHint();
			if( pfnh == null )
				continue;
			try {
				pfnh = pfnh.getCanonicalFile();
			} catch( IOException ioe ) {
				log.warn( ioe );
				// vd will be orphaned!
				continue;
			}
			if( linkage.equals( pfnh ) ) {
				baseDisk.setChild( vd );
				vd.setParent( baseDisk );
				// and recursively for the identified child...
				link( vd, childDisks );
				break;
			}
		}
	}

	private final File dir;
	private final List<VirtualDisk> baseDisks;
	private final Log log;

	static public final String FILESUFFIX = ".vmx";

	// A VMware vm dir always seems to have a .vmx file (??)...
	static public final FilenameFilter VMXFILE =
		new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( FILESUFFIX );
			}
		};
}


// eof