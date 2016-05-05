package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import edu.uw.apl.vmvols.model.virtualbox.VDIDisk;
import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;

/**
 * @author Stuart Maclean
 *
 * Tests for the {@link VirtualDisk.create} API, creating a
 * VirtualDisk without needing to mention VirtualMachine at all.  We
 * test both using the default 'create' method, which requires no
 * explicit generation, and the version with generation supplied.
 */

public class VirtualDiskCreateTest extends junit.framework.TestCase {

	Collection<File> fs;
	
	/*
	  On the Dell Latitude, have a symlink 'data' to our VBox VM home
	*/
	protected void setUp() throws Exception {
		File root = new File( "data" );
		if( !root.isDirectory() )
			return;
		fs = FileUtils.listFiles
			( root, new String[] { VMDKDisk.FILESUFFIX, VDIDisk.FILESUFFIX },
			  true );
		//		System.out.println( fs );
	}

	public void testCreateSelf() throws IOException {
		System.out.println( "testCreateSelf" );
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f );
			File source = vd.getPath();
			assertTrue( f.getCanonicalFile().equals
						( source.getCanonicalFile() ) );
		}
	}

	public void testCreateBase() throws IOException {
		System.out.println( "testCreateBase" );
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f, VirtualDisk.BASE );
			assertEquals( vd.getGeneration(), VirtualDisk.BASE );
			VirtualDisk base = vd.getBase();
			assertTrue( vd == base );
			
		}
	}

	public void testCreateActive() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f, VirtualDisk.ACTIVE );
			VirtualDisk active = vd.getActive();
			assertTrue( vd == active );
		}
	}

	public void testCreateUnknownGeneration() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			try {
				VirtualDisk vd = VirtualDisk.create( f, 55 );
				fail( "" + f );
			} catch( NoSuchGenerationException nsge ) {
			}
		}
	}
			
}

// eof
