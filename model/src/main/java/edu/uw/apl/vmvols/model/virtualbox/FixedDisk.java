package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.AbstractRandomAccessVolume;
import edu.uw.apl.vmvols.model.RandomAccessVolume;

/**
   Preallocated base image file of a fixed size.
   VDI_IMAGE_TYPE_FIXED,
*/

public class FixedDisk extends VDIDisk {

	protected FixedDisk( File f, VDIHeader h ) {
		super( f, h );
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
		throw new IllegalStateException( "FixedDisk.getGeneration query " + i);
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
	long contiguousStorage() {
		return size();
	}

	
	@Override
	public InputStream getInputStream() throws IOException {
		// the entire embedded disk is located at dataOffset, contiguously
		FileInputStream fis = new FileInputStream( source );
		long posn = 0;
		while( posn < header.dataOffset() ) {
			long skip = fis.skip( header.dataOffset() - posn );
			posn += skip;
		}
		return fis;
	}

	@Override
	public RandomAccessVolume getRandomAccessVolume() throws IOException {
		return new FixedDiskRandomAccessVolume();
	}

	class FixedDiskRandomAccessVolume extends AbstractRandomAccessVolume {
		FixedDiskRandomAccessVolume() throws IOException {
			raf = new RandomAccessFile( source, "rw" );
			raf.seek( header.dataOffset() );
			size = size();
			posn = 0;
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
			raf.seek( header.dataOffset() + s );
			posn = s;
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
				int nin = raf.read( ba, off+total, len-total );
				if( log.isDebugEnabled() ) {
					log.debug( getGeneration() + ".read " + posn  +
							   " = " + nin );
				}
				total += nin;
				posn += nin;
			}
			return total;
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

			raf.write( ba, off, len );
			if( log.isDebugEnabled() ) {
				log.debug( getGeneration() + ".write " + posn );
			}
			posn += len;
		}

		private final RandomAccessFile raf;
		private final long size;
		private long posn;
	}
}

// eof
