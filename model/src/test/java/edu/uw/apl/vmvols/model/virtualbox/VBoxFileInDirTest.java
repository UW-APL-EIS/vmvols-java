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
package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;

/**
 * Tests for {@link VBoxVM}
 *
 * @author Stuart Maclean.
 *
 * This test no no longer valid, we have abandoned the method of
 * building a full VM object from one named .vdi file IN a vbox dir.
 * Use {@link edu.uw.apl.vmvols.model.VirtualDisk.create} instead.
 */

public class VBoxFileInDirTest extends junit.framework.TestCase {

	public void testAll() throws Exception {
		File root = new File( "data/VBox" );
		if( !root.isDirectory() )
			return;
		Collection<File> fs = FileUtils.listFiles
			( root, new String[] { VMDKDisk.FILESUFFIX, VDIDisk.FILESUFFIX },
			  true );
		for( File f : fs ) {
			// only testing bona-fide 'file in vbox vm dir' files...
			File dir = f.getParentFile();
			if( !VBoxVM.isVBoxVM( dir ) )
				continue;
			test( dir, f );
		}
	}

	void test( File vboxDir, File diskFile ) throws IOException {
		if( !vboxDir.isDirectory() )
			fail( "Not a directory: " + vboxDir );
		System.err.println( vboxDir );
		VBoxVM vm = new VBoxVM( vboxDir );

		/*
		  Can at least assert that one of the disks of the VM
		  matches the diskFile
		*/
		List<VirtualDisk> vds = vm.getBaseDisks();
		
	}
}

// eof
