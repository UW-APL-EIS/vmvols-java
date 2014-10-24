package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import edu.uw.apl.vmvols.model.AbstractRandomAccessVolume;
import edu.uw.apl.vmvols.model.RandomAccessVolume;
import edu.uw.apl.vmvols.model.VirtualDisk;


/**
   From VDICore.h...

   Dynamically growing image file for differencing support.
   VDI_IMAGE_TYPE_DIFF,

   A DifferenceDisk must have an attached parent (which can be any of
   NormalDisk,FixedDisk or another DifferenceDisk).  Where the
   BlockMap in a DifferenceDisk shows a 'missing block', we refer to
   the parent disk for data for that block.

*/

public class DifferenceDisk extends DynamicDisk {
	
	protected DifferenceDisk( File f, VDIHeader h ) {
		super( f, h );
	}

	/**
	   An incorrect parent disk attachment would cause mayhem ;) So we
	   check the linkage before accepting the parent.  Likely that
	   this parent attachment is not done 'by hand', but you never
	   know...
	*/
	void setParent( VDIDisk d ) {
		String linkage = header.imageParentUUID();
		if( !linkage.equals( d.header.imageCreationUUID() ) ) {
			throw new IllegalArgumentException
				( "Linkage mismatch setting parent " + source + "," +
				  d.getPath() );
		}
		parent = d;
	}

	// not checking parent here, just return what we have...
	VDIDisk getParent() {
		return parent;
	}

	@Override
	public int getGeneration() {
		checkParent();
		return 1 + parent.getGeneration();
	}
	
	@Override
	public VirtualDisk getGeneration( int i ) {
		int g = getGeneration();
		if( g == i )
			return this;
		if( g > i ) {
			checkParent();
			return parent.getGeneration( i );
		}
		if( child != null )
			return child.getGeneration( i );
		throw new IllegalStateException( "DifferenceDisk.getGeneration query "
										 + i);
	}

	@Override
	public List<? extends VirtualDisk> getAncestors() {
		List<VDIDisk> result = new ArrayList<VDIDisk>();
		int g = getGeneration();
		for( int i = 0; i < g; i++ ) {
			VDIDisk vd = (VDIDisk)getGeneration(i);
			result.add( vd );
		}
		return result;
	}
	

	@Override
	public InputStream getInputStream() throws IOException {
		readBlockMap();
		checkParent();
		return new DifferenceDiskInputStream( parent.getInputStream() );
	}

	@Override
	public RandomAccessVolume getRandomAccessVolume()
		throws IOException {
		readBlockMap();
		checkParent();
		return new DifferenceDiskRandomAccessVolume
			( parent.getRandomAccessVolume() );
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
	class DifferenceDiskInputStream extends InputStream {
		/**
		 * @param pis a SeekableInputStream from the parent disk for
		 * the matching sector sequence as used here
		 */
		DifferenceDiskInputStream( InputStream pis )
			throws IOException {
			raf = new RandomAccessFile( source, "r" );
			origin = 0;
			size = size();
			this.pis = pis;
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
			pis.skip( n );
			if( n < 0 )
				return 0;
			long min = Math.min( n, size-posn );
			posn += min;
			dPos();
			return min;
	    }

		@Override
		public void close() throws IOException {
			pis.close();
			raf.close();
		}

		/**
		   We have to keep this stream's posn AND the parent disk
		   stream's posn aligned.  So if WE do the read, we force a
		   posn adjustment in the parent, by e.g. seeking.  If our
		   block is missing, we ask the parent for the read and we
		   align our posn accordingly.  Note though how we do NOT use
		   local skip/seek to do this as that would call the parent
		   too.
		*/
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

		   As per read(), we must keep our posn aligned with that
		   of our parent stream.
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
				case VDI_IMAGE_BLOCK_ZERO: 
					nin = pis.read( ba, off+total, fromBlock );
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
					nin = fromBlock;
					// align the parent with where our own posn will be...
					pis.skip( nin );
					break;
				}
				total += nin;
				posn += nin;
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
		private final InputStream pis;
		private final long origin, size;
		private long posn;
		private int bIndex, bOffset;
		private final byte[] block;
		private int bmePrev;
	}
	
	class DifferenceDiskRandomAccessVolume extends AbstractRandomAccessVolume {
		DifferenceDiskRandomAccessVolume( RandomAccessVolume prav )
			throws IOException {
			raf = new RandomAccessFile( source, "rw" );
			origin = 0;
			size = size();
			this.prav = prav;
			posn = 0;
			dPos();
		}

		@Override
		public void close() throws IOException {
			prav.close();
			raf.close();
		}

		@Override
		public long length() throws IOException {
			return size;
		}

		@Override
		public void seek( long s ) throws IOException {
			prav.seek( s );
			log.debug( getGeneration() + ".seek "+ s );
			// according to java.io.RandomAccessFile, no restriction on seek
			posn = s;
			dPos();
		}
		
		@Override
		public int read() throws IOException {
			if( posn >= size )
				return -1;
			
			int result;
			// block map entry...
			int bme = blockMap[bIndex];
			switch( bme ) {
			case VDI_IMAGE_BLOCK_FREE:
			case VDI_IMAGE_BLOCK_ZERO:
				result = prav.read();
				break;
			default: 
				// need long operands to the product, to avoid overflow...
				long seek = header.dataOffset() +
					bme * header.blockSize() + bOffset;
				raf.seek( seek );
				result = raf.read();
				// align the parent with where our own posn will be...
				prav.seek(posn+1);
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
				int n = Math.min( left, inBlock );
				//logger.debug( "inBlock left n " + inBlock + " " + left + " "+n);
				int bme = blockMap[bIndex];
				//logger.debug( "gte " + gte + " " + gdIndex + " "+ gtIndex );
				int nin;
				switch( bme ) {
				case VDI_IMAGE_BLOCK_FREE:
				case VDI_IMAGE_BLOCK_ZERO: 
					nin = prav.read( ba, off+total, n );
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
					raf.seek( seek );
					nin = raf.read( ba, off+total, n );
					if( log.isDebugEnabled() ) {
						log.debug( getGeneration() + ".read " + posn  +
								   " = " + nin );
					}
					// align the parent with where our own posn will be...
					prav.seek( posn + nin );
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

			// align the parent with where our own posn will be...
			prav.seek( posn + 1 );

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

				// align the parent with where our own posn will be...
				prav.seek( posn + n );

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
				log.debug( getGeneration() + " posn: "+ posn +
						   " bIndex: " + bIndex +
						   " bOffset: " + bOffset );
		}

		private final RandomAccessFile raf;
		private final RandomAccessVolume prav;
		private final long origin, size;
		private long posn;
		private int bIndex, bOffset;
	}

	private VDIDisk parent;
}

// eof
