/**
 * Copyright © 2015, University of Washington
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the University of Washington nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE UNIVERSITY
 * OF WASHINGTON BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.EOFException;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.codec.binary.Hex;

/**
 * @author Stuart Maclean
 *
 * Various utility methods related to reading from InputStreams, in
 * particular ensuring that an exact number of bytes have been
 * read/skipped.  Contracts for java.io.InputStream read operations
 * are always that 'fewer bytes than the supplied amount CAN be
 * returned'.  We want ALL bytes back!
 */

public class Utils {

	/**
	   Mimic DataInput.readFully, avoiding the use of a
	   DataInputStream wrapper around an InputStream.  Follows the
	   contract of DataInput.readFully (especially the throwing of
	   EOFException on incomplete read)

	   Should read byte count into b matching b.length itself, i.e
	   completely fill up b.
	   
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
	   actual skip count can be < n.  So wrap it in a loop to turn
	   ' <= n ' into ' == n'.
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
	 * @result Count of bytes read from stream until eof reached
	 */
	static public long size( InputStream is ) throws IOException {
		return size( is, 1024*1024 );
	}
	
	/**
	 * @result Count of bytes read from stream until eof reached, with
	 * reads in blocks of blockSize.
	 */
	static public long size( InputStream is, int blockSize )
		throws IOException {
		long result = 0;
		int nin;
		byte[] ba = new byte[blockSize];
		while( (nin = is.read( ba )) != -1 ) {
			result += nin;
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
		} catch( NoSuchAlgorithmException neverSinceMD5Required ) {
		}
		int nin;
		byte[] ba = new byte[blockSize];
		while( (nin = is.read( ba )) != -1 ) {
			md5.update( ba, 0, nin );
		}
		byte[] hash = md5.digest();
		return Hex.encodeHexString( hash );
	}
	
	/**
	   Given a byte count, express as a short string with units.  Much
	   like the output of 'df -h'.  Example: 1024 -> "1K"
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
