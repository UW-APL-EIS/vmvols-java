package edu.uw.apl.vmvols.fuse;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.*;

/**
 * Our VirtualMachineFileSystem class uses regexes to parse/dissect a
 * path into components: vmname, generation, disk. Check here that the
 * regexs defined really do (or do not!) permit typical vmnames as
 * used in e.g. VirtualBox.
 *
 * @see VirtualMachineFileSystem
 */
public class NamingTest extends junit.framework.TestCase {

	public void testLiteral() {
		Matcher m = VirtualMachineFileSystem.NAMEP.matcher( "Win7_64" );
		assertTrue( m.matches() );
	}

	public void testLocalVBox() {
		File dir = new File( "data/VBox" );
		if( !dir.isDirectory() )
			return;
		File[] fs = dir.listFiles( new FileFilter() {
				public boolean accept( File pathName ) {
					return pathName.isDirectory();
				}
			} );
		for( File f : fs ) {
			System.out.println( f );
			Matcher m = VirtualMachineFileSystem.NAMEP.matcher( f.getName() );
			assertTrue( "" + f, m.matches() );
		}
	}
}

// eof
