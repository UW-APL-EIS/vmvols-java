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
	 * @param ba expected 512 bytes extracted from a .vmdk file.
	 * Normally at file start, but streamOptimized disks have
	 * footers (matching header layout) near end of file.
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
	
	private Logger logger;
	
	// #define SPARSE_MAGICNUMBER 0x564d444b /* 'V' 'M' 'D' 'K' */
	static public final long MAGICNUMBER = 0x564d444bL;

	static public final int SIZEOF = 512;

	static public final int FLAGS_USEREDUNDANTGRAINTABLE = (1 << 1);
	static public final int FLAGS_COMPRESSEDGRAINS = (1 << 16);
	static public final int FLAGS_HASGRAINMARKERS = (1 << 17);
}

// eof
