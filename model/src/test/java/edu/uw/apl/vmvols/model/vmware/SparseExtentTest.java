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

import java.io.*;
import java.util.*;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.Utils;

/**
   All test data files here must contain a SparseExtent.  All
   monolithicSparse disk types satisy this.
*/

public class SparseExtentTest extends junit.framework.TestCase {
	
	public void testGrainDirectory( SparseExtent se,
									long infileLength ) throws IOException {
		/*
		  long[] gd = se.getGrainDirectory();
		Set<Long> s = new HashSet<Long>();
		for( long gde : gd ) {
			assertTrue( "zero gde ? " + gde, gde != 0 );
			boolean b = s.add( gde );

			assertTrue( "duplicate gde ? " + gde, b );

			assertTrue( "gde too big ? " + gde,
						gde * Constants.SECTORLENGTH < infileLength );
			
		}
		*/
	}

	/*
	  All grain tables entries (save for 0) should be unique across
	  all grain tables in the grain directory
	*/
	public void testGrainTables( SparseExtent se, long infileLength )
		throws IOException {
		/*
		  long[][] gts = se.getGrainTables();
		Set<Long> s = new HashSet<Long>();
		for( long[] gt : gts ) {
			for( long gte : gt ) {
				// a zero gte means 'not allocated' and is ok..
				if( gte == 0 )
					continue;
				boolean aligned = gte % se.header.grainSize == 0;
				assertTrue( "Grain offset not aligned with grain size: " + gte,
							aligned );
				
				boolean isUnique = s.add( gte );
				assertTrue( "duplicate gte ? " + gte, isUnique );

				assertTrue( "gte too big ? " + gte,
							gte * Constants.SECTORLENGTH < infileLength );
							}
							}
		*/
	}

	public void _testCaine() throws IOException {
		File f = new File( "data/vmdk/CaineTester.vmdk" );
		test( f );
	}

	public void _testXP() throws IOException {
		File f = new File( "data/vmdk/XPProfessional.vmdk" );
		test( f );
	}

	public void testSplitSparse() throws IOException {
		File dir = new File( "data/vmware/splitsparse" );
		if( !dir.isDirectory() )
			return;
		File[] fs = dir.listFiles( VMDKDisk.FILEFILTER );
		Arrays.sort( fs );
		for( File f : fs ) {
			test( f );
		}
	}

	public void testMonolithicSparse1() throws IOException {
		File f = new File
			( "data/vmware/monolithicsparse/XPProfessional.vmdk" );
		test( f );
	}

	public void testMonolithicSparse2() throws IOException {
		File f = new File
			( "data/vmware/monolithicsparse/CaineTester.vmdk" );
		test( f );
	}

	public void testMonolithicSparse3() throws IOException {
		File f = new File
			( "/lv1/vmdk/XPProfessional.vmdk" );
		test( f );
	}

	void test( File f ) throws IOException {
		if( !f.exists() )
			return;
		System.out.println( f );
		try {
			SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
			report( seh );
			SparseExtent se = new SparseExtent( f, seh );
			RandomAccessFile raf = new RandomAccessFile( f, "r" );
			long length = raf.length();
			testGrainDirectory( se, length );
			testGrainTables( se, length );
		} catch( IllegalStateException ise ) {
			// this file has no sparse extent header...
		}
	}

	private void report( SparseExtentHeader seh ) {
		System.out.println( "Flags: " + String.format( "%X", seh.flags ) );
		long sz = seh.capacity;
		System.out.println( "Capacity: " + sz + " " +
							Utils.sizeEstimate(sz * Constants.SECTORLENGTH) );
		long gs = seh.grainSize;
		System.out.println( "GrainSize " + gs );
		System.out.println( "Grain Count " + sz / gs );
		System.out.println( "GrainDir Offset " + seh.gdOffset );
		System.out.println( "GrainDir Redundant Offset " + seh.rgdOffset );
		long gdo = seh.grainDirOffset();
		System.out.println( "GrainDir Usable " + gdo );
		long oh = seh.overhead;
		System.out.println( "Overhead " + oh );
		System.out.println( "Descriptor Offset " + seh.descriptorOffset );
		System.out.println( "Descriptor Size " + seh.descriptorSize );
	}
}

// eof
