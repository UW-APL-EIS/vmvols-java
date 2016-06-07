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
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;

/**
 * @author Stuart Maclean
 *
 * Test various properties of any VirtualBox vm with disks having many
 * (2+) generations, i.e. at least one snapshot taken.
 */

public class VBoxMultiGenerationTest extends junit.framework.TestCase {
	
	public void testAll() throws Exception {
		File root = new File( "data/VBox" );
		if( !root.isDirectory() )
			return;
		File[] fs = root.listFiles( new FileFilter() {
				public boolean accept( File pathName ) {
					return pathName.isDirectory();
				}
			} );
		Arrays.sort( fs );
		for( File f : fs ) {
			test( f );
		}
	}

	void test( File dir ) throws IOException {
		if( !VBoxVM.isVBoxVM( dir ) )
			return;
		VBoxVM vm = new VBoxVM( dir );
		test( vm );
	}
	
	void test( VBoxVM vm ) {
		System.out.println( "Name: " + vm.getName() );
		System.out.println( "Active: " + vm.getActiveDisks() );
		for( VirtualDisk vd : vm.getActiveDisks() ) {
			report( vd );
		}
	}

	void report( VirtualDisk vd ) {
		System.out.println( "Generation: " + vd.getGeneration() );
		//		System.out.println( "Create: " + vdi.imageCreationUUID() );
		//System.out.println( "Parent: " + vdi.imageParentUUID() );
		String id = vd.getID();
		System.out.println( "id " + id );

		int g = vd.getGeneration();
		if( g > 1 ) {
			VirtualDisk p = vd.getGeneration( g-1 );
			assertNotSame( p.getPath(), vd.getPath() );
		}
	}
}

// eof
