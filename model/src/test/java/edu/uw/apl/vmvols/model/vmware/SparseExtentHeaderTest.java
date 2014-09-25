package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

public class SparseExtentHeaderTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testLocalData() {
		File dir = new File( "data" );
		if( !dir.isDirectory() )
			return;
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testSparseExtentHeader( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}

	public void testPackerPlaypen() {
		File dir = new File( "/home/stuart/playpen/packer" );
		if( !dir.isDirectory() )
			return;
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testSparseExtentHeader( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void testSparseExtentHeader( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( f );
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		System.out.println( seh.paramString() );
	}
}

// eof
