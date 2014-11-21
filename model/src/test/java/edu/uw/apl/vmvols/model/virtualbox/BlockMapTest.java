package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * Check the integrity of the block map data structure in any vdi
 * files we can locate.
 */
public class BlockMapTest extends junit.framework.TestCase {
	
	public void testAll() throws Exception {
		File dir = new File( "/lv1/home/stuart/VBox" );
		if( !dir.isDirectory() )
			return;
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vdi" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testBlockMap( f );
			} catch( Exception e ) {
				System.err.println( e );
			}
		}
	}

	void testBlockMap( File vdiFile ) throws IOException {
		System.out.println( vdiFile );
		try {
			VDIDisk vd = VDIDisk.readFrom( vdiFile );
			report( vd );
			vd.readBlockMap();
		} catch( VDIMissingSignatureException se ) {
			/*
			  OK, likely some 'saved while powered up' .vdi file
			  as used by Cuckoo Sandbox.  Such files do NOT follow
			  the regular rules for vdi layout.
			*/
		}
	}

	void report( VDIDisk vdi ) {
		System.out.println( "Type: " + vdi.imageType() );
		System.out.println( "BlockSize: " + vdi.blockSize() );
		//	System.out.println( "Generation: " + vdi.getGeneration() );
		System.out.println( "Create: " + vdi.imageCreationUUID() );
		System.out.println( "Parent: " + vdi.imageParentUUID() );
	}
}

// eof
