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

import edu.uw.apl.vmvols.model.VirtualDisk;

import org.apache.commons.io.EndianUtils;
import org.apache.log4j.Logger;

/**
   see also ./VDICore.h for reference and VBoxManage for the public api...
*/
abstract public class VDIDisk extends VirtualDisk {

	/**
	  Note how we split the reading of a .vdi file into three
	  'phases'.  At VD construction, we just read in the header (and
	  its preceding preheader).  The header alone tells us about any
	  parent-child relationship between files.

	  We also have a method to read in the blockmap, which is required
	  for phase three, which is reading in the actual dd data.

	  The main entry point to creation of VirtualDisks is VBoxManage.create,
	  which handles the child-parent relationships of Snapshots, etc
	*/

	protected VDIDisk( File f, VDIHeader h ) {
		super( f );
		header = h;
		log = Logger.getLogger( getClass() );
	}

	public void setChild( DifferenceDisk dd ) {
		child = dd;
		dd.setParent( this );
	}

	// may be null, ok.  Assume caller knows this...
	public DifferenceDisk getChild() {
		return child;
	}
	
	public VDIDisk getBase() {
		return (VDIDisk)getGeneration(0);
	}

	static public VDIDisk create( File hostVDIFile ) throws IOException {
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
			throw new IllegalStateException
				( "Image type not supported: " + h.imageType() );
		}
		return result;
	}

	
	// provide a hint to test apps as how to allocate their read buffers...
	abstract long contiguousStorage();


	/**
	   An 'active' disk is that which VirtualBox would write to (and
	   start the read process chain at) in a running VM.  For a base
	   disk which has never been Snapshot'ed, the base is active.
	   Else some child snapshot is active.
	*/
	@Override
	public VirtualDisk getActive() {
		return child == null ? this : child.getActive();
	}

	@Override
	public String getID() {
		VDIDisk base = getBase();
		return "VBox-" + base.header.imageCreationUUID();
	}
	
	@Override
	public long size() {
		return header.diskSize();
	}


	//	@Override
	public File getPathBase()  {
		return null;//		return getBase().getPath();
	}

	public String imageParentUUID() {
		return header.imageParentUUID();
	}
	
	public String imageCreationUUID() {
		return header.imageCreationUUID();
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
	
	public int hashCode() {
		return header.imageCreationUUID().hashCode();
	}

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
	   Various sanity checks that block map is consistent, and that
	   the real data referenced by the block map is indeed available.

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
				throw new VDIException
					( "BlockMapEntry duplicate: " + bme +
					  ". Corrupt vdi? " + getPath() );
			}
		}
	}
	
	protected void readBlockMap() throws IOException {
		// only need to read the block map at most once, it is invariant...
		if( blockMap != null )
			return;
		
		int N = (int)header.blockCount();
		blockMap = new int[N];
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( header.blocksOffset() );
		byte[] ba = new byte[4*N];
		raf.readFully( ba );
		for( int i = 0; i < N; i++ ) {
			blockMap[i] = (int)EndianUtils.readSwappedUnsignedInteger
				( ba, 4*i );
		}
		raf.close();
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

	protected final Logger log;
	protected final VDIHeader header;
	
	// child may be null
	protected DifferenceDisk child;
	

	static public final FilenameFilter FILENAMEFILTER = new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( ".vdi" );
			}
		};


	static public final int VDI_IMAGE_TYPE_NORMAL = 1;
	static public final int VDI_IMAGE_TYPE_FIXED = 2;
	static public final int VDI_IMAGE_TYPE_DIFF = 4;


	static public final int VDI_IMAGE_BLOCK_FREE = ~0;
	static public final int VDI_IMAGE_BLOCK_ZERO = ~1;
}


// eof
