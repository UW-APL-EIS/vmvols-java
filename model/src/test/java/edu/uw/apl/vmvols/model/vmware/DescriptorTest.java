package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;

public class DescriptorTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testLocalData() {
		File dir = new File( "data" );
		if( !dir.isDirectory() )
			return;
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

	public void testPackerPlaypen() {
		File dir = new File( "/home/stuart/playpen/packer" );
		if( !dir.isDirectory() )
			return;
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
	
	public void _testVBoxHome() {
		File dir = new File( "/home/stuart/VBox" );
		if( !dir.isDirectory() )
			return;
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
	
	public void testVagrantManagedVM() {
		File dir = new File( "/home/stuart/VBox/vagrant_getting_started_default_1411079646599_11839" );
		if( !dir.isDirectory() )
			return;
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
		if( !f.exists() )
			return;
		System.out.println( f );
		Descriptor d = VMDKDisk.locateDescriptor( f );
		System.out.println( d );
	}
}

// eof
