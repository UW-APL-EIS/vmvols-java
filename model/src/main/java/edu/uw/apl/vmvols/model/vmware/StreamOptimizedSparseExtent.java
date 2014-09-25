package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.EndianUtils;
import org.apache.log4j.Logger;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVolume;
import edu.uw.apl.vmvols.model.AbstractRandomAccessVolume;

/**
 * Stream-optimized compressed sparse extent.  The normal format for
 * vmdk files in .ovf bundles.  As well as being 'sparse', each grain
 * for which there _IS_ data is compressed using RFC1951 (Deflate).
 */
public class StreamOptimizedSparseExtent {

	public StreamOptimizedSparseExtent( File f, SparseExtentHeader h ) {
		source = f;
		header = h;
		logger = Logger.getLogger( getClass() );
		grainSizeBytes = header.grainSize * Constants.SECTORLENGTH;
		grainMarkersOrigin = (1 + header.descriptorOffset +
			header.descriptorSize) * Constants.SECTORLENGTH;

		grainTableCoverageBytes = header.numGTEsPerGT * grainSizeBytes;

		/*
		  maintain a zero grain as an optimisation for all reads
		  which hit such a grain
		*/
		if( grainSizeBytes == ZEROGRAIN_128.length ) {
			zeroGrain = ZEROGRAIN_128;
			logger.info( "Using static zero grain: " +
						 zeroGrain.length );
		} else {
			zeroGrain = new byte[(int)grainSizeBytes];
			logger.info( "Using local zero grain: " +
						 zeroGrain.length );
		}
	}

	List<Marker> getMarkers() throws IOException {
		if( markers == null )
			readMarkers();
		return markers;
	}
	
	private void readMarkers() throws IOException {
		markers = new ArrayList<Marker>();
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( grainMarkersOrigin );
		byte[] ba = new byte[Marker.SIZEOF];
		while( true ) {
			raf.readFully( ba );
			long val = EndianUtils.readSwappedLong( ba, 0 );
			long size = EndianUtils.readSwappedUnsignedInteger( ba, 8 );
			int type = (int)EndianUtils.readSwappedUnsignedInteger( ba, 8+4 );

			System.out.println( val + " " + size + " " + type );
			
			int markerLength = 0;
			if( size == 0 ) {
				if( type == Marker.TYPE_EOS )
					break;
				long numSectors = val;
				markerLength = 512;
			} else {
				markerLength += (size - 8 + 4);
				long lba = val;
			}
			Marker m = new Marker( val, size, type );
			markers.add( m );
			raf.skipBytes( markerLength );
		}
		raf.close();
	}
	
	InputStream getInputStream() throws IOException {

		readGrainTables();
		return new SparseExtentInputStream( null );
	}

	RandomAccessVolume getRandomAccessVolume() throws IOException {
		readGrainTables();
		return new SparseExtentRandomAccessVolume();
	}

	public long size() {
		return header.capacity * Constants.SECTORLENGTH;
	}
	
	public void readGrainDirectory() throws IOException {
		// only need to read the directory at most once, it is invariant...
		if( grainDirectory != null )
			return;

		int grainCount = (int)(header.capacity / header.grainSize);
		int grainDirectoryLength = (int)Math.ceil
			( (double)grainCount / header.numGTEsPerGT );
		System.out.println( "GDLen : " + grainDirectoryLength );
		grainDirectory = new long[grainDirectoryLength];
		byte[] ba = new byte[4*grainDirectory.length];
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		long gdo = header.grainDirOffset();
		System.out.println( "GDOff : " + gdo * Constants.SECTORLENGTH );
		raf.seek( gdo * Constants.SECTORLENGTH );
		raf.readFully( ba );
		for( int i = 0; i < grainDirectory.length; i++ ) {
			long gde = EndianUtils.readSwappedUnsignedInteger( ba, 4*i );
			grainDirectory[i] = gde;
			if( i == 0 ) {
				System.out.println( "GDE0 : " + gde );
			}

			//			logger.debug( i + " " + grainDirectory[i] );
		}
		raf.close();
	}

	public long[] getGrainDirectory() throws IOException {
		readGrainDirectory();
		return grainDirectory;
	}
	
	public void readGrainTables() throws IOException {
		readGrainDirectory();
		
		// only need to read the tables at most once, it is invariant...
		if( grainTables != null )
			return;
		
		grainTables = new long[grainDirectory.length][];
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		byte[] ba = new byte[(int)(4*header.numGTEsPerGT)];

		/*
		  The last grain table may not be 'full', depending on the
		  number of grains needed to span the extent.  So reads past
		  the end of the valid grain table entries are logically
		  invalid.  From experimentation, we have seen that grain
		  tables are always numGTEsPerGT entries long, with the
		  'invalid' entries set to 0.  So the raf.read will never
		  fail.
		*/
		long grainCount = header.capacity / header.grainSize;
		long gteCount = 0;
		for( int i = 0; i < grainDirectory.length; i++ ) {
			long[] grainTable = new long[(int)header.numGTEsPerGT];
			long gde = grainDirectory[i];
			if( i == 0 )
				System.out.println( "Seek to " + gde * Constants.SECTORLENGTH );
			raf.seek( gde * Constants.SECTORLENGTH );
			raf.readFully( ba );
			for( int j = 0; j < grainTable.length; j++ ) {
				long gte = EndianUtils.readSwappedUnsignedInteger( ba, 4*j );
				grainTable[j] = gte;
				if( i == 0 && j == 0 )
					System.out.println( gte );
				
				//logger.debug( i + " " + j + " " + grainTable[j] );
				gteCount++;
				//	System.out.println( "GTEs " + gteCount );
				if( gteCount > grainCount ) {
					/*
					  System.out.println( "At cap: " + gte + " " +
										i + " " + j );
					*/
					/*
					  The break leaves some parts of ba at 0, which is
					  OK since we should never have to read those
					  grain table entries (they would be past the
					  capacity of the disk).
					*/
					break;
				}
			}
			grainTables[i] = grainTable;
		}
		raf.close();
	}
	
	public long[][] getGrainTables() throws IOException {
		readGrainTables();
		return grainTables;
	}

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

	/*
	  The SparseExtentInputStream does the real work.  It presents a
	  standard InputStream functionality interface to clients, which
	  call any combo of read, skip, etc. These operations move a
	  logical 'file pointer' forwards (just like would occur for real
	  when using e.g. a FileInputStream).  We then map this file
	  pointer (which is a long) to a physical location in the backing
	  vmdk file, by use of simple indexing and offsets using the grain
	  directory, grain tables and offset into grain.

	  Note that since the SparseExtentInputStream is set up
	  with both a starting sector and sector count, it works for both
	  whole VMDKDisks and for Partitions and Unallocateds within.
	*/

	class SparseExtentInputStream extends InputStream {
		/**
		   @param pis inputstream of any parent extent, matching this
		   SEIS, which may represent a whole VMDKDisk or a
		   VirtualPartition (or even a VirtualUnallocated).  If the
		   enclosing VMDKDisk has no parent, this will be null.  So
		   pis always aligns with this SEIS, in terms of the sector
		   sequence spanned.

		   Called only within this class, local access...
		*/
		SparseExtentInputStream( InputStream pis ) throws IOException {
			this.pis = pis;
			raf = new RandomAccessFile( source, "r" );
			origin = 0;
			size = size();
			posn = 0;
			dPos();
		}

	    @Override
	    public int available() throws IOException {
			long l = size - posn;
			if( l >= Integer.MAX_VALUE )
				return Integer.MAX_VALUE;
			return (int)l;
	    }

		@Override
	    public long skip( long n ) throws IOException {
			if( pis != null ) {
				pis.skip( n );
			}
			if( n < 0 )
				return 0;
			long min = Math.min( n, size-posn );
			posn += min;
			dPos();
			return min;
	    }

		@Override
		public void close() throws IOException {
			if( pis != null ) {
				pis.close();
			}
			raf.close();
		}

		@Override
		public int read() throws IOException {
			if( posn >= size )
				return -1;
			
			int result;
			boolean parentRead = false;
			long gte = grainTables[gdIndex][gtIndex];
			if( gte == 0 ) {
				if( pis != null ) {
					result = pis.read();
					parentRead = true;
				} else {
					result = 0;
				}
			} else {
				long seek = gte * Constants.SECTORLENGTH + gOffset;
				raf.seek( seek );
				result = raf.read();
			}

			/*
			  If the parent did the read, its position update will
			  have been occurred.  If not, call skip on the parent,
			  which will do so.  This works whether the parent is a
			  SectorSequence.InputStream or another
			  SparseExtentInputStream
			*/
			if( pis != null && !parentRead ) {
				pis.skip( 1 );
			}
			posn += 1;
			dPos();
			return result;
		}

		/**
		   For the array read, we shall attempt to satisy the length
		   requested, even if it is takes us many reads (of the
		   physical file) from different grains to do so.  While the
		   contract for InputStream is that any read CAN return < len
		   bytes, for InputStreams backed by file data, users probably
		   expect len bytes back (fewer of course if eof).

		   Further, when using this class with our
		   VirtualDiskFS/Fuse4j/fuse system to expose the vmdk to
		   fuse, fuse states that the callback read operation is
		   REQUIRED to return len bytes if they are available
		   (i.e. not read past eof)
		*/
		   
		@Override
		public int read( byte[] ba, int off, int len ) throws IOException {
			// checks from the contract for InputStream...
			if( ba == null )
				throw new NullPointerException();
			if( off < 0 || len < 0 || off + len > ba.length )
				throw new IndexOutOfBoundsException();
			if( len == 0 )
				return 0;
			
			if( posn >= size )
				return -1;

			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );
			int actual = (int)actualL;
			logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inGrain = (int)(grainSizeBytes - gOffset);
				int n = Math.min( left, inGrain );
				if( logger.isDebugEnabled() )
					logger.debug( "inGrain left n " + inGrain + " " +
								  left + " "+n);
				long gte = grainTables[gdIndex][gtIndex];
				if( logger.isDebugEnabled() )
					logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				int nin;
				if( gte == 0 ) {
					if( pis != null ) {
						nin = pis.read( ba, off+total, n );
						// look: test nin == n
					} else {
						// have a zero grain pre-allocated, fill from it...
						System.arraycopy( zeroGrain, 0, ba, off+total, n );
						nin = n;
					}
				} else {
					/*
					  physical file location is sector lookup from
					  current grainTableEntry, plus our offset into
					  that grain..
					*/
					long seek = gte * Constants.SECTORLENGTH + gOffset;
					raf.seek( seek );
					nin = raf.read( ba, off+total, n );
					// LOOK: test nin == n
					if( logger.isDebugEnabled() ) {
						logger.debug( "SEIS.read[BII: " +
									  " " + n + " " + nin + " " + total );
					}
					// align the file pointer of the parent, if any...
					if( pis != null )
						pis.skip( nin );
				}
				total += nin;
				posn += nin;
				dPos();
			}
			return total;
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

			long imageOffset = origin + posn;
			gdIndex = (int)(imageOffset / grainTableCoverageBytes);
			// this next operation MUST be done in long space, NOT int...
			gtIndex = (int)((imageOffset - grainTableCoverageBytes * gdIndex) /
							grainSizeBytes);
			gOffset = (int)(imageOffset % grainSizeBytes);

			if( logger.isDebugEnabled() )
				logger.debug( "gdIndex: " + gdIndex +
							  " gtIndex: " + gtIndex +
							  " gOffset: " + gOffset );
		}

	
		private final RandomAccessFile raf;
		private final long origin, size;
		private long posn;
		private int gdIndex, gtIndex, gOffset;
		private InputStream pis;
	}
	
	class SparseExtentRandomAccessVolume extends AbstractRandomAccessVolume {
		SparseExtentRandomAccessVolume() throws IOException {
			raf = new RandomAccessFile( source, "rw" );
			origin = 0;
			size = size();
			posn = 0;
			dPos();
		}

		@Override
		public void close() throws IOException {
			raf.close();
		}

		@Override
		public long length() throws IOException {
			return size;
		}

		@Override
		public void seek( long s ) throws IOException {
			//		log.debug( getGeneration() + ".seek " + s );
			// according to java.io.RandomAccessFile, no restriction on seek
			posn = s;
			dPos();
		}
		
		@Override
		public int read() throws IOException {
			if( posn >= size )
				return -1;
			
			int result;
			boolean parentRead = false;
			long gte = grainTables[gdIndex][gtIndex];
			if( gte == 0 ) {
				if( pis != null ) {
					result = pis.read();
					parentRead = true;
				} else {
					result = 0;
				}
			} else {
				long seek = gte * Constants.SECTORLENGTH + gOffset;
				raf.seek( seek );
				result = raf.read();
			}

			/*
			  If the parent did the read, its position update will
			  have been occurred.  If not, call skip on the parent,
			  which will do so.
			*/
			if( pis != null && !parentRead ) {
				pis.skip( 1 );
			}
			posn += 1;
			dPos();
			return result;

		}

		/**
		   For the array read, we shall attempt to satisy the length
		   requested, even if it is takes us many reads (of the
		   physical file) from different blocks to do so.  While the
		   contract for InputStream is that any read CAN return < len
		   bytes, for InputStreams backed by file data, users probably
		   expect len bytes back (fewer of course if eof).

		   Further, when using this class with our
		   VirtualDiskFS/Fuse4j/fuse system to expose the vdi to
		   fuse, fuse states that the callback read operation is
		   REQUIRED to return len bytes if they are available
		   (i.e. not read past eof)
		*/
		   
		@Override
		public int read( byte[] ba, int off, int len ) throws IOException {
			// checks from the contract for InputStream...
			if( ba == null )
				throw new NullPointerException();
			if( off < 0 || len < 0 || off + len > ba.length )
				throw new IndexOutOfBoundsException();
			if( len == 0 )
				return 0;
			
			if( posn >= size )
				return -1;

			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );
			int actual = (int)actualL;
			logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inGrain = (int)(grainSizeBytes - gOffset);
				int n = Math.min( left, inGrain );
				logger.debug( "inGrain left n " + inGrain + " " + left + " "+n);
				long gte = grainTables[gdIndex][gtIndex];
				logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				int nin;
				if( gte == 0 ) {
					if( pis != null ) {
						nin = pis.read( ba, off+total, n );
						// look: test nin == n
					} else {
						// have a zero grain pre-allocated, fill from it...
						System.arraycopy( zeroGrain, 0, ba, off+total, n );
						nin = n;
					}
				} else {
					/*
					  physical file location is sector lookup from
					  current grainTableEntry, plus our offset into
					  that grain..
					*/
					long seek = gte * Constants.SECTORLENGTH + gOffset;
					raf.seek( seek );
					nin = raf.read( ba, off+total, n );
					// LOOK: test nin == n
					if( logger.isDebugEnabled() ) {
						logger.debug( "SEIS.read[BII: " +
									  " " + n + " " + nin + " " + total );
					}
					// align the file pointer of the parent, if any...
					if( pis != null )
						pis.skip( nin );
				}
				total += nin;
				posn += nin;
				dPos();
			}
			return total;
		}

		@Override
		public void write( int b ) throws IOException {
			if( posn >= size )
				return;

			// grain table entry...
			long gte = grainTables[gdIndex][gtIndex];
			if( gte == 0 ) {
				gte = nextFreeGrain();
				grainTables[gdIndex][gtIndex] = gte;
				writeGrainTables();
			}
			
			// need long operands to the product, to avoid overflow...
			long seek = gte * Constants.SECTORLENGTH + gOffset;
			raf.seek( seek );
			raf.write( b );
			posn += 1;
			dPos();
		}

		/**
		   For the array write, we shall attempt to satify the length
		   requested, even if it is takes us many writes (of the
		   physical file) from different blocks to do so.
		*/
		   
		@Override
		public void write( byte[] ba, int off, int len ) throws IOException {

			logger.debug( "Write.[BII: " + off + " " + len );

			// checks from the contract for RandomAccessFile...
			if( ba == null )
				throw new NullPointerException();
			if( off < 0 || len < 0 || off + len > ba.length ) {
				logger.warn( ba.length + " " + off + " "+ len );
				throw new IndexOutOfBoundsException();
			}
			if( len == 0 )
				return;
			
			if( posn >= size ) {
				return;
			}
			
			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );
			int actual = (int)actualL;
			//logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inGrain = (int)(grainSizeBytes - gOffset);
				int n = Math.min( left, inGrain );
				long gte = grainTables[gdIndex][gtIndex];
				logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				if( gte == 0 ) {
					gte = nextFreeGrain();
					grainTables[gdIndex][gtIndex] = gte;
					writeGrainTables();
				}
				// need long operands to the product, to avoid overflow...
				long seek = gte * Constants.SECTORLENGTH + gOffset;
				raf.seek( seek );
				raf.write( ba, off+total, n );
				total += n;
				posn += n;
				dPos();
			}
		}

		
		/**
		   Called whenever the local posn changes value.
		*/
		private void dPos() {
			/*
			  This is the crux of the sparse random access. We map
			  logically map the 'file pointer' on the volume to a
			  grain table, table entry and grain offset.  To do the
			  next read/write, we then calc a physical seek offset
			  into the actual file and read/write.

			  According to java.io.RandomAccessFile, a file posn
			  is permitted to be past its size limit.  We cannot
			  map such a posn to the grain info of course...
			*/
			if( posn >= size )
				return;

			long imageOffset = origin + posn;
			gdIndex = (int)(imageOffset / grainTableCoverageBytes);
			// this next operation MUST be done in long space, NOT int...
			gtIndex = (int)((imageOffset - grainTableCoverageBytes * gdIndex) /
							grainSizeBytes);
			gOffset = (int)(imageOffset % grainSizeBytes);

			if( logger.isDebugEnabled() )
				logger.debug( "gdIndex: " + gdIndex +
							  " gtIndex: " + gtIndex +
							  " gOffset: " + gOffset );
		}

		private final RandomAccessFile raf;
		private final long origin, size;
		private long posn;
		private int gdIndex, gtIndex, gOffset;
		private InputStream pis;
	}
	
	File source;
	SparseExtentHeader header;
	
	long[] grainDirectory;
	long[][] grainTables;
	byte[] zeroGrain;

	long grainMarkersOrigin;
	
	// derivable from header properties. Used by streaming operations...
	long grainSizeBytes, grainTableCoverageBytes;

	Logger logger;
	
	/*
	  The spec says that the default grain size is 2^7 = 128 sectors,
	  or 64KB.  So prealloc the '64KB zero grain', so that all
	  SparseExentInputStreams can use it (so saving on per-disk/stream
	  copies)
	*/
	static private final byte[] ZEROGRAIN_128 =
		new byte[128*Constants.SECTORLENGTH];

	static class Marker {
		Marker( long val, long size, int type ) {
			this.val = val;
			this.size = size;
			this.type = type;
		}
	
		long val;
		long size;
		int type;

		static final int SIZEOF = 8 + 4 + 4;

		static final int TYPE_EOS = 0;
		static final int TYPE_GT = 1;
		static final int TYPE_GD = 2;
		static final int TYPE_FOOTER = 3;
		
	}

	List<Marker> markers;
}

// eof
