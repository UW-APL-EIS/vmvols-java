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
import org.apache.log4j.Logger;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.VirtualDisk;

/**
   Based upon page 8 of the Virtual Disk Format 1.1 spec...
*/
public class SparseExtentHeader {
	
	/**
	 * @param ba expected 512 bytes from start of .vmdk file...
	 */
	public SparseExtentHeader( byte[] ba ) throws IOException {

		logger = Logger.getLogger( getClass() );
		
		// uint32...
		long magic = EndianUtils.readSwappedUnsignedInteger( ba, 0 );
		if( magic != MAGICNUMBER ) {
			throw new IllegalStateException
				( "Unexpected magic number " + magic +
				  ". No SparseExtentHeader..." );
		}
		
		// uint32...
		long version = EndianUtils.readSwappedUnsignedInteger( ba, 4 );
		boolean allowVersion2Plus = true;
		if( version != 1 && !allowVersion2Plus ) {
			throw new IllegalStateException
				( "Unexpected version " + version );
		}
		
		// uint32...
		flags = EndianUtils.readSwappedUnsignedInteger( ba, 8 );
		
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
		logger.debug( "GTEsPerGT " + numGTEsPerGT );

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
		int compressAlgorithm =
			EndianUtils.readSwappedUnsignedShort( ba,77);
		
		/*
		  gives a read count of 79 bytes, which leaves 512-79 = 433
		  bytes of padding. This agrees with the spec which notes:
		  
		  uint8 pad[433];
		*/
		
	}

	long getDescriptorOffset() {
		return descriptorOffset * Constants.SECTORLENGTH;
	}

	long getDescriptorSize() {
		return descriptorSize * Constants.SECTORLENGTH;
	}

	// in sectors...
	long grainDirOffset() {
		return (flags & 0x2) == 0x2 ? rgdOffset : gdOffset;
	}

	public String paramString() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter( sw );
		pw.printf( "Flags: %08x\n", flags );
		pw.println( "Capacity: " + capacity );
		pw.println( "GrainSize: " + grainSize );
		pw.println( "DescriptorOffset: " + descriptorOffset );
		pw.println( "DescriptorSize: " + descriptorSize );
		pw.println( "NumGTEsPerGT: " + numGTEsPerGT );
		pw.println( "Overhead: " + overhead );
		return sw.toString();
	}
	
	final long flags, capacity, grainSize, gdOffset, rgdOffset, overhead;
	final long numGTEsPerGT;
	final long descriptorOffset, descriptorSize;

	private Logger logger;
	
	// #define SPARSE_MAGICNUMBER 0x564d444b /* 'V' 'M' 'D' 'K' */
	static public final long MAGICNUMBER = 0x564d444bL;

	static public final int SIZEOF = 512;
}

// eof
