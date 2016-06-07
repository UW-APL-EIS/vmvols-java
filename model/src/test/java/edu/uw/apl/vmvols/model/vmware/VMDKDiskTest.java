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

public class VMDKDiskTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testNull() {
	}
	
	public void testConstructor( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( f );
		VMDKDisk vd = null;//new VMDKDisk( f );
	}

	/*
	public void testDescriptor( VMDKDisk vd ) throws Exception {
		vd.readDescriptor();
		String d = vd.getDescriptor();
		System.out.println( d );
	}
	*/

	/*
	public void testGrainDirectory( VMDKDisk d ) throws Exception {
		long[] gd = d.getGrainDirectory();
		Set<Long> s = new HashSet<Long>();
		for( long gde : gd ) {
			assertTrue( "zero gde ? " + gde, gde != 0 );
			boolean b = s.add( gde );
			assertTrue( "duplicate gde ? " + gde, b );
		}
	}
	*/
	
	/*
	  All grain tables entries (save for 0) should be unique across
	  all grain tables in the grain directory
	*/
	/*
	  public void testGrainTables( VMDKDisk d ) throws Exception {
		long[][] gts = d.getGrainTables();
		Set<Long> s = new HashSet<Long>();
		for( long[] gt : gts ) {
			for( long gte : gt ) {
				// a zero gte means 'not allocated' and is ok..
				if( gte == 0 )
					continue;
				boolean isUnique = s.add( gte );
				assertTrue( "duplicate gte ? " + gte, isUnique );
			}
		}
	}
	*/
	
	/*
	  public void testMD5( VMDKDisk d, String[] expecteds ) throws Exception {
		InputStream is = d.getInputStream();
		long block = d.contiguousStorage();
		String actual = Utils.md5sum( is, (int)(2*block) );
		is.close();
		System.out.println( d.getPath() + " " + actual );
		assertEquals( "md5 mismatch?", expecteds[0], actual );

		List<Partition> ps = d.getPartitions();
		for( Partition p : ps ) {
			is = p.getInputStream();
			actual = Utils.md5sum( is );
			is.close();
			System.out.println( p.getPath() + " " + actual );
			assertEquals( "md5 mismatch?", expecteds[p.index()], actual );
		}
	}
	*/
	
}

// eof
