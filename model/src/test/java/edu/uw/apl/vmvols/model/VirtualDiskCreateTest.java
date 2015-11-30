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

	public void testCreateBase() throws IOException {
		System.out.println( "testCreateBase" );
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f, VirtualDisk.BASE );
			System.out.println( vd.getGeneration() );
			assertEquals( vd.getGeneration(), VirtualDisk.BASE );
			VirtualDisk base = vd.getBase();
			assertTrue( vd == base );
			
		}
	}

	public void _testCreateActive() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			VirtualDisk vd = VirtualDisk.create( f );
			VirtualDisk active = vd.getActive();
			assertTrue( vd == active );
		}
	}

	public void _testCreateUnknownGeneration() throws IOException {
		for( File f : fs ) {
			System.out.println( f );
			try {
				VirtualDisk vd = VirtualDisk.create( f, 55 );
				fail();
			} catch( IllegalArgumentException noSuchGeneration ) {
			}
		}
	}

	public void _testCreateFromVBoxDir() throws IOException {
		File root = new File( "data/VBox" );
		if( !root.isDirectory() )
			return;
		File[] fs = root.listFiles( (FileFilter)DirectoryFileFilter.INSTANCE );
		for( File f : fs ) {

			// Have some special files like 'Shared' with are NOT vm dirs..
			if( !VBoxVM.isVBoxVM( f ) )
				continue;
			
			VBoxVM vm = new VBoxVM( f );

			// if VM has 2+ disks, creating a file from the dir should fail
			if( vm.getBaseDisks().size() > 1 ) {
				try {
					VirtualDisk vd = VirtualDisk.create( f );
					fail();
				} catch( IllegalArgumentException expected ) {
				}
			} else {
				// with 1 base disk only, expect to create a disk from dir file
				VirtualDisk vd = VirtualDisk.create( f );
			}
		}
	}

			
}

// eof
