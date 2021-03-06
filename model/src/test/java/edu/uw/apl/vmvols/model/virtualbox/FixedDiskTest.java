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
import java.io.InputStream;
import java.io.IOException;

import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;
import edu.uw.apl.vmvols.model.Utils;

/**
 * @author Stuart Maclean
 *
 * Test cases for class {@link FixedDisk}.  We have prepared some
 * Virtualbox VMs with FixedDisk-created disks, use these in the
 * tests.
 */

public class FixedDiskTest extends junit.framework.TestCase {

	/*
	  On the Dell Latitude laptop, we created a Virtualbox VM with a
	  FixedDisk hard drive, 2GB in size.  We never did anything with
	  that disk, i.e. never proceeded to install any OS, so its entire
	  contents is still zeros.
	*/
	public void testBlank2GB() throws Exception {
		File f = new File( "data/VBox/BlankFixed/BlankFixed.vdi" );
		if( !f.exists() )
			return;
		VDIDisk vd = VDIDisk.readFrom( f );

		// 1: assert expected class
		assertTrue( vd instanceof FixedDisk );
		FixedDisk fd = (FixedDisk)vd;

		// and expected type
		assertEquals( fd.imageType(), VDIDisk.VDI_IMAGE_TYPE_FIXED );
		
		// 2: assert expected size rule holds
		long actualHostFileLen = f.length();
		long expectedHostFileLen = fd.size() + fd.dataOffset();
		assertEquals( actualHostFileLen, expectedHostFileLen );

		/*
		  3: compare md5 of actual content read with that of a
		  reference.  The reference, 2GBs of zeros, was computed thus:

		  $ dd if=/dev/zero bs=1M count=2K | md5sum
		*/
		String expectedContentHash =
			"a981130cf2b7e09f4686dc273cf7187e";
		InputStream is = fd.getInputStream();
		String actualContentHash =
			Utils.md5sum( is );
		is.close();
		assertEquals( expectedContentHash, actualContentHash );

		/*
		  4: Same as 3, but using RandomAccessVirtualDisk api in place
		  of InputStream api.  RandomAccessVirtualDisk extends
		  InputStream, so can still use in in Utils.
		*/
		RandomAccessVirtualDisk ravd = fd.getRandomAccess( false );
		int biggerThanBlock = 1024*1024*16;
		String actualContentHash2 = Utils.md5sum( ravd, biggerThanBlock );
		ravd.close();
		assertEquals( expectedContentHash, actualContentHash2 );

		/*
		  5: skip forward entire virtual disk and read, we should see eof
		*/
		is = fd.getInputStream();
		is.skip( fd.size() );
		byte[] ba = new byte[1024];
		int nin = is.read( ba );
		assertEquals( nin, -1 );
		is.close();

		/*
		  6: As test 5, but use RandomAccessVirtualDisk.seek
		  in place of InputStream.skip.
		*/
		ravd = fd.getRandomAccess( false );
		ravd.seek( fd.size() );
		int nin2 = ravd.read( ba );
		assertEquals( nin2, -1 );
		ravd.close();

		// 7: As 6, but with 3 seeks before the read.
		ravd = fd.getRandomAccess( false );
		ravd.seek( fd.size() );
		ravd.seek( 0L );
		ravd.seek( fd.size() );
		int nin3 = ravd.read( ba );
		assertEquals( nin3, -1 );
		ravd.close();
		
	}
}

// eof
