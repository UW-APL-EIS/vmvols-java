package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.HashSet;

import edu.uw.apl.vmvols.model.Utils;

/**
 * @author Stuart Maclean
 *
 * Tests related to the composition of MonolithicSparseDisks, a variant
 * of .vmdk files.
 *
 * LOOK: We don't appear to be asserting anything??
 */

public class MonolithicSparseDiskTest extends junit.framework.TestCase {

	public void _testCaine() throws IOException {
		File f = new File( "data/vmdk/CaineTester.vmdk" );
		if( !f.exists() )
			return;
		MonolithicSparseDisk msd = (MonolithicSparseDisk)VMDKDisk.readFrom(f);
		InputStream is = msd.getInputStream();
		int bs = (int)msd.contiguousStorage();
		String md5 = Utils.md5sum( is, bs );
		is.close();
	}
	
	public void testMD5() throws IOException {
		File f = new File( "data/vmware/monolithicsparse/XPProfessional.vmdk" );
		if( !f.exists() )
			return;
		System.out.println( f );
		MonolithicSparseDisk msd = (MonolithicSparseDisk)VMDKDisk.readFrom(f);
		InputStream is = msd.getInputStream();
		int bs = (int)msd.contiguousStorage();
		String md5 = Utils.md5sum( is, bs );
		is.close();
		System.out.println( "MD5 " + md5 );
	}

	public void testLocatePartitions() throws Exception {
		File f = new File( "data/vmware/monolithicsparse/XPProfessional.vmdk" );
		if( !f.exists() )
			return;
		System.out.println( f );
		MonolithicSparseDisk msd = (MonolithicSparseDisk)VMDKDisk.readFrom(f);
	}
}

// eof
