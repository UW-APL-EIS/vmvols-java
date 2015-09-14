package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;
import edu.uw.apl.vmvols.model.Utils;

/**
 * @author Stuart Maclean
 *
 * Preallocated base image file of a fixed size.
 * VDI_IMAGE_TYPE_FIXED,
 */

public class FixedDisk extends VDIDisk {

	protected FixedDisk( File f, VDIHeader h ) {
		super( f, h );
	}
	
	@Override
	long contiguousStorage() {
		return size();
	}

	@Override
	public InputStream getInputStream() throws IOException {
		/*
		  The entire embedded disk is located at dataOffset,
		  contiguously.  Note how we do not even bother to load/read
		  the block map.  For a FixedDisk, the blockmap is the
		  identity, modulo the dataOffset
		*/
		FileInputStream fis = new FileInputStream( source );
		Utils.skipFully( fis, header.dataOffset() );
		return fis;
	}

	@Override
	public RandomAccessVirtualDisk getRandomAccess( boolean writable )
		throws IOException {
		return new FixedDiskRandomAccess( writable );
	}

	class FixedDiskRandomAccess extends RandomAccessVirtualDisk {
		FixedDiskRandomAccess( boolean writable ) throws IOException {
			super( size() );
			String mode = writable ? "rw" : "r";
			raf = new RandomAccessFile( source, mode );
			raf.seek( header.dataOffset() );
		}

		@Override
		public void close() throws IOException {
			raf.close();
		}

		@Override
		public void seek( long s ) throws IOException {
			/*
			  According to java.io.RandomAccessFile, no restriction on
			  seek.  That is, seek posn can be -ve or past eof
			*/
			raf.seek( header.dataOffset() + s );
			posn = s;
		}


		/**
		   For the array read, we shall attempt to satisy the length
		   requested, even if it is takes us many reads (of the
		   physical file) from different blocks to do so.  While the
		   contract for InputStream asserts that any read <em>can</em>
		   return < len bytes, for InputStreams backed by file data,
		   users probably expect len bytes back (fewer of course if
		   eof).

		   Further, when using this class with our
		   VirtualMachineFS/Fuse4j/fuse system to expose the vdi to
		   fuse, fuse states that the callback read operation is
		   <b>required</b> to return len bytes if they are available
		   (i.e. not read past eof)
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

		   LOOK: then why have we only one write ???  THIS NEEDS
		   FINISHING if/when we ever get serious about supported
		   virtual disk writes.  For our forensics-oriented interests,
		   read access is all we need.
		*/
		   
		@Override
		public void writeImpl( byte[] ba, int off, int len )
			throws IOException {

			log.debug( "Write.[BII: " + off + " " + len );

			raf.write( ba, off, len );
			if( log.isDebugEnabled() ) {
				log.debug( getGeneration() + ".write " + posn );
			}
			posn += len;
		}

		private final RandomAccessFile raf;
	}
}

// eof
