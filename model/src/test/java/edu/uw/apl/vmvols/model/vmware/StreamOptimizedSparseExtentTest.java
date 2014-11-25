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
