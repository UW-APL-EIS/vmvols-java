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

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Stuart Maclean
 *
 * Abstract class defining three abstract methods and a series of helper
 * functions for the end goal of reading from and writing to a virtual
 * disk at an arbitrary disk sector and sub-sector location.  These
 * reads/writes are at the disk level, i.e. 'underneath' any filesystem
 * which may be on the (virtual) disk.
 *
 * Subclasses must define just
 *
 * readImpl
 * writeImpl
 * seek
 *
 * We subclass java.io.InputStream, and steal a partial write-oriented
 * API from java.io.RandomAccessFile.  Especially useful is 'seek',
 * allowing us to hop around the disk even when reading, a feature
 * that is required by file system traversals by e.g. Sleuthkit.  The
 * seekability of the virtual disk data via this class is also what
 * makes our VirtualMachineFileSystem (FUSE) possible.
 */

abstract public class RandomAccessVirtualDisk extends InputStream {

	protected RandomAccessVirtualDisk( long size ) {
		this.size = size;
		posn = 0;
	}

	abstract public int readImpl( byte[] b, int off, int len )
		throws IOException;

	abstract public void writeImpl( byte[] b, int off, int len )
		throws IOException;

	abstract public void seek( long pos ) throws IOException;

	@Override
	public int available() throws IOException {
		// Cannot simply cast 'size - posn' to int, could get -ve value!
		long l = size - posn;
		if( l >= Integer.MAX_VALUE )
			return Integer.MAX_VALUE;
		return (int)l;
	}

	@Override
	public int read() throws IOException {
		byte[] ba = new byte[1];
		int n = read( ba, 0, 1 );
		if( n == -1 )
			return -1;
		return ba[0] & 0xff;
	}

	@Override
	public int read( byte[] b, int off, int len ) throws IOException {
		
		// checks from the contract for InputStream...
		if( b == null )
			throw new NullPointerException();
		if( off < 0 || len < 0 || off + len > b.length ) {
			throw new IndexOutOfBoundsException();
		}
		if( len == 0 )
			return 0;

		// LOOK: posn < 0, since seek allows for this...
		if( posn >= size )
			return -1;
		
		int n = readImpl( b, off, len );
		if( n == -1 ) {
			throw new IOException();
		}
		return n;
	}

	@Override
	public long skip( long n ) throws IOException {
		if( n < 0 )
			return 0;
		long min = Math.min( n, size-posn );
		posn += min;
		return min;
	}

	public void write( int b ) throws IOException {
		byte[] ba = new byte[1];
		ba[0] = (byte)b;
		write( ba, 0, 1 );
	}

	public void write( byte[] bs ) throws IOException {
		write( bs, 0, bs.length );
	}
		
	public void write( byte[] b, int off, int len ) throws IOException {

		// checks from the contract for java.io.RandomAccessFile...
		if( b == null )
			throw new NullPointerException();
		if( off < 0 || len < 0 || off + len > b.length ) {
			throw new IndexOutOfBoundsException();
		}
		if( len == 0 )
			return;
			
		// LOOK: posn < 0, since seek allows for this...
		if( posn >= size ) {
			return;
		}
			
		writeImpl( b, off, len );
	}

	protected final long size;
	protected long posn;
}

// eof