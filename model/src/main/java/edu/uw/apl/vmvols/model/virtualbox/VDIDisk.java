package edu.uw.apl.vmvols.model.virtualbox;

import java.io.DataInput;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import edu.uw.apl.vmvols.model.VirtualDisk;

import org.apache.commons.io.EndianUtils;

/**
 * @author Stuart Maclean
 *
 * Abstract base class capturing commonalities of the three variants
 * of VirtualBox .vdi files: NormalDisk, FixedDisk, DifferenceDisk.
 
 * Our VirtualBox classes are pure-Java, no JNI/C bridge to any
 * underlying VirtualBox .so/.dlls.  We do all the file format parsing
 * locally.
 *
 * See also our local copy of VDICore.h (copied from the VirtualBox
 * codebase) for reference and VBoxManage for the public api...
 */

abstract public class VDIDisk extends VirtualDisk {

	/**
	  Note how we split the reading of a .vdi file into three
	  'phases'.  At VD construction, we just read in the header (and
	  its preceding pre-header).  The header alone tells us about any
	  parent-child relationship between files.

	  We also have a method to read in the blockmap, which is required
	  for phase three, which is reading in the actual dd data.

	  The main entry point to creation of VirtualDisks is VBoxManage.create,
	  which handles the child-parent relationships of Snapshots, etc
	*/

	protected VDIDisk( File f, VDIHeader h ) {
		super( f );
		header = h;
	}

	public VDIHeader getHeader() {
		return header;
	}

	/**
	 * Our 'entry point' into .vdi file processing.  Reads the header
	 * from a .vdi file and decides (from the type field) which type
	 * of vdi disk this is:
	 *
	 * dynamic (created by Vbox to grow as data written to the virtual disk)
	 *
	 * fixed (created by Vbox with full capacity immediately)
	 *
	 * difference (used for generations 2+, a result of Snapshot operations)
	 */
	static public VDIDisk readFrom( File hostVDIFile ) throws IOException {
		VDIHeader h = VDIHeaders.parse( hostVDIFile );
		VDIDisk result = null;
		switch( (int)h.imageType() ) {
		case VDI_IMAGE_TYPE_NORMAL:
			result = new NormalDisk( hostVDIFile, h );
			break;
		case VDI_IMAGE_TYPE_FIXED:
			result = new FixedDisk( hostVDIFile, h );
			break;
		case VDI_IMAGE_TYPE_DIFF:
			result = new DifferenceDisk( hostVDIFile, h );
			break;
		default:
			/*
			  Other formats we can't decipher. A 'paused VM' has a host
			  disk format we can't grok (boo!)
			*/
			throw new VDIException
				( "Image type not supported: " + h.imageType() );
		}
		return result;
	}
	
	/**
	 * Provide a hint to test apps as how to allocate their read buffers...
	 */
	abstract long contiguousStorage();

	@Override
	/**
	 * @return a name/id for this virtual disk, with a leading indicator that
	 * this is a VBox/.vdi file (since VMware also uses UUIDs)
	 */
	public String getID() {
		VirtualDisk base = getBase();
		return "VDI-" + base.getUUID();
	}
	
	@Override
	public long size() {
		return header.diskSize();
	}

	@Override
	public UUID getUUID() {
		return header.imageCreationUUID();
	}

	@Override
	public UUID getUUIDParent() {
		return header.imageParentUUID();
	}

	public int imageType() {
		return (int)header.imageType();
	}

	public long blockSize() {
		return header.blockSize();
	}

	public long blockCount() {
		return header.blockCount();
	}
	
	public long dataOffset() {
		return header.dataOffset();
	}

	@Override
	public int hashCode() {
		return header.imageCreationUUID().hashCode();
	}

	@Override
	public boolean equals( Object o ) {
		if( this == o )
			return true;
		if( !( o instanceof VDIDisk ) )
			return false;
		VDIDisk that = (VDIDisk)o;
		return this.header.imageCreationUUID().equals
			( that.header.imageCreationUUID() );
	}

	/**
	   Various sanity checks that the block map is consistent, and
	   that the real data referenced by the block map is indeed
	   available.

	   We have seen VM disk corruptions, like when we used our fuse
	   system while also having the VM powered up (and thus under VBox
	   control).  A bad idea!
	*/
	   
	private void checkBlockMap() {
		long fLength = getPath().length();
		long dto = dataOffset();
		long bs = blockSize();
		Set<Integer> s = new HashSet<Integer>();
		for( int bme : blockMap ) {

			// The bme may 'point' to no data at all...
			if( bme == VDI_IMAGE_BLOCK_FREE || bme == VDI_IMAGE_BLOCK_ZERO )
				continue;
			
			// 1: the bme could point past eof of true file
			long highestOffset = dto + bme * bs + bs;
			if( highestOffset > fLength ) {
				throw new VDIException
					( "BlockMapEntry too big: " + bme +
					  ". Corrupt vdi? " + getPath() );
			}
			// 2: duplicate bme..
			boolean isUnique = s.add( bme );
			if( !isUnique ) {
				log.warn( s );
				throw new VDIException
					( "BlockMapEntry duplicate: " + bme +
					  ". Corrupt vdi? " + getPath() );
			}
		}
	}
	
	protected void readBlockMap() throws IOException {
		/*
		  Only need to read the block map at most once, it is
		  invariant.  On the rare occasions when we do write to the
		  virtualdisk, we update the block map in both local memory
		  and out to the file, so the two are always synchronized.
		*/
		if( blockMap != null )
			return;
		
		int N = (int)header.blockCount();
		blockMap = new int[N];
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( header.blocksOffset() );
		byte[] ba = new byte[4*N];
		raf.readFully( ba );
		raf.close();

		// LOOK: We are assuming little-endian formats, seems to hold...
		for( int i = 0; i < N; i++ ) {
			blockMap[i] = (int)EndianUtils.readSwappedUnsignedInteger
				( ba, 4*i );
		}
		checkBlockMap();
	}

	protected void writeBlockMap() throws IOException {
		int N = (int)header.blockCount();
		byte[] ba = new byte[4*N];
		log.info( "Writing BlockMap for : " + source );
		for( int i = 0; i < N; i++ ) {
			EndianUtils.writeSwappedInteger( ba, 4*i, blockMap[i] );
		}
		RandomAccessFile raf = new RandomAccessFile( source, "rw" );
		raf.seek( header.blocksOffset() );
		raf.write( ba );
		raf.close();
	}

	// access only for test cases is this package...
	int[] getBlockMap() throws IOException {
		readBlockMap();
		return blockMap;
	}
	
	protected int nextFreeBlock() {
		int max = -1;
		for( int i = 0; i < blockMap.length; i++ ) {
			if( blockMap[i] == VDI_IMAGE_BLOCK_FREE ||
				blockMap[i] == VDI_IMAGE_BLOCK_ZERO )
				continue;
			if( blockMap[i] > max )
				max = blockMap[i];
		}
		return max+1;
	}

	protected int[] blockMap;

	protected final VDIHeader header;
		
	static public final String FILESUFFIX = "vdi";

	static public final FilenameFilter FILEFILTER = new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( FILESUFFIX );
			}
		};

	static public final int VDI_IMAGE_TYPE_NORMAL = 1;
	static public final int VDI_IMAGE_TYPE_FIXED  = 2;
	static public final int VDI_IMAGE_TYPE_DIFF   = 4;


	// #define VDI_IMAGE_BLOCK_FREE   ((VDIIMAGEBLOCKPOINTER)~0)
	static public final int VDI_IMAGE_BLOCK_FREE = ~0;

	// #define VDI_IMAGE_BLOCK_ZERO   ((VDIIMAGEBLOCKPOINTER)~1)
	static public final int VDI_IMAGE_BLOCK_ZERO = ~1;
}

// eof
