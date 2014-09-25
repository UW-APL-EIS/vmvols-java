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

	public void testPackerPlaypen() {
		File dir = new File( "/home/stuart/playpen/packer/" );
		if( !dir.isDirectory() )
			return;
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testMarkers( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void testMarkers( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( f );
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		System.out.println( seh.paramString() );
		StreamOptimizedSparseExtent sose = new StreamOptimizedSparseExtent
			( f, seh );
		List<StreamOptimizedSparseExtent.Marker> ms = sose.getMarkers();
		System.out.println( "Markers: " + ms.size() );
	}
}

// eof
