package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;

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
		if( !VBoxVM.isVBox( dir ) )
			return;
		VBoxVM vm = new VBoxVM( dir );
		report( vm );
	}

	void report( VBoxVM vm ) {
		System.out.println( "Name: " + vm.getName() );
		System.out.println( "Base: " + vm.getBaseDisks() );
		System.out.println( "Active: " + vm.getActiveDisks() );
		for( VirtualDisk vd : vm.getActiveDisks() ) {
			VDIDisk vdi = (VDIDisk)vd;
			report( vdi );
			for( VirtualDisk an : vdi.getAncestors() ) {
				VDIDisk vdian = (VDIDisk)an;
				report( vdian );
			}
		}
	}

	void report( VDIDisk vdi ) {
		System.out.println( "Generation: " + vdi.getGeneration() );
		System.out.println( "Create: " + vdi.imageCreationUUID() );
		System.out.println( "Parent: " + vdi.imageParentUUID() );
	}
}

// eof
