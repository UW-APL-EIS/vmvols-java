package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

import edu.uw.apl.vmvols.model.Utils;

public class SparseExtentReadTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testVirtualBoxes() throws Exception {
		File dir = new File( "/home/stuart/VBox/" );
		if( !dir.isDirectory() )
			return;
		testRoot( dir );
	}
	
	private void testRoot( File dir ) throws IOException {	
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			// Skip the 'descriptor only' variant of .vmdk
			if( f.length() < 1024 )
				continue;
			try {
				test( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void test( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( f );
		VMDKDisk d = VMDKDisk.readFrom( f );
		if( !( d instanceof MonolithicSparseDisk ) )
			return;
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		System.out.println( seh.paramString() );
		InputStream is = d.getInputStream();
		String md5 = Utils.md5sum( is );
		is.close();
		System.out.println( md5 );
	}
}

// eof
