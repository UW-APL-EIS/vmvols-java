package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;

/**
 * Tests for {@link VBoxVM}
 *
 * @author Stuart Maclean
 *
 * LOOK: We are not asserting anything here.  Valid test case??
 */

public class VBoxVMTest extends junit.framework.TestCase {

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
		System.out.println( dir );
		if( !VBoxVM.isVBoxVM( dir ) )
			return;
		VBoxVM vm = new VBoxVM( dir );
		report( vm );
	}

	void report( VBoxVM vm ) {
		System.out.println( "Name: " + vm.getName() );
		System.out.println( "Base: " + vm.getBaseDisks() );
		System.out.println( "Active: " + vm.getActiveDisks() );

		System.out.println( "ActiveDisks:" );
		for( VirtualDisk vd : vm.getActiveDisks() ) {
			report( vd );
			/*
			  for( VirtualDisk an : vdi.getAncestors() ) {
				VDIDisk vdian = (VDIDisk)an;
				report( vdian );
			}
			*/
		}

		System.out.println( "BaseDisks:" );
		for( VirtualDisk vd : vm.getBaseDisks() ) {
			report( vd );
			/*
			  for( VirtualDisk an : vdi.getAncestors() ) {
				VDIDisk vdian = (VDIDisk)an;
				report( vdian );
			}
			*/
		}
	}

	void report( VirtualDisk vd ) {
		System.out.println( "Generation: " + vd.getGeneration() );
		System.out.println( "Create: " + vd.getUUID() );
		System.out.println( "Parent: " + vd.getUUIDParent() );
	}
}

// eof
