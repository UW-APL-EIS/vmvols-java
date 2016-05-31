package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;

/**
 * Tests for {@link VBoxVM}
 *
 * @author Stuart Maclean.
 *
 * This test no no longer valid, we have abandoned the method of
 * building a full VM object from one named .vdi file IN a vbox dir.
 * Use {@link edu.uw.apl.vmvols.model.VirtualDisk.create} instead.
 */

public class VBoxFileInDirTest extends junit.framework.TestCase {

	public void testAll() throws Exception {
		File root = new File( "data/VBox" );
		if( !root.isDirectory() )
			return;
		Collection<File> fs = FileUtils.listFiles
			( root, new String[] { VMDKDisk.FILESUFFIX, VDIDisk.FILESUFFIX },
			  true );
		for( File f : fs ) {
			// only testing bona-fide 'file in vbox vm dir' files...
			File dir = f.getParentFile();
			if( !VBoxVM.isVBoxVM( dir ) )
				continue;
			test( dir, f );
		}
	}

	void test( File vboxDir, File diskFile ) throws IOException {
		if( !vboxDir.isDirectory() )
			fail( "Not a directory: " + vboxDir );
		System.err.println( vboxDir );
		VBoxVM vm = new VBoxVM( vboxDir );

		/*
		  Can at least assert that one of the disks of the VM
		  matches the diskFile
		*/
		List<VirtualDisk> vds = vm.getBaseDisks();
		
	}
}

// eof
