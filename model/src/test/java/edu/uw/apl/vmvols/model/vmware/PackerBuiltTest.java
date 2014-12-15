package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;

import org.apache.commons.io.FileUtils;
import edu.uw.apl.vmvols.model.VirtualDisk;


/**
 * Tests for {@link MonolithicStreamOptimizedDisk}
 *
 * @author Stuart Maclean
 */
public class PackerBuiltTest extends junit.framework.TestCase {
	
	public void testPackerPlaypen() {
		File dir = new File( "/home/stuart/playpen/packer" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}

	public void testDIMSPacker() {
		File dir = new File( "/home/stuart/apl/projects/infosec/dims/packer" );
		if( !dir.isDirectory() )
			return;
		testDir( dir );
	}

	private void testDir( File dir ) {
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { VMDKDisk.FILESUFFIX }, true );
			//			( dir, new String[] { "vmdk" }, true );
		System.out.println( "Located: " + fs.size() );
		for( File f : fs ) {
			try {
				test( f );
			} catch( Exception e ) {
				e.printStackTrace();
			}
		}
	}
	
	public void test( File f ) throws Exception {
		if( !f.exists() || f.length() == 0 )
			return;
		System.out.println( f );
		VMDKDisk vd = VMDKDisk.readFrom( f );
		report( vd );
		read( vd );
		
	}

	void report( VirtualDisk vd ) {
		System.out.println( "Generation: " + vd.getGeneration() );
		System.out.println( "Create: " + vd.getUUID() );
		System.out.println( "Parent: " + vd.getUUIDParent() );
	}

	void read( VirtualDisk vd ) throws Exception {
		InputStream is = vd.getInputStream();
		byte[] ba = new byte[1024*1024];
		int nin = is.read( ba );
		is.close();
		System.out.println( "Read " + nin );
	}
}

// eof
