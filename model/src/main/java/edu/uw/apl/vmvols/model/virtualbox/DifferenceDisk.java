package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;
import edu.uw.apl.vmvols.model.VirtualDisk;


/**
 * @author Stuart Maclean
 *
   From VDICore.h...

   Dynamically growing image file for differencing support.
   VDI_IMAGE_TYPE_DIFF,

   A DifferenceDisk must have an attached parent (which can be any of
   NormalDisk, FixedDisk or another DifferenceDisk).  Where the
   BlockMap in a DifferenceDisk shows a 'missing block', we refer to
   the parent disk for data for that block.

   @see DynamicDisk
   @see VDIDisk
*/

public class DifferenceDisk extends DynamicDisk {
	
	protected DifferenceDisk( File f, VDIHeader h ) {
		super( f, h );
	}

	@Override
	public InputStream getInputStream() throws IOException {
		readBlockMap();
		//		checkParent();
		InputStream parentIS = parent.getInputStream();
		return new DifferenceDiskRandomAccess
			( (RandomAccessVirtualDisk)parentIS, false );
	}

	@Override
	public RandomAccessVirtualDisk getRandomAccess( boolean writable )
		throws IOException {
		readBlockMap();
		checkParent();
		/*
		  By definition, any non-active disk cannot be writable,
		  and a parent of this disk cannot be active.
		*/
		RandomAccessVirtualDisk parentRA = parent.getRandomAccess( false );
		return new DifferenceDiskRandomAccess( parentRA, writable );
	}

	private void checkParent() {
		if( parent == null )
			throw new IllegalStateException
				( "Missing parent for DifferenceDisk " + source );
	}
	/**
	   Whenever we change the file pointer (posn), either via a skip
	   or read, we must do so also in the parent, to keep them
	   'aligned'
	*/
	class DifferenceDiskRandomAccess extends RandomAccessVirtualDisk {
		DifferenceDiskRandomAccess( RandomAccessVirtualDisk parentRA,
									boolean writable )
			throws IOException {
			super( size() );
			this.parentRA = parentRA;
			String mode = writable ? "rw" : "r";
			raf = new RandomAccessFile( source, mode);
			dPos();
			block = new byte[(int)header.blockSize()];
			bmePrev = -1;
		}


		@Override
		public void close() throws IOException {
			parentRA.close();
			raf.close();
		}
		   
		@Override
		public void seek( long s ) throws IOException {
			/*
			  According to java.io.RandomAccessFile, no restriction on
			  seek.  That is, seek posn can be -ve or past eof
			*/
			parentRA.seek( s );
			posn = s;
			dPos();
		}

		@Override
		public long skip( long n ) throws IOException {
			parentRA.skip( n );
			long result = super.skip( n );
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

		   As per read(), we must keep our posn aligned with that
		   of our parent stream.
		*/
		   
		@Override
		public int readImpl( byte[] ba, int off, int len ) throws IOException {
			
			// Do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );

			// Cannot blindly coerce a long to int, result could be -ve
			int actual = actualL > Integer.MAX_VALUE ? Integer.MAX_VALUE :
				(int)actualL;

			//logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				int inBlock = (int)(header.blockSize() - bOffset);
				int fromBlock = Math.min( left, inBlock );
				//logger.debug( "inBlock left n " + inBlock + " " + left + " "+n);
				int bme = blockMap[bIndex];
				//logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				switch( bme ) {
				case VDI_IMAGE_BLOCK_FREE:
				case VDI_IMAGE_BLOCK_ZERO: 
					// LOOK: fromParent should ALWAYS == fromBlock
					int fromParent = parentRA.readImpl( ba, off+total,
														fromBlock );
					total += fromParent;
					posn += fromParent;
					break;
				default:
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
					total += fromBlock;
					posn  += fromBlock;
					parentRA.skip( fromBlock );
					break;
				}
				dPos();
			}
			return total;
		}

		@Override
		public void writeImpl( byte[] ba, int off, int len )
			throws IOException {

			log.debug( "Write.[BII: " + off + " " + len );

			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );

			// Cannot blindly coerce a long to int, result could be -ve
			int actual = actualL > Integer.MAX_VALUE ? Integer.MAX_VALUE :
				(int)actualL;
			
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

				// align the parent with where our own posn will be...
				parentRA.seek( posn + n );

				total += n;
				posn += n;
				dPos();
			}
		}

		/**
		 *   Called whenever the local posn changes value.
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

			long imageOffset = posn;
			bIndex = (int)(imageOffset / header.blockSize());
			bOffset = (int)(imageOffset % header.blockSize());

			if( log.isDebugEnabled() )
				log.debug( getGeneration() + " posn: "+ posn +
						   " bIndex: " + bIndex +
						   " bOffset: " + bOffset );
		}

		private final RandomAccessFile raf;
		private final RandomAccessVirtualDisk parentRA;
		private int bIndex, bOffset;
		private final byte[] block;
		private int bmePrev;
	}

	//	private VDIDisk parent;
}

// eof
