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
 * @author Stuart Maclean
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
			if( !VBoxVM.isVBox( dir ) )
				continue;
			test( f );
		}
	}

	void test( File vdFile ) throws IOException {
		if( vdFile.isDirectory() )
			fail( "Not a file: " + vdFile );
		System.err.println( vdFile );
		VBoxVM vm = new VBoxVM( vdFile );
		List<VirtualDisk> base = vm.getBaseDisks();
		assertEquals( base.size(), 1 );
		
													   
	}
}

// eof
