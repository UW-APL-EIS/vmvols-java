package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;

/**
 * @author Stuart Maclean
 *
 */

public class VMwareMultiGenerationTest extends junit.framework.TestCase {
	
	public void testAll() throws Exception {
		File root = new File( "data/vmware" );
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
		if( !VMwareVM.isVMwareVM( dir ) )
			return;
		VMwareVM vm = new VMwareVM( dir );
		test( vm );
	}
	
	void test( VMwareVM vm ) {
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
		System.out.println( "path " + vd.getPath() );

		int g = vd.getGeneration();
		if( g > 1 ) {
			List<VirtualDisk> ancs = vd.getAncestors();
			System.out.println( "ancestors " + ancs.size() );
			
			VirtualDisk p = vd.getGeneration( g-1 );
			assertNotSame( p.getPath(), vd.getPath() );
			report( vd.getGeneration(g-1) );
		}
	}
}

// eof
