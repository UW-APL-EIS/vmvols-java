package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

/**
 * @author Stuart Maclean
 *
 * Tests for {@link VDIDisk}. Check the integrity of the block map
 * data structure in any vdi files we can locate.
 *
 * LOOK: We are not asserting anything.  Is this a valid test case?
 */

public class BlockMapTest extends junit.framework.TestCase {

	protected void setUp() {
		vdis = new ArrayList<File>();
		
		// VirtualBox vms on rejewski.apl
		File dir1 = new File( "/lv1/home/stuart/VBox" );
		if( dir1.isDirectory() ) {
			Collection<File> fs = FileUtils.listFiles
			( dir1, new String[] { VDIDisk.FILESUFFIX }, true );
			vdis.addAll( fs );
		}

		// VirtualBox vms on Dell Laptop
		File dir2 = new File( "/home/stuart/VBox" );
		if( dir2.isDirectory() ) {
			Collection<File> fs = FileUtils.listFiles
			( dir2, new String[] { VDIDisk.FILESUFFIX }, true );
			vdis.addAll( fs );
		}
		

		System.out.println( "Located: " + vdis.size() );
	}
	
	public void testAll() throws Exception {
		for( File f : vdis ) {
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
		//		System.out.println( "Create: " + vdi.imageCreationUUID() );
		//System.out.println( "Parent: " + vdi.imageParentUUID() );
	}

	private List<File> vdis;
}

// eof
