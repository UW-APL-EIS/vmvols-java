package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.commons.io.EndianUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;

/**
 * @author Stuart Maclean
 *
 * A VMware 'extent' contains some or all of the data of a virtual
 * disk.  Typically, most .vmdk files are of the
 * 'monolithicsparsedisk' variant, which employ a single extent to
 * hold ALL of the virtual disk data.
 *
 * A 'sparse extent' is then a SparseExtentHeader plus the data, held
 * in 'grains'.  Info in the SEH tells us where to look in the host
 * file (the .vmdk) for data at any given offset in the virtual disk.

 * See model/doc/vmware/vmdk_specs.pdf for description of the VMDK
 * format, grain directories and tables, etc.
 *
 */

 public class SparseExtent {

	public SparseExtent( File f, SparseExtentHeader h ) {
		source = f;
		header = h;
		log = LogFactory.getLog( getClass() );
	}

	public long size() {
		return header.capacity * Constants.SECTORLENGTH;
	}
	
	public void readMetaData() throws IOException {
		readGrainData();
	}

	private void readGrainData() throws IOException {
		// only need to read the directory at most once, it is invariant...
		if( grainDirectory != null )
			return;
		
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		long grainCount = header.capacity / header.grainSize;
		int grainTableCount = (int)(grainCount / header.numGTEsPerGT );
		log.info( "GrainCount: "+ grainCount );
		log.info( "GrainTableCount: "+ grainTableCount );

		byte[] gdBuf = new byte[4*grainTableCount];
		long gdOffset = header.grainDirOffset();
		log.info( "Using gdOffset: "+ gdOffset );
		raf.seek( gdOffset * Constants.SECTORLENGTH );
		raf.readFully( gdBuf );
		long[] gdes = new long[grainTableCount];
		for( int i = 0; i < gdes.length; i++ ) {
			long gde = EndianUtils.readSwappedUnsignedInteger( gdBuf, 4*i );
			gdes[i] = gde;
		}
		
		byte[] gtBuf = new byte[(int)(4*header.numGTEsPerGT)];
		grainDirectory = new long[gdes.length][];

		for( int i = 0; i < gdes.length; i++ ) {
			long gde = gdes[i];
			//log.debug( i + " " + gde );
			
			if( gde == 0 ) {
				//				log.info( "GDE Zero: " + i );
				grainDirectory[i] = PARENTGDE;
				continue;
			}
			
			if( gde == 1 ) {
				log.info( "GDE One: " + i );
				if( true )
					throw new IllegalStateException( "GDE 1 " + source );
				grainDirectory[i] = ZEROGDE;
				continue;
			}

			raf.seek( gde * Constants.SECTORLENGTH );
			raf.readFully( gtBuf );

			long[] grainTable = new long[(int)header.numGTEsPerGT];
			for( int gt = 0; gt < grainTable.length; gt++ ) {
				long gte = EndianUtils.readSwappedUnsignedInteger( gtBuf,
																   4*gt );
				grainTable[gt] = gte;
			}
			grainDirectory[i] = grainTable;
		}
		raf.close();
	}

	private void buildZeroGrains() {
		if( zeroGrain != null )
			return;
		grainSizeBytes = header.grainSize * Constants.SECTORLENGTH;
		grainTableCoverageBytes = grainSizeBytes * header.numGTEsPerGT;
		log2GrainSize = log2( grainSizeBytes );
		log2GrainTableCoverage = log2( grainTableCoverageBytes );
		log2SectorSize = log2( Constants.SECTORLENGTH );
	    if( header.grainSize == GRAINSIZE_DEFAULT ) {
			zeroGrain =	ZEROGRAIN_DEFAULT;
			zeroGrainTable = ZEROGRAINTABLE_DEFAULT;
		} else {
			zeroGrain = new byte[(int)grainSizeBytes];
			zeroGrainTable = new byte[(int)grainTableCoverageBytes];
		}
	}

	// for test case access only
	long[][] getGrainDirectory() {
		return grainDirectory;
	}

	/*
	private long nextFreeGrain() {
		long max = -1;
		for( long[] gt : grainTables ) {
			for( long gte : gt ) {
				if( gte == 0 )
					continue;
				if( gte > max )
					max = gte;
			}
		}
		return max+1;
	}
	
	private void writeGrainTables() throws IOException {
		byte[] ba = new byte[(int)(4*header.numGTEsPerGT)];
		RandomAccessFile raf = new RandomAccessFile( source, "rw" );
		for( int i = 0; i < grainDirectory.length; i++ ) {
			long[] gt = grainTables[i];
			for( int j = 0; j < gt.length; j++ ) {
				EndianUtils.writeSwappedInteger( ba, 4*j, (int)gt[j] );
			}
			long gde = grainDirectory[i];
			raf.seek( gde * Constants.SECTORLENGTH );
			raf.write( ba );
		}
		raf.close();
	}
	*/

	InputStream getInputStream( InputStream parentIS ) throws IOException {
		readMetaData();
		buildZeroGrains();
		return new SparseExtentRandomAccess
			( false, (RandomAccessVirtualDisk)parentIS );
	}

	RandomAccessVirtualDisk getRandomAccess( RandomAccessVirtualDisk parentRA )
		throws IOException {
		readMetaData();
		buildZeroGrains();
		return new SparseExtentRandomAccess( true, parentRA );
	}

	/*
	  The SparseExtentRandomAccess does the real work.  It presents a
	  standard InputStream functionality interface to clients, which
	  call any combo of read, skip, etc. These operations move a
	  logical 'file pointer' forwards (just like would occur for real
	  when using e.g. a FileInputStream).  We then map this file
	  pointer (which is a long) to a physical location in the backing
	  vmdk file, by use of simple indexing and offsets using the grain
	  directory, grain tables and offset into grain.
	*/

	class SparseExtentRandomAccess extends RandomAccessVirtualDisk {
		/**
		   @param parentRA randomaccessvirtualdisk of any parent
		   extent, matching this SERA.  If the enclosing VMDKDisk has
		   no parent, this will be null.  So parentRA always aligns
		   with this SERA, in terms of the sector sequence spanned.

		   Called only within this class, local access...
		*/
		SparseExtentRandomAccess( boolean writable,
								  RandomAccessVirtualDisk parentRA )
			throws IOException {
			super( size() );
			this.parentRA = parentRA;
			String mode = writable ? "rw" : "r";
			raf = new RandomAccessFile( source, mode );
			dPos();
			grainBuffer = new byte[(int)grainSizeBytes];
			gtePrev = 0;
		}

		@Override
		public void close() throws IOException {
			if( parentRA != null )
				parentRA.close();
			raf.close();
		}

		@Override
	    public long skip( long n ) throws IOException {
			if( parentRA != null )
				parentRA.skip( n );
			long result = super.skip( n );
			dPos();
			return result;
		}

		@Override
		public void seek( long s ) throws IOException {
			/*
			  According to java.io.RandomAccessFile, no restriction on
			  seek position s.  That is, seek posn can be -ve or past
			  eof
			*/
			if( parentRA != null )
				parentRA.seek( s );
			posn = s;
			dPos();
		}

		/**
		   For the array read, we shall attempt to satisy the length
		   requested, even if it is takes us many reads (of the
		   physical .vmdk file) from different grains to do so.  While
		   the contract for InputStream is that any read CAN return <
		   len bytes, for InputStreams backed by file data, users
		   probably expect len bytes back (fewer of course if eof).

		   Further, when using this class with our
		   VirtualMachineFileSystemS/Fuse4j/fuse system to expose the
		   vmdk to fuse, fuse states that the callback read operation
		   is REQUIRED to return len bytes if they are available
		   (i.e. not read past eof)
		*/
		   
		@Override
		public int readImpl( byte[] ba, int off, int len ) throws IOException {

			// Do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );

			// Cannot blindly coerce a long to int, result could be -ve
			int actual = actualL > Integer.MAX_VALUE ? Integer.MAX_VALUE :
				(int)actualL;

			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				long[] gt = grainDirectory[gdIndex];
				if( false ) {
				} else if( gt == PARENTGDE ) {
					if( parentRA != null ) {
						// look, read from parent...
					}
					log.debug( "Zero GD : " + gdIndex );
					int grainTableOffset = (int)
						(gtIndex << log2GrainSize + gOffset);
					int inGrainTable = (int)
						(grainTableCoverageBytes - grainTableOffset);
					int fromGrainTable = Math.min( left, inGrainTable );
					System.arraycopy( zeroGrainTable, grainTableOffset,
									  ba, off+total, fromGrainTable );
					if( log.isDebugEnabled() )
						log.debug( len + " " + actual + " " +
								   left + " " + inGrainTable + " " +
								   fromGrainTable );
					total += fromGrainTable;
					posn += fromGrainTable;
				} else if( gt == ZEROGDE ) {
					throw new IllegalStateException( "ZEROGDE!" );
				} else {
					int inGrain = (int)(grainSizeBytes - gOffset);
					int fromGrain = Math.min( left, inGrain );
					if( log.isDebugEnabled() )
						log.debug( len + " " + actual + " " + left + " " +
								   inGrain + " " + fromGrain );
					long gte = gt[gtIndex];
					if( false ) {
					} else if( gte == 0 ) {
						// 0 means 'from parent if exists, else zeros'
						if( log.isDebugEnabled() )
							log.debug( "Zero GT : "+ gdIndex + " " + gtIndex );
						if( parentRA != null ) {
							// LOOK: fromParent should ALWAYS == fromGrain
							int fromParent = parentRA.readImpl( ba, off+total,
																fromGrain );
							total += fromParent;
							posn += fromParent;
						} else {
							System.arraycopy( zeroGrain, 0,
											  ba, off+total, fromGrain );
							total += fromGrain;
							posn += fromGrain;
						}
					} else if( gte == 1 ) {
						// 1 means 'zeros'
						System.arraycopy( zeroGrain, 0,
										  ba, off+total, fromGrain );
						total += fromGrain;
						posn += fromGrain;
						if( parentRA != null )
							parentRA.skip( fromGrain );
					} else {
						if( gte != gtePrev ) {
							raf.seek( gte << log2SectorSize );
							raf.readFully( grainBuffer );
							gtePrev = gte;
						}
						System.arraycopy( grainBuffer, (int)gOffset,
										  ba, off+total, fromGrain );
						total += fromGrain;
						posn += fromGrain;
						if( parentRA != null )
							parentRA.skip( fromGrain );
					}
				}
				if( log.isDebugEnabled() )
					log.debug( total + " " + posn );
				dPos();
			}
			return total;
		}


		/**
		   For the array write, we shall attempt to satify the length
		   requested, even if it is takes us many writes (of the
		   physical file) from different blocks to do so.
		*/
		
		@Override
		public void writeImpl( byte[] ba, int off, int len )
			throws IOException {

			// Until we fix this ...
			if( true )
				throw new UnsupportedOperationException();
			
			log.debug( "Write.[BII: " + off + " " + len );
			
			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );

			// Cannot blindly coerce a long to int, result could be -ve
			int actual = actualL > Integer.MAX_VALUE ? Integer.MAX_VALUE :
				(int)actualL;

			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inGrain = (int)(grainSizeBytes - gOffset);
				int fromGrain = Math.min( left, inGrain );
				long gte = 0;// LOOK grainTables[gdIndex][gtIndex];
				log.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				if( gte == 0 ) {
					gte = 0;//nextFreeGrain();
					//grainTables[gdIndex][gtIndex] = gte;
					//writeGrainTables();
				}
				// need long operands to the product, to avoid overflow...
				long seek = gte * Constants.SECTORLENGTH + gOffset;
				raf.seek( seek );
				raf.write( ba, off+total, fromGrain );
				total += fromGrain;
				posn += fromGrain;
				dPos();
			}
		}

		/**
		   Called whenever the local posn changes value.
		   Do NOT make calls to the parent.dPos here,
		   but only from the reads...
		*/
		private void dPos() {

			/*
			  This is the crux of the sparse reading. We map logically
			  map the 'file pointer' on the input stream to a grain
			  table, table entry and grain offset.  To do the next
			  read, we then calc a physical seek offset into the atual
			  file and read.

			  According to java.io.RandomAccessFile, a file posn
			  is permitted to be past its size limit.  We cannot
			  map such a posn to the grain info of course...
			*/
			if( posn >= size )
				return;

			// Logically same as posn / grainTableCoverage
			gdIndex = (int)(posn >>> log2GrainTableCoverage);


			/*
			  Logically same as
			  
			  (posn - (gdIndex * grainTableCoverage)) / grainSize
			*/
			long inTable = posn - ((long)gdIndex << log2GrainTableCoverage);
			gtIndex = (int)(inTable >>> log2GrainSize);

			// Logically same as inTable % grainSizeBytes
			gOffset = (int)(inTable & (grainSizeBytes - 1));

			if( log.isDebugEnabled() )
				log.debug( "gdIndex: " + gdIndex +
							  " gtIndex: " + gtIndex +
							  " gOffset: " + gOffset );
		}

		private final RandomAccessFile raf;
		private RandomAccessVirtualDisk parentRA;
		private int gdIndex, gtIndex, gOffset;
		private long gtePrev;
		private byte[] grainBuffer;
	}
	
	static int log2( long i ) {
		for( int p = 0; p < 32; p++ ) {
			if( i == 1 << p )
				return p;
		}
		throw new IllegalArgumentException( "Not a pow2: " + i );
	}

	File source;
	SparseExtentHeader header;
	Log log;
	
	long grainSizeBytes, grainTableCoverageBytes;
	int log2GrainSize, log2GrainTableCoverage, log2SectorSize;

	long[][] grainDirectory;
	byte[] zeroGrain;
	byte[] zeroGrainTable;
	

	/*
	  The VMware vmdk spec says that the default grain size is 2^7 =
	  128 sectors, or 64KB.  So prealloc the '64KB zero grain', so
	  that all SparseExents, StreamOptimizedSparseExtents can use it
	  (so saving on per-disk/stream copies)
	*/

	 // LOOK: We have this in ./Constants.java ??
	static final long GRAINSIZE_DEFAULT = 128;

	static final long NUMGTESPERGT = 512;

	static final byte[] ZEROGRAIN_DEFAULT =
		new byte[(int)(GRAINSIZE_DEFAULT * Constants.SECTORLENGTH)];

	static final byte[] ZEROGRAINTABLE_DEFAULT =
		new byte[(int)(GRAINSIZE_DEFAULT * Constants.SECTORLENGTH *
					   NUMGTESPERGT )];

	static final long[] ZEROGDE = new long[0];
	static final long[] PARENTGDE = new long[0];
}

// eof
