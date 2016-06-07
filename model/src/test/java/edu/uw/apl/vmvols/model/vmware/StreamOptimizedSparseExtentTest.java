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
import java.util.List;

import org.apache.commons.io.FileUtils;

public class StreamOptimizedSparseExtentTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testPackerPlaypen() throws Exception {
		File dir = new File( "/home/stuart/playpen/packer/ovfs" );
		if( !dir.isDirectory() )
			return;
		testRoot( dir );
	}

	public void testVagrantBoxes() throws Exception {
		File dir = new File( "/lv1/vagrant.d/boxes/" );
		if( !dir.isDirectory() )
			return;
		testRoot( dir );
	}
	
	private void testRoot( File dir ) throws IOException {	
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testFooter( f );
				//		testMarkers( f );
				//				testReadMetaData( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void testMarkers( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( "testMarkers " + f );
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		System.out.println( seh.paramString() );
		/*
		  In a true streamoptimizedsparseextent, the gdOffset is 0 in the
		  header, indicating that the footer should be used.
		*/
		if( seh.grainDirOffset() != 0 )
			return;
		StreamOptimizedSparseExtent sose = new StreamOptimizedSparseExtent
			( f, seh );
		/*
		  List<StreamOptimizedSparseExtent.Marker> ms = sose.getAllMarkers();
		System.out.println( "Markers: " + ms.size() );

		List<StreamOptimizedSparseExtent.GrainMarker> gms = sose.getGrainMarkers();
		System.out.println( "GrainMarkers: " + gms.size() );

		List<StreamOptimizedSparseExtent.MetadataMarker> mdms =
			sose.getMetadataMarkers();
		System.out.println( "MetadataMarkers: " + mdms.size() );
		*/
	}

	public void testFooter( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( "testFooter " + f );
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		System.out.println( "Header: " + seh.paramString() );
		StreamOptimizedSparseExtent sose = new StreamOptimizedSparseExtent
			( f, seh );
		SparseExtentHeader footer = sose.locateSparseExtentFooter();
		System.out.println( "Footer: " + footer.paramString() );

		/*
		  List<StreamOptimizedSparseExtent.MetadataMarker> mdms =
			sose.getMetadataMarkers();
		for( StreamOptimizedSparseExtent.MetadataMarker mdm : mdms ) {
			if( mdm.type == StreamOptimizedSparseExtent.MetadataMarker.TYPE_GD )
				System.out.println( mdm.paramString() );
		}
		*/
	}

	public void testReadMetaData( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( "testReadMetaData " + f );
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		//System.out.println( "Header: " + seh.paramString() );
		StreamOptimizedSparseExtent sose = new StreamOptimizedSparseExtent
			( f, seh );
		SparseExtentHeader sef = sose.locateSparseExtentFooter();
		System.out.println( "Footer: " + sef.paramString() );

		//		sose.readGrainDirectory();
		//		System.out.println( sose.grainDirectory.paramString() );
		
		//sose.readGrainTables();
	}
}

// eof
