package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.security.MessageDigest;

import org.apache.commons.codec.binary.Hex;

public class Utils {

	/**
	   Mimic DataInput.readFully, avoiding the use of a
	   DataInputStream wrapper itself.  Follows the contract of
	   DataInput.readFully (especially the throwing of EOFException on
	   incomplete read)

	   @see DataInput.readFully
	*/
	static public void readFully( InputStream is, byte[] b )
		throws IOException {
		int total = 0;
		while( total < b.length ) {
			int nin = is.read( b, total, b.length - total );
			if( nin == -1 )
				throw new EOFException();
			total += nin;
		}
	}

	/**
	   The contract of InputStream.skip( long n ) is such that the
	   actual skip count can be < n.  So wrap it in a loop...
	*/
	static public long skipFully( InputStream is, long skip )
		throws IOException {
		long total = 0;
		while( total < skip ) {
			long n = is.skip( skip-total );
			total += n;
		}
		return total;
	}

	/**
	 * @result count of bytes read from stream until eof reached
	 */
	static public long size( InputStream is ) throws IOException {
		return size( is, 1024*1024 );
	}
	
	static public long size( InputStream is, int blockSize )
		throws IOException {
		long result = 0;
		int nin;
		byte[] ba = new byte[blockSize];
		while( (nin = is.read( ba )) != -1 ) {
			result += nin;
			//			System.out.println( nin + " " + result );
		}
		return result;
	}

	static public String md5sum( InputStream is ) throws IOException {
		return md5sum( is, 1024*1024 );
	}
	
	static public String md5sum( InputStream is, int blockSize )
		throws IOException {
		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance( "md5" );
		} catch( Exception e ) {
			// never
		}
		int nin;
		byte[] ba = new byte[blockSize];
		while( (nin = is.read( ba )) != -1 ) {
			md5.update( ba, 0, nin );
		}
		byte[] hash = md5.digest();
		return Hex.encodeHexString( hash );
	}

	static public String md5sum( RandomAccessVolume rav ) throws IOException {
		return md5sum( rav, 1024*1024 );
	}

	// grr, this is the SAME logic as InputStream...
	static public String md5sum( RandomAccessVolume rav, int blockSize )
		throws IOException {
		MessageDigest md5 = null;
		try {
			md5 = MessageDigest.getInstance( "md5" );
		} catch( Exception e ) {
			// never
		}
		int nin;
		byte[] ba = new byte[blockSize];
		while( (nin = rav.read( ba )) != -1 ) {
			md5.update( ba, 0, nin );
		}
		byte[] hash = md5.digest();
		return Hex.encodeHexString( hash );
	}

	/**
	   Given a byte count, express as a short string with units.  Much
	   like the output of 'df -h'
	*/
	static public String sizeEstimate( long bytes ) {
		if( bytes < Constants.KiB ) {
			return "" + bytes + "B";
		}
		if( bytes < Constants.MiB ) {
			return "" + bytes / Constants.KiB + "K";
		}
		if( bytes < Constants.GiB ) {
			return "" + bytes / Constants.MiB + "M";
		}
		if( bytes < Constants.TiB ) {
			return "" + bytes / Constants.GiB + "G";
		}
		return String.format( "%.1fT", (double)bytes / Constants.TiB );
	}

}

// eof
