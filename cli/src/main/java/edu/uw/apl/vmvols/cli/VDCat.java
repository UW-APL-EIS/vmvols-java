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
package edu.uw.apl.vmvols.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.*;
import org.apache.log4j.LogManager;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;

/**
 * @author Stuart Maclean
 *
 * Open the identified virtual disk file, supplied on args[0], and
 * stream its entire <em>logical</em> contents to stdout.  By logical,
 * we mean the disk content as seen by the VM were it to be powered
 * up.  This implies that the content streamed is the latest
 * 'Snapshot' were the disk to have any Snapshots.
 *
 * The file name as passed on args[0] can be either the directory of a
 * VM or the name of a <b>base</b> virtual disk name, NOT a Snapshot
 * file.  We automatically then locate the <b>active</b> file from the
 * base one.

 * We do NOT mean the physical content of actual file on disk on the
 * host (for which plain cat would suffice).  Hence the name 'vdcat',
 * mimics the Unix tool cat.
 *
 * For a VirtualBox disk image file:
 *
 * $ VDCat /path/to/some/basefile.vdi
 *
 * For a VMWare disk image file:
 *
 * $ VDCat /path/to/some/basefile.vmdk
 *
 * For a VM directory rather than an identified file:
 *
 * $ VDCat /path/to/some/vmdir
 *
 * This last invocation will only work if the VM has a single hard
 * drive, else command is ambiguous, and an individual virtual disk
 * file name should be supplied instead.  In general, most VMs likely
 * have only a single hard drive, so this ambiguity is rare.
 *
 * All the invocations above have the same effect as powering up the
 * VM and doing something akin to 'cat /dev/sda' from within that VM.
 * Note that this is disk-level access, i.e. from first sector of
 * disk.  It is not file-system level access.
 */

public class VDCat {

	static public void main( String[] args ) {

		final String usage = "Usage: " + VDCat.class.getName() +
			" (virtualDiskFile | virtualMachineDirectory)";
		if( args.length < 1 ) {
			System.err.println( usage );
			System.exit(1);
		}

		File f = new File( args[0] );
		if( !f.exists() ) {
			System.err.println( f + ": no such file or directory" );
			System.exit(1);
		}

		try {
			VirtualMachine vm = VirtualMachine.create( f );
			if( vm == null )
				throw new IllegalArgumentException( "Cannot build vm from " +
													f );
			List<VirtualDisk> disks = vm.getBaseDisks();
			VirtualDisk vd = null;
			if( disks.size() == 1 ) {
				vd = disks.get(0);
			} else {
				if( f.isDirectory() ) {
					List<File> fs = new ArrayList<File>();
					for( VirtualDisk el : disks )
						fs.add( el.getPath() );
					throw new IllegalArgumentException
						( "VM created from " + f + " has " + disks.size() +
						  " disks (" + fs + "), must specify one." );
				}
				// One of these MUST succeed, so vd cannot be left null
				for( int i = 0; i < disks.size(); i++ ) {
					VirtualDisk el = disks.get(i);
					if( el.getPath().equals( f ) ) {
						vd = el;
						break;
					}
				}
			}

			/*
			  Thus far been working in base disk space,
			  i.e. generation 1 of any disk associated with the VM.
			  What we are actually going to cat is the ACTIVE disk
			  content...
			*/
			vd = vd.getActive();
			
			InputStream is = vd.getInputStream();

			byte[] ba = new byte[1024*1024];
			while( true ) {
				int nin = is.read( ba );
				if( nin < 1 )
					break;
				System.out.write( ba, 0, nin );
			}
			is.close();
		} catch( Exception e ) {
			System.err.println( e );
		}
	}
}

// eof
