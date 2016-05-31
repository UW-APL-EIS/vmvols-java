package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;

/**
 * @author Stuart Maclean
 *
 * Test various properties of any VirtualBox vm with disks having many
 * (2+) generations, i.e. at least one snapshot taken.
 */

public class VBoxMultiGenerationTest extends junit.framework.TestCase {
	
	public void testAll() throws Exception {
		File root = new File( "data/VBox" );
		if( !root.isDirectory() )
			return;
		File[] fs = root.listFiles( new FileFilter() {
				public boolean accept( File pathName ) {
					return pathName.isDirectory();
				}
			} );
		Arrays.sort( fs );
		for( File f : fs ) {
			test( f );
		}
	}

	void test( File dir ) throws IOException {
		if( !VBoxVM.isVBoxVM( dir ) )
			return;
		VBoxVM vm = new VBoxVM( dir );
		test( vm );
	}
	
	void test( VBoxVM vm ) {
		System.out.println( "Name: " + vm.getName() );
		System.out.println( "Active: " + vm.getActiveDisks() );
		for( VirtualDisk vd : vm.getActiveDisks() ) {
			report( vd );
		}
	}

	void report( VirtualDisk vd ) {
		System.out.println( "Generation: " + vd.getGeneration() );
		//		System.out.println( "Create: " + vdi.imageCreationUUID() );
		//System.out.println( "Parent: " + vdi.imageParentUUID() );
		String id = vd.getID();
		System.out.println( "id " + id );

		int g = vd.getGeneration();
		if( g > 1 ) {
			VirtualDisk p = vd.getGeneration( g-1 );
			assertNotSame( p.getPath(), vd.getPath() );
		}
	}
}

// eof
