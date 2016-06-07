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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * @author Stuart Maclean
 *
 * Tests for {@link VDIDisk}. Check the integrity of the block map
 * data structure in any vdi files we can locate.
 *
 * LOOK: We are not asserting anything.  Is this a valid test case?
 */

public class BlockMapTest extends junit.framework.TestCase {

	protected void setUp() {
		vdis = new ArrayList<File>();
		
		// VirtualBox vms on rejewski.apl
		File dir1 = new File( "/lv1/home/stuart/VBox" );
		if( dir1.isDirectory() ) {
			Collection<File> fs = FileUtils.listFiles
			( dir1, new String[] { VDIDisk.FILESUFFIX }, true );
			vdis.addAll( fs );
		}

		// VirtualBox vms on Dell Laptop
		File dir2 = new File( "/home/stuart/VBox" );
		if( dir2.isDirectory() ) {
			Collection<File> fs = FileUtils.listFiles
			( dir2, new String[] { VDIDisk.FILESUFFIX }, true );
			vdis.addAll( fs );
		}
		

		System.out.println( "Located: " + vdis.size() );
	}
	
	public void testAll() throws Exception {
		for( File f : vdis ) {
			try {
				testBlockMap( f );
			} catch( Exception e ) {
				System.err.println( e );
			}
		}
	}

	void testBlockMap( File vdiFile ) throws IOException {
		System.out.println( vdiFile );
		try {
			VDIDisk vd = VDIDisk.readFrom( vdiFile );
			report( vd );
			vd.readBlockMap();
		} catch( VDIMissingSignatureException se ) {
			/*
			  OK, likely some 'saved while powered up' .vdi file
			  as used by Cuckoo Sandbox.  Such files do NOT follow
			  the regular rules for vdi layout.
			*/
		}
	}

	void report( VDIDisk vdi ) {
		System.out.println( "Type: " + vdi.imageType() );
		System.out.println( "BlockSize: " + vdi.blockSize() );
		//	System.out.println( "Generation: " + vdi.getGeneration() );
		//		System.out.println( "Create: " + vdi.imageCreationUUID() );
		//System.out.println( "Parent: " + vdi.imageParentUUID() );
	}

	private List<File> vdis;
}

// eof
