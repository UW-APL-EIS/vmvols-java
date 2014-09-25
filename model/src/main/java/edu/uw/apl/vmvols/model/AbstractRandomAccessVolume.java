package edu.uw.apl.vmvols.model;

import java.io.IOException;

/**
   Partial implementation of the RandomAccessVolume interface.
*/

abstract public class AbstractRandomAccessVolume
	implements RandomAccessVolume {

	@Override
	public int read() throws IOException {
		byte[] ba = new byte[1];
		int n = read( ba, 0, 1 );
		if( n == -1 )
			return -1;
		return ba[0] & 0xff;
	}

	@Override
	public int read( byte[] b ) throws IOException {
		return read( b, 0, b.length );
	}

	@Override
	public void write( int b ) throws IOException {
		byte[] ba = new byte[1];
		ba[0] = (byte)b;
		write( ba );
	}
	
	@Override
	public void write( byte[] b ) throws IOException {
		write( b, 0, b.length );
	}
}

// eof