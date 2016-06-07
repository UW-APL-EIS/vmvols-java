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
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

/**
 * Tests for {@link Descriptor}
 *
 * @author Stuart Maclean
 */
public class DescriptorTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void _testLocalData() {
		File dir = new File( "data" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}


	public void testPackerPlaypen() {
		File dir = new File( "/home/stuart/playpen/packer" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}

	public void testVagrantBoxes() {
		File dir = new File( "/lv1/vagrant.d/boxes" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}
		
	public void testVBoxHome() {
		File dir = new File( "/home/stuart/VBox" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}
	
	public void testVagrantManagedVM() {
		File dir = new File( "/home/stuart/VBox/vagrant_getting_started_default_1411079646599_11839" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}

	private void testDir( File dir ) {
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testDescriptor( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void testDescriptor( File f ) throws Exception {
		if( !f.exists() || f.length() == 0 )
			return;
		System.out.println( f );
		Descriptor d = VMDKDisk.locateDescriptor( f );
		System.out.println( d );
		assertTrue( "" + f, d.type != null );
		// We cannot process vmfs files, which don't have ddb.uuid.* fields
		if( d.type.equals( "vmfs" ) )
			return;
		assertTrue( "" + f, d.uuidImage != null );
		assertTrue( "" + f, d.uuidParent != null );
	}
}

// eof
