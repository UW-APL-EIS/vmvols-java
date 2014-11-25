package edu.uw.apl.vmvols.model.virtualbox;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import org.apache.commons.io.EndianUtils;

public class VDIHeaders {

	static VDIHeader parse( File f ) throws IOException {
		RandomAccessFile raf = new RandomAccessFile( f, "r" );
		PreHeader ph = new PreHeader( raf );
		VDIHeader result = null;
		switch( ph.versionMajor() ) {
		case 0:
			// to do. old??
			throw new IllegalStateException( "Major version 0 not supported: "
											 + f );
		case 1: 
			result = new Header1( raf );
			break;
		}
		return result;
	}

	/**
	 * with info from VDICore.h in Vbox OSE sources, replicated here
	 * in ./VDICore.h for easy reference
	 */
	static class PreHeader {
		PreHeader( DataInput di ) throws IOException {
			byte[] ba = new byte[SIZEOF];
			di.readFully( ba );
			sig = EndianUtils.readSwappedUnsignedInteger( ba, 64 );
			if( sig != VDI_IMAGE_SIGNATURE )
				throw new VDIMissingSignatureException
					( "VDI Signature missing" );
			version = EndianUtils.readSwappedUnsignedInteger( ba, 68 );
		}

		public long signature() {
			return sig;
		}

		public long version() {
			return version;
		}

		public int versionMajor() {
			return (int)((version & 0xffff0000L) >> 16);
		}
		
		public int versionMinor() {
			return (int)(version & 0xffff);
		}
		
		long sig, version;
		static public final int SIZEOF = 64+4+4;

		
		/** Image signature. */
		//		#define VDI_IMAGE_SIGNATURE   (0xbeda107f)

		static public final long VDI_IMAGE_SIGNATURE  = 0xbeda107fL;
	}

	static class Header1 implements VDIHeader {
		public Header1( DataInput di ) throws IOException {
			byte[] ba = new byte[4];
			di.readFully( ba );
			// cbHeader...
			long sizeof = EndianUtils.readSwappedUnsignedInteger( ba, 0 );
			//			logger.debug( "VH1 sizeof " + sizeof );
			ba = new byte[(int)sizeof-4];
			di.readFully( ba );

			// u32Type...
			type = EndianUtils.readSwappedUnsignedInteger( ba, 0 );
			//logger.debug( "type " + type );

			// flags 4, comment 256, then...

			blocksOffset = EndianUtils.readSwappedUnsignedInteger( ba, 264 );
			//logger.debug( "blocks offset " + blocksOffset );

			dataOffset = EndianUtils.readSwappedUnsignedInteger( ba, 268 );
			//logger.debug( "data offset " + dataOffset );

			// disk geometry 16, dummy 4, then...

			diskSize = EndianUtils.readSwappedLong( ba, 292 );
			blockSize = EndianUtils.readSwappedUnsignedInteger( ba, 300 );

			long blockExtraSize = EndianUtils.readSwappedUnsignedInteger
				( ba, 304 );
			if( blockExtraSize != 0 ) {
				throw new VDIException
					( "Non zero blockExtraSize not supported!" );
			}
			
			blockCount = EndianUtils.readSwappedUnsignedInteger( ba, 308 );

			// blocksAllocated 4, then...
			// RTUUID          uuidCreate;
			// RTUUID          uuidModify
			// RTUUID          uuidLinkage;

			/*
			  byte[] uuid = new byte[RTUUID.SIZEOF];

			System.arraycopy( ba, 316, uuid, 0, uuid.length );
			uuidCreate = new RTUUID( uuid );

			System.arraycopy( ba, 316+2*RTUUID.SIZEOF, uuid, 0, uuid.length );
			uuidLinkage = new RTUUID( uuid );
			*/

			uuidCreate = readFrom( ba, 316 );
			uuidLinkage = readFrom( ba, 316+32 );
			
			
		}

		static private UUID readFrom( byte[] ba, int offset ) {
			long timeLow = EndianUtils.readSwappedUnsignedInteger
				( ba, offset );
			int timeMidI = EndianUtils.readSwappedUnsignedShort
				( ba, offset + 4 );
			long timeMid = timeMidI & 0xffffffffL;
			int versionTimeHiI = EndianUtils.readSwappedUnsignedShort
				( ba, offset + 6 );
			long versionTimeHi = versionTimeHiI & 0xffffffffL;
			long msb = (timeLow << 32) | (timeMid << 16) | versionTimeHi;

			/*		int reservedClockSeqI = EndianUtils.readSwappedUnsignedShort
				( ba, offset + 10 );
			long reservedClockSeq = reservedClockSeqI & 0xffffffffL;
			*/
			long lsb = 0;
			for( int i = 0; i < 8; i++ )
				lsb |= (ba[offset+8+i] & 0xffL) << (56 - 8*i);
			return new UUID( msb, lsb );
		}
			
		@Override
		public long imageType() {
			return type;
		}
		
		@Override
		public long diskSize() {
			return diskSize;
		}

		@Override
		public long blocksOffset() {
			return blocksOffset;
		}
		
		@Override
		public long dataOffset() {
			return dataOffset;
		}
		
		@Override
		public long blockSize() {
			return blockSize;
		}

		@Override
		public long blockCount() {
			return blockCount;
		}

		@Override
		public UUID imageCreationUUID() {
			return uuidCreate;
		}

		@Override
		public UUID imageParentUUID() {
			return uuidLinkage;
		}

		@Override
		public String toString() {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter( sw );
			pw.println( "type: " + type );
			pw.println( "uuidCreate: " + uuidCreate );
			pw.println( "uuidParent: " + uuidLinkage );
			return sw.toString();
		}
		
		// all these are defined as uint32_t in VDICore, so need longs here..
		long type;
		long blocksOffset, dataOffset, diskSize;
		long blockSize;
		long blockCount;
		UUID uuidCreate;
		UUID uuidLinkage;
	}

	/**
	 * As per /path/to/vbox/include/iprt/uuid.h
	 */
	static class RTUUID {
		public RTUUID( byte[] ba ) throws IOException {
			u32TimeLow = EndianUtils.readSwappedUnsignedInteger( ba, 0 );
			u16TimeMid = EndianUtils.readSwappedUnsignedShort( ba, 4 );
			u16TimeHiAndVersion = EndianUtils.readSwappedUnsignedShort( ba, 6);
			u8ClockSeqHiAndReserved = ba[8] & 0xff;
			u8ClockSeqLow = ba[9] & 0xff;
			au8Node = new int[6];
			for( int i = 0; i < 6; i++ ) {
				au8Node[i] = ba[10+i] & 0xff;
			}
		}

		public String toString() {
			return String.format
				( "%08x-%04x-%04x-%02x%02x-%02x%02x%02x%02x%02x%02x",
				  u32TimeLow,
				  u16TimeMid,
				  u16TimeHiAndVersion,
				  u8ClockSeqHiAndReserved,
				  u8ClockSeqLow,
				  au8Node[0], au8Node[1], au8Node[2],
				  au8Node[3], au8Node[4], au8Node[5] );
		}
		
		long    u32TimeLow;
        int     u16TimeMid;
        int     u16TimeHiAndVersion;
        int     u8ClockSeqHiAndReserved;
		int     u8ClockSeqLow;
        int[]   au8Node;

		static final String NULL =
			"00000000-0000-0000-0000-000000000000";

		static public final int SIZEOF = 16;
	} 
}

// eof
