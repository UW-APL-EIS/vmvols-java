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
import java.util.Set;
import java.util.List;
import java.util.HashSet;

import edu.uw.apl.vmvols.model.Utils;

/**
 * @author Stuart Maclean
 *
 * Tests related to the composition of MonolithicSparseDisks, a variant
 * of .vmdk files.
 *
 * LOOK: We don't appear to be asserting anything??
 */

public class MonolithicSparseDiskTest extends junit.framework.TestCase {

	public void _testCaine() throws IOException {
		File f = new File( "data/vmdk/CaineTester.vmdk" );
		if( !f.exists() )
			return;
		MonolithicSparseDisk msd = (MonolithicSparseDisk)VMDKDisk.readFrom(f);
		InputStream is = msd.getInputStream();
		int bs = (int)msd.contiguousStorage();
		String md5 = Utils.md5sum( is, bs );
		is.close();
	}
	
	public void testMD5() throws IOException {
		File f = new File( "data/vmware/monolithicsparse/XPProfessional.vmdk" );
		if( !f.exists() )
			return;
		System.out.println( f );
		MonolithicSparseDisk msd = (MonolithicSparseDisk)VMDKDisk.readFrom(f);
		InputStream is = msd.getInputStream();
		int bs = (int)msd.contiguousStorage();
		String md5 = Utils.md5sum( is, bs );
		is.close();
		System.out.println( "MD5 " + md5 );
	}

	public void testLocatePartitions() throws Exception {
		File f = new File( "data/vmware/monolithicsparse/XPProfessional.vmdk" );
		if( !f.exists() )
			return;
		System.out.println( f );
		MonolithicSparseDisk msd = (MonolithicSparseDisk)VMDKDisk.readFrom(f);
	}
}

// eof
