package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;

import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;
import edu.uw.apl.vmvols.model.Utils;

/**
 * @author Stuart Maclean
 *
 * Presumably some assertion that a virtual disk's contents when
 * hashed under e.g. md5, match some known/expected value.  Not seeing
 * any reference md5 hash?
 */

public class MD5Test extends junit.framework.TestCase {

	public void testNuga2() throws Exception {
		File f = new File( "data/VBox/nuga2/nuga2.vdi" );
		if( !f.exists() )
			return;
		VDIDisk vd1 = VDIDisk.readFrom( f );
		testInputStream( vd1 );
		VDIDisk vd2 = VDIDisk.readFrom( f );
		testRandomAccessVolume( vd2 );
		VDIDisk vd3 = VDIDisk.readFrom( f );
		testSeekRandomAccessVolume( vd3 );
	}

	void testInputStream( VDIDisk d ) throws IOException {
		long start = System.currentTimeMillis();
		InputStream is = d.getInputStream();
		String s = Utils.md5sum( is );
		System.out.println( d.getPath() + " " + s );
		is.close();
		long stop = System.currentTimeMillis();
		System.out.println( "Elapsed (inputstream): " + (stop-start)/1000 );
	}

	void testRandomAccessVolume( VDIDisk d ) throws IOException {
		int[] blockSizes = { 1<<10, 1<<12, 1<<16, 1<<20, 1<<24 };
		for( int blockSize : blockSizes ) {
			testRandomAccessVolume( d, blockSize );
		}
	}
	
	void testRandomAccessVolume( VDIDisk d, int blockSize ) throws IOException {
		long start = System.currentTimeMillis();
		RandomAccessVirtualDisk rav = d.getRandomAccess( false );
		String s = Utils.md5sum( rav, blockSize );
		System.out.println( d.getPath() + " " + blockSize + " " + s );
		rav.close();
		long stop = System.currentTimeMillis();
		System.out.println( "Elapsed (randomaccessvolume): " +
							(stop-start)/1000 );
	}

	void testSeekRandomAccessVolume( VDIDisk d ) throws IOException {
		int[] blockSizes = { 1<<10, 1<<12, 1<<16, 1<<20, 1<<24 };
		for( int blockSize : blockSizes ) {
			testSeekRandomAccessVolume( d, blockSize );
		}
	}
	
	
	void testSeekRandomAccessVolume( VDIDisk d, int blockSize )
		throws IOException {
		long start = System.currentTimeMillis();
		RandomAccessVirtualDisk rav = d.getRandomAccess( false );

		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance( "md5" );
		} catch( Exception e ) {
			// never
		}
		int nin;
		long posn = 0;
		byte[] ba = new byte[blockSize];
		while( (nin = rav.read( ba )) != -1 ) {
			md5.update( ba, 0, nin );
			posn += nin;
			rav.seek( posn );
		}
		byte[] hash = md5.digest();
		String s = Hex.encodeHexString( hash );
		System.out.println( d.getPath() + " " + blockSize + " " + s );
		rav.close();
		long stop = System.currentTimeMillis();
		System.out.println( "Elapsed (seek.randomaccessvolume): " +
							(stop-start)/1000 );
	}
}

// eof
