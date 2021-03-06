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
package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.io.EndianUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.VirtualDisk;

/**
 * @author Stuart Maclean.
 *
 * Object representation of a VMware SparseExtentHeader, a data structure
 * which appears at the very start of .vmdk files.
 *
 * Based upon page 8 of the Virtual Disk Format 1.1 spec, which gives
 * the usual 'definition by C struct'.
 *
 * @see Descriptor
 */
public class SparseExtentHeader {
	
	/**
	 * @param ba expected 512 bytes extracted from a .vmdk file.
	 *
	 * Normally at file start, but streamOptimized disks have
	 * footers (matching header layout) near end of file too.
	 */
	public SparseExtentHeader( byte[] ba ) throws IOException {

		log = LogFactory.getLog( getClass() );
		
		// uint32...
		long magic = EndianUtils.readSwappedUnsignedInteger( ba, 0 );
		if( magic != MAGICNUMBER ) {
			throw new IllegalStateException
				( "Unexpected magic number " + magic +
				  ". No SparseExtentHeader..." );
		}
		
		// uint32...
		version = EndianUtils.readSwappedUnsignedInteger( ba, 4 );
		boolean allowVersion2Plus = true;
		if( version != 1 && !allowVersion2Plus ) {
			throw new IllegalStateException
				( "Unexpected version " + version );
		}
		
		// uint32...
		flags = (int)EndianUtils.readSwappedUnsignedInteger( ba, 8 );
		
		/*
		  The spec types the following fields as 'uint64' (via a
		  typedef to SectorType), which we map to Java long.  The
		  fact that our long is signed is OK since this top bit
		  could only be set in the vmdk if a size was 2^63 which
		  is rather large ;)
		  
		  The units of all these 'SectorType' values is 512-byte
		  sectors.
		*/
		capacity = EndianUtils.readSwappedLong( ba, 12 );
		grainSize = EndianUtils.readSwappedLong( ba, 20 );
		descriptorOffset = EndianUtils.readSwappedLong( ba, 28 );
		descriptorSize = EndianUtils.readSwappedLong( ba, 36 );

		// uint32...
		numGTEsPerGT = EndianUtils.readSwappedUnsignedInteger( ba, 44);
		log.debug( "GTEsPerGT " + numGTEsPerGT );

		// SectorType...
		rgdOffset = EndianUtils.readSwappedLong( ba, 48 );
		gdOffset = EndianUtils.readSwappedLong( ba, 56 );
		overhead = EndianUtils.readSwappedLong( ba, 64 );
			
		// uint8...
		int uncleanShutdown = ba[72] & 0xff;
		int singleEndLineChar = ba[73] & 0xff;
		int nonEndLineChar = ba[74] & 0xff;
		int doubleEndLineChar1 = ba[75] & 0xff;
		int doubleEndLineChar2 = ba[76] & 0xff;
		
		// uint16...
		compressAlgorithm =
			EndianUtils.readSwappedUnsignedShort( ba,77 );
		
		/*
		  gives a read count of 79 bytes, which leaves 512-79 = 433
		  bytes of padding. This agrees with the spec, which notes:
		  
		  uint8 pad[433];
		*/
		
	}

	public int flags() {
		return flags;
	}
	
	public long getDescriptorOffset() {
		return descriptorOffset;
	}

	public long getDescriptorOffsetBytes() {
		return getDescriptorOffset() * Constants.SECTORLENGTH;
	}

	public long getDescriptorSize() {
		return descriptorSize;
	}

	public long getDescriptorSizeBytes() {
		return getDescriptorSize() * Constants.SECTORLENGTH;
	}

	// in sectors...
	public long grainDirOffset() {
		return (flags & FLAGS_USEREDUNDANTGRAINTABLE )
			== FLAGS_USEREDUNDANTGRAINTABLE ? rgdOffset : gdOffset;
	}

	public String paramString() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter( sw );
		pw.printf( "Flags: %08x\n", flags );
		pw.println( "Version: " + version );
		pw.println( "Capacity: " + capacity );
		pw.println( "GrainSize: " + grainSize );
		pw.println( "DescriptorOffset: " + descriptorOffset );
		pw.println( "DescriptorSize: " + descriptorSize );
		pw.println( "NumGTEsPerGT: " + numGTEsPerGT );
		pw.println( "rgdOffset: " + rgdOffset );
		pw.println( "gdOffset: " + gdOffset );
		pw.println( "Overhead: " + overhead );
		pw.println( "Compression: " + compressAlgorithm );
		return sw.toString();
	}
	
	final long version;
	final int flags;
	final long capacity, grainSize, gdOffset, rgdOffset, overhead;
	final long numGTEsPerGT;
	final long descriptorOffset, descriptorSize;
	final int compressAlgorithm;
	
	private Log log;
	
	// #define SPARSE_MAGICNUMBER 0x564d444b /* 'V' 'M' 'D' 'K' */
	static public final long MAGICNUMBER = 0x564d444bL;

	static public final int SIZEOF = 512;

	static public final int FLAGS_USEREDUNDANTGRAINTABLE = (1 << 1);
	static public final int FLAGS_COMPRESSEDGRAINS = (1 << 16);
	static public final int FLAGS_HASGRAINMARKERS = (1 << 17);
}

// eof
