package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;
import edu.uw.apl.vmvols.model.vmware.VMwareVM;

/**
 * @author Stuart Maclean
 *
 * Tests for the {@link VirtualMachine.create} API, creating a
 * VirtualMachine given a file system directory.
 */

public class VirtualMachineCreateTest extends junit.framework.TestCase {

	Collection<File> fs;
	
	/*
	  On the Dell Latitude, have a symlink 'data' to our VBox and VMware
	  VM homes
	*/
	protected void setUp() throws Exception {
		File root1 = new File( "data/VBox" );
		if( !root1.isDirectory() )
			return;
		Collection<File> c1 = FileUtils.listFilesAndDirs
			( root1, FalseFileFilter.INSTANCE, TrueFileFilter.INSTANCE );
		File root2 = new File( "data/vmware" );
		if( !root2.isDirectory() )
			return;
		Collection<File> c2 = FileUtils.listFilesAndDirs
			( root2, FalseFileFilter.INSTANCE, TrueFileFilter.INSTANCE );
		fs = new ArrayList<File>();
		fs.addAll( c1 );
		fs.addAll( c2 );
	}

	public void test1() {
		for( File f : fs ) {
			System.out.println( f );
			try {
				if( !( VBoxVM.isVBoxVM( f ) || VMwareVM.isVMwareVM( f ) ) )
					continue;
				VirtualMachine vm = VirtualMachine.create( f );
				System.out.println( vm.getName() );
			} catch( IOException ioe ) {
				continue;
			} catch( IllegalStateException ise ) {
				System.out.println( ise );
				fail();
			}
		}
	}
}

// eof
