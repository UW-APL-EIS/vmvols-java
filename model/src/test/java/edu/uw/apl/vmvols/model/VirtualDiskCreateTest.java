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
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import edu.uw.apl.vmvols.model.virtualbox.VDIDisk;
import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;

/**
 * @author Stuart Maclean
 *
 * Tests for the {@link VirtualDisk.create} API, creating a
 * VirtualDisk without needing to mention VirtualMachine at all.  We
 * test both using the default 'create' method, which requires no
 * explicit generation, and the version with generation supplied.
 */

public class VirtualDiskCreateTest extends junit.framework.TestCase {

	Collection<File> fs = new ArrayList();
	
	/*
	  On the Dell Latitude, have a symlink 'data' to our VBox VM home
	*/
	protected void setUp() throws Exception {

		String[] suffices = { VMDKDisk.FILESUFFIX, VDIDisk.FILESUFFIX };
		File root = new File( "data" );
		if( root.isDirectory() ) {
			Collection<File> vmControlled = FileUtils.listFiles
				( root, suffices, true );
			fs.addAll( vmControlled );
		}
		// Also add any standalone files, e.g. from .ova, packer
		root = new File( "/home/stuart/playpen/virtualization" );
		if( root.isDirectory() ) {
			Collection<File> standalone = FileUtils.listFiles
				( root, suffices, true );
			fs.addAll( standalone );
		}

		root = new File( "/home/stuart/apl/projects/infosec/packer-vms" );
		if( root.isDirectory() ) {
			Collection<File> standalone = FileUtils.listFiles
				( root, suffices, true );
			fs.addAll( standalone );
		}

		System.out.println( fs );
		
			
	}

	public void testCreateSelf() throws IOException {
		System.out.println( "testCreateSelf" );
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f );
			File source = vd.getPath();
			assertTrue( f.getCanonicalFile().equals
						( source.getCanonicalFile() ) );
		}
	}

	public void testCreateBase() throws IOException {
		System.out.println( "testCreateBase" );
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f, VirtualDisk.BASE );
			assertEquals( vd.getGeneration(), VirtualDisk.BASE );
			VirtualDisk base = vd.getBase();
			assertTrue( vd == base );
			
		}
	}

	public void testCreateActive() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f, VirtualDisk.ACTIVE );
			VirtualDisk active = vd.getActive();
			assertTrue( vd == active );
		}
	}

	public void testCreateUnknownGeneration() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			try {
				VirtualDisk vd = VirtualDisk.create( f, 555 );
				fail( "" + f );
			} catch( NoSuchGenerationException nsge ) {
			}
		}
	}

	// Ensure a VD always has its VM member set...
	public void testHasVirtualMachine() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			try {
				VirtualDisk vd = VirtualDisk.create( f );
				VirtualMachine vm = vd.getVirtualMachine();
				assertFalse( vm == null );
			} catch( NoSuchGenerationException nsge ) {
			}
		}
	}
			
}

// eof
