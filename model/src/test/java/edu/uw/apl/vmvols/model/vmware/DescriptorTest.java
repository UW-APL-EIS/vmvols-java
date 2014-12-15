package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

/**
 * Tests for {@link Descriptor}
 *
 * @author Stuart Maclean
 */
public class DescriptorTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void _testLocalData() {
		File dir = new File( "data" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}


	public void testPackerPlaypen() {
		File dir = new File( "/home/stuart/playpen/packer" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}

	public void testVagrantBoxes() {
		File dir = new File( "/lv1/vagrant.d/boxes" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}
		
	public void testVBoxHome() {
		File dir = new File( "/home/stuart/VBox" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}
	
	public void testVagrantManagedVM() {
		File dir = new File( "/home/stuart/VBox/vagrant_getting_started_default_1411079646599_11839" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}

	private void testDir( File dir ) {
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				testDescriptor( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void testDescriptor( File f ) throws Exception {
		if( !f.exists() || f.length() == 0 )
			return;
		System.out.println( f );
		Descriptor d = VMDKDisk.locateDescriptor( f );
		System.out.println( d );
		assertTrue( "" + f, d.type != null );
		// We cannot process vmfs files, which don't have ddb.uuid.* fields
		if( d.type.equals( "vmfs" ) )
			return;
		assertTrue( "" + f, d.uuidImage != null );
		assertTrue( "" + f, d.uuidParent != null );
	}
}

// eof
