package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import edu.uw.apl.vmvols.model.RandomAccessVolume;
import edu.uw.apl.vmvols.model.AbstractRandomAccessVolume;
import edu.uw.apl.vmvols.model.VirtualDisk;

/**
   From VDICore.h...
   
   Normal dynamically growing base image file. 
   VDI_IMAGE_TYPE_NORMAL = 1,

   Like the comment says, this is a base image which grows
   dynamically.  Not all blocks are allocated when the image is
   constructed.  A read of a 'missing' block results in a zero or
   'random' value, according to the block entry value for that block.
   In practice, 'random' means 'zero'.  */

public class NormalDisk extends DynamicDisk {
	
	protected NormalDisk( File f, VDIHeader h ) {
		super( f, h );

		/*
		  maintain random and zero blocks as an optimisation for all
		  reads which hit such a block
		*/
		if( header.blockSize() == 1024*1024 ) {
			randomBlock = RANDOMBLOCK_20;
			zeroBlock = ZEROBLOCK_20;
		} else {
			randomBlock = new byte[(int)header.blockSize()];
			for( int i = 0; i < randomBlock.length; i++ )
				randomBlock[i] = (byte)RANDOM;
			zeroBlock = new byte[randomBlock.length];
		}
	}

	//	@Override
	public String getID() {
		return "VBox-" + header.imageCreationUUID();
	}

	@Override
	public int getGeneration() {
		return 0;
	}

	@Override
	public VirtualDisk getGeneration( int i ) {
		if( i == 0 )
			return this;
		if( child != null )
			return child.getGeneration( i );
		throw new IllegalStateException( "NormalDisk.getGeneration query " + i);
	}

	@Override
	public VirtualDisk getActive() {
		if( child == null )
			return this;
		return child.getActive();
	}

	@Override
	public List<VirtualDisk> getAncestors() {
		return Collections.emptyList();
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		readBlockMap();
		return new NormalDiskInputStream();
	}
	
	@Override
	public RandomAccessVolume getRandomAccessVolume() throws IOException {
		readBlockMap();
		return new NormalDiskRandomAccessVolume();
	}

	class NormalDiskInputStream extends InputStream {
		NormalDiskInputStream()
			throws IOException {
			raf = new RandomAccessFile( source, "r" );
			origin = 0;
			size = size();
			posn = 0;
			dPos();

			block = new byte[(int)header.blockSize()];
			bmePrev = -1;
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
			if( n < 0 )
				return 0;
			long min = Math.min( n, size-posn );
			posn += min;
			dPos();
			return min;
	    }

		@Override
		public void close() throws IOException {
			raf.close();
		}

		@Override
		public int read() throws IOException {
			if( posn >= size )
				return -1;
			byte[] ba = new byte[1];
			// given that checked eof above, the read must return one byte
			int n = read( ba, 0, 1 );
			return ba[0] & 0xff;
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
			if( off < 0 || len < 0 || off + len > ba.length ) {
				logger.warn( ba.length + " " + off + " "+ len );
				throw new IndexOutOfBoundsException();
			}
			if( len == 0 )
				return 0;
			
			if( posn >= size ) {
				return -1;
			}
			
			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );
			int actual = (int)actualL;
			//logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inBlock = (int)(header.blockSize() - bOffset);
				int fromBlock = Math.min( left, inBlock );
				//logger.debug( "inBlock left n " + inBlock + " " + left + " "+n);
				int bme = blockMap[bIndex];
				//logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				int nin;
				switch( bme ) {
				case VDI_IMAGE_BLOCK_FREE:
					// have a random block pre-allocated, fill from it...
					System.arraycopy( randomBlock, 0, ba, off+total, fromBlock);
					break;
				case VDI_IMAGE_BLOCK_ZERO: 
					// have a zero block pre-allocated, fill from it...
					System.arraycopy( zeroBlock, 0, ba, off+total, fromBlock );
					break;
				default:
					/*
					  physical file location is sector lookup from
					  current blockMapEntry, plus our offset into
					  that block..
					*/
					// need long operands to the product, to avoid overflow...
					if( bme != bmePrev ) {
						// need long operands to the product, to avoid overfow
						long seek = header.dataOffset() +
							bme * header.blockSize();
						if( log.isTraceEnabled() ) {
							log.trace( getGeneration() + ".seek " + seek );
						}
						raf.seek( seek );
						raf.readFully( block );
						bmePrev = bme;
					}
					System.arraycopy( block, bOffset, ba, off+total, fromBlock);
				}
				total += fromBlock;
				posn += fromBlock;
				dPos();
			}
			return total;
		}

		/**
		   Called whenever the local posn changes value.
		*/
		private void dPos() {

			/*
			  This is the crux of the sparse reading. We map logically
			  map the 'file pointer' on the input stream to a block
			  map and block offset.  To do the next read, we then calc
			  a physical seek offset into the atual file and read.

			  According to java.io.RandomAccessFile, a file posn
			  is permitted to be past its size limit.  We cannot
			  map such a posn to the block map info of course...
			*/
			if( posn >= size )
				return;

			long imageOffset = origin + posn;
			bIndex = (int)(imageOffset / header.blockSize());
			bOffset = (int)(imageOffset % header.blockSize());

			if( log.isDebugEnabled() )
				log.debug( getGeneration() + " posn: "+ posn +
						   " bIndex: " + bIndex +
						   " bOffset: " + bOffset );
		}

	
		private final RandomAccessFile raf;
		private final long origin, size;
		private long posn;
		private int bIndex, bOffset;
		private final byte[] block;
		private int bmePrev;
	}

	class NormalDiskRandomAccessVolume extends AbstractRandomAccessVolume {
		NormalDiskRandomAccessVolume() throws IOException {
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
			log.debug( getGeneration() + ".seek "+ s );
			// according to java.io.RandomAccessFile, no restriction on seek
			posn = s;
			dPos();
		}

		public int readXXX() throws IOException {
			if( posn >= size )
				return -1;
			
			int result;
			// block map entry...
			int bme = blockMap[bIndex];
			switch( bme ) {
			case VDI_IMAGE_BLOCK_FREE:
				result = RANDOM;
				break;
			case VDI_IMAGE_BLOCK_ZERO:
				result = 0;
				break;
			default: {
				// need long operands to the product, to avoid overflow...
				long seek = header.dataOffset() +
					bme * header.blockSize() + bOffset;
				raf.seek( seek );
				result = raf.read();
			}
				break;
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
			if( off < 0 || len < 0 || off + len > ba.length ) {
				logger.warn( "IOOBE: " + ba.length + " " + off + " "+ len );
				throw new IndexOutOfBoundsException();
			}
			if( len == 0 )
				return 0;
			
			if( posn >= size ) {
				return -1;
			}
			
			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );
			int actual = (int)actualL;
			//logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inBlock = (int)(header.blockSize() - bOffset);
				int n = Math.min( left, inBlock );
				//logger.debug( "inBlock left n " + inBlock + " " + left + " "+n);
				int bme = blockMap[bIndex];
				logger.debug( "bme " + bme );
				int nin;
				switch( bme ) {
				case VDI_IMAGE_BLOCK_FREE:
					// have a random block pre-allocated, fill from it...
					System.arraycopy( randomBlock, 0, ba, off+total, n );
					nin = n;
					break;
				case VDI_IMAGE_BLOCK_ZERO: 
					// have a zero block pre-allocated, fill from it...
					System.arraycopy( zeroBlock, 0, ba, off+total, n );
					nin = n;
					break;
				default:
					/*
					  physical file location is sector lookup from
					  current blockMapEntry, plus our offset into
					  that block..
					*/
					// need long operands to the product, to avoid overflow...
					long seek = header.dataOffset() +
						bme * header.blockSize() + bOffset;
					logger.debug( "seek " + seek );
					raf.seek( seek );
					nin = raf.read( ba, off+total, n );
					if( log.isDebugEnabled() ) {
						log.debug( getGeneration() + ".read " + posn  +
								   " = " + nin );
					}
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
			
			// block map entry...
			int bme = blockMap[bIndex];
			if( bme == VDI_IMAGE_BLOCK_FREE ||
				bme == VDI_IMAGE_BLOCK_ZERO ) {
				bme = nextFreeBlock();
				blockMap[bIndex] = bme;
				writeBlockMap();
			}
			
			// need long operands to the product, to avoid overflow...
			long seek = header.dataOffset() +
				bme * header.blockSize() + bOffset;
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

			log.debug( "Write.[BII: " + off + " " + len );

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
				int inBlock = (int)(header.blockSize() - bOffset);
				int n = Math.min( left, inBlock );
				//logger.debug( "inBlock left n " + inBlock + " " + left + " "+n);
				int bme = blockMap[bIndex];
				if( bme == VDI_IMAGE_BLOCK_FREE ||
					bme == VDI_IMAGE_BLOCK_ZERO ) {
					bme = nextFreeBlock();
					blockMap[bIndex] = bme;
					writeBlockMap();
				}
				//logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				/*
				  physical file location is sector lookup from
				  current blockMapEntry, plus our offset into
				  that block..
				*/
				// need long operands to the product, to avoid overflow...
				long seek = header.dataOffset() +
					bme * header.blockSize() + bOffset;
				raf.seek( seek );
				raf.write( ba, off+total, n );
				if( log.isDebugEnabled() ) {
					log.debug( getGeneration() + ".write " + posn );
				}
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
			  According to java.io.RandomAccessFile, a file posn
			  is permitted to be past its size limit.  We cannot
			  map such a posn to the block map info of course...
			*/
			if( posn >= size )
				return;

			long imageOffset = origin + posn;
			bIndex = (int)(imageOffset / header.blockSize());
			bOffset = (int)(imageOffset % header.blockSize());

			if( log.isDebugEnabled() )
				log.debug( getGeneration() + ".posn: "+ posn +
						   " bIndex: " + bIndex +
						   " bOffset: " + bOffset );
		}

		private final RandomAccessFile raf;
		private final long origin, size;
		private long posn;
		private int bIndex, bOffset;
	}
	

	private final byte[] randomBlock;
	private final byte[] zeroBlock;
	
	/**
	 * Block marked as free is not allocated in image file, read from this
	 * block may returns any random data.
	 */
	//	#define VDI_IMAGE_BLOCK_FREE   ((VDIIMAGEBLOCKPOINTER)~0)

	/**
	 * Block marked as zero is not allocated in image file, read from this
	 * block returns zeroes.
	 */
	//	#define VDI_IMAGE_BLOCK_ZERO   ((VDIIMAGEBLOCKPOINTER)~1)
	
	// random. Well, 0 is random!
	static private final int RANDOM = 0;
	
	/*
	  In our experience, block size seems to be 1MB, so prealloc
	  random and zero blocks for all those DynamicDiskInputStreams
	  that can use them (so saving on per-stream copies)
	*/
	static private final byte[] RANDOMBLOCK_20 = new byte[1024*1024];
	static {
		for( int i = 0; i < RANDOMBLOCK_20.length; i++ )
			RANDOMBLOCK_20[i] = (byte)RANDOM;
	}
	
	static private final byte[] ZEROBLOCK_20 = new byte[1024*1024];
}

// eof
