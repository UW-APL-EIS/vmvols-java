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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;
import edu.uw.apl.vmvols.model.vmware.VMwareVM;

/**
 * @author Stuart Maclean
 *
 * Tests for the {@link VirtualMachine.create} API, creating a
 * VirtualMachine given a file system directory.
 */

public class VirtualMachineCreateTest extends junit.framework.TestCase {

	Collection<File> fs;
	
	/*
	  On the Dell Latitude, have a symlink 'data' to our VBox and VMware
	  VM homes
	*/
	protected void setUp() throws Exception {
		File root1 = new File( "data/VBox" );
		if( !root1.isDirectory() )
			return;
		Collection<File> c1 = FileUtils.listFilesAndDirs
			( root1, FalseFileFilter.INSTANCE, TrueFileFilter.INSTANCE );
		File root2 = new File( "data/vmware" );
		if( !root2.isDirectory() )
			return;
		Collection<File> c2 = FileUtils.listFilesAndDirs
			( root2, FalseFileFilter.INSTANCE, TrueFileFilter.INSTANCE );
		fs = new ArrayList<File>();
		fs.addAll( c1 );
		fs.addAll( c2 );
	}

	public void test1() {
		for( File f : fs ) {
			System.out.println( f );
			try {
				if( !( VBoxVM.isVBoxVM( f ) || VMwareVM.isVMwareVM( f ) ) )
					continue;
				VirtualMachine vm = VirtualMachine.create( f );
				System.out.println( vm.getName() );
			} catch( IOException ioe ) {
				continue;
			} catch( IllegalStateException ise ) {
				System.out.println( ise );
				fail();
			}
		}
	}
}

// eof
