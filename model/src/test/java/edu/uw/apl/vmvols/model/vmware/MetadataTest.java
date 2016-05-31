package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * @author Stuart Maclean
 *
 * Tests related to the metadata (header, grain directory,tables)
 * found inside all VMWare .vmdk files.
 *
 * LOOK: We don't appear to be asserting anything??
 */
public class MetadataTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testPackerPlaypen() throws Exception {
		File dir = new File( "/home/stuart/playpen/packer" );
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
	
	public void testLocalData() throws Exception {
		File dir = new File( "./data/vmware/" );
		if( !dir.isDirectory() )
			return;
		testRoot( dir );
	}
	
	private void testRoot( File dir ) throws IOException {	
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { VMDKDisk.FILESUFFIX }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				test( f );
			} catch( Exception e ) {
				e.printStackTrace();
				fail();
			}
		}
	}
	
	public void test( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( f );
		SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
		System.out.println( "Header: " + seh.paramString() );
		/*
		  In a true streamoptimizedsparseextent, the gdOffset is 0 in the
		  header, indicating that the footer should be used.
		*/
		if( seh.grainDirOffset() != -1 )
			return;
		StreamOptimizedSparseExtent sose = new StreamOptimizedSparseExtent
			( f, seh );
		SparseExtentHeader footer = sose.locateSparseExtentFooter();
		System.out.println( "Footer: " + footer.paramString() );
		sose.readMetaData();
		long[][] gd = sose.getGrainDirectory();
		reportGrainDirectory( gd, f );
	}

	private void reportGrainDirectory( long[][] gd, File f ) {
		System.out.println( f + " " + gd.length );
	}
}

// eof
