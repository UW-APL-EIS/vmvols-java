package edu.uw.apl.vmvols.model.vmware;

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
import java.util.UUID;

import org.apache.commons.io.EndianUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.VirtualDisk;

/**
 * @author Stuart Maclean
 *
 * Data and logic common to all VMWare virtual disks (or some at
 * least, not the VMFS ones!).  Hosted VMWare products, like
 * Workstation, Player, as well as VM builder tool 'packer' all
 * manipulate variants of these disks, which use the .vmdk file
 * suffix.
 *
 * See model/doc/vmware/vmdk_specs.pdf for description of the VMDK format.
 * That excellent description made this reader very straightforward. Only
 * one missing detail: no endianness mentioned.  We guessed little-endian
 * and guessed right ;)
 */

/**
 * See also
 * https://www.vmware.com/support/developer/vddk/vddk12_diskmanager.pdf
 * or the local copy at model/doc/vmware/vddk12_diskmanager.pdf.
 */

abstract public class VMDKDisk extends VirtualDisk {
	
	/**
	  Note how we split the reading of a .vmdk file into three
	  'phases'.  In the constructor, we just read in the header, which
	  is at the start of every .vmdk file.
	  
	  We also have a method to read in the 'grain directory' and
	  'grain tables', which are data structs required for phase three,
	  which is reading in the actual virtual disk 'dd' data, which is
	  stored in chunks called 'grains'.
	*/

	protected VMDKDisk( File f, Descriptor d  ) {
		super( f );
		descriptor = d;
	}

	/**
	 *  Any given .vmdk <em>may</em> start with a SparseExtentHeader...
	 */
	static public SparseExtentHeader locateSparseExtentHeader( File f )
		throws IOException {
		RandomAccessFile raf = new RandomAccessFile( f, "r" );
		if( raf.length() < SparseExtentHeader.SIZEOF )
			throw new IllegalStateException( "No SparseExtentHeader: " + f );
		byte[] ba = new byte[SparseExtentHeader.SIZEOF];
		raf.readFully( ba );
		raf.close();
		return new SparseExtentHeader( ba );
	}

	/**
	   To locate a descriptor, first try to locate a
	   SparseExtentHeader.  That header may provide offset and 'size
	   of' the enclosed Descriptor.  If this file has no
	   SparseExtentHeader, it may just be a standalone Descriptor
	   (quite why they use a common .vmdk filename suffix for wildly
	   different file contents/formats is beyond me!)
	*/
	static public Descriptor locateDescriptor( File f ) throws IOException {
		SparseExtentHeader seh = null;
		try {
			seh = locateSparseExtentHeader( f );
			long dOffset = seh.descriptorOffset;
			if( dOffset == 0 )
				return null;
			long dSize = seh.descriptorSize;
			RandomAccessFile raf = new RandomAccessFile( f, "r" );
			raf.seek( dOffset * Constants.SECTORLENGTH );
			byte[] ba = new byte[(int)(dSize * Constants.SECTORLENGTH)];
			raf.readFully( ba );
			raf.close();
			return new Descriptor( ba );
		} catch( IllegalStateException ise ) {
			/*
			  No SparseExtentHeader at start of data, assume standalone
			  descriptor file
			*/
			byte[] ba = FileUtils.readFileToByteArray( f );
			return new Descriptor( ba );
		}
	}

	/**
	 * Load a .vmdk file from disk into a VMDKDisk object.  Since
	 * VMDKDisk is itself abstract, we actually create a concrete
	 * subclass of it.  Which exact one is governed by details in the
	 * Descriptor object located in the file.
	 */
	static public VMDKDisk readFrom( File vmdkFile ) throws IOException {
		VMDKDisk result = null;

		Descriptor d = null;
		try {
			d = locateDescriptor( vmdkFile );
			if( d == null )
				return null;
		} catch( IllegalStateException ise ) {
			// LOOK: null is best result ???
			return null;
		}
		String type = d.getCreateType();
		if( type == null )
			return null;
		type = type.intern();
		if( false ) {
		} else if( "monolithicSparse" == type ) {
			SparseExtentHeader seh = locateSparseExtentHeader( vmdkFile );
			result = new MonolithicSparseDisk( vmdkFile, seh, d );
		} else if( "twoGbMaxExtentSparse" == type ) {
			result = new SplitSparseDisk( vmdkFile, d );
		} else if( "streamOptimized" == type ) {
			SparseExtentHeader seh = locateSparseExtentHeader( vmdkFile );
			result = new MonolithicStreamOptimizedDisk( vmdkFile, seh, d );
		} else {
			// to finish..
			throw new VMDKException( "Disk type not supported: " + type );
		}
		return result;
	}

	@Override
	public UUID getUUID() {
		return descriptor.uuidImage;
	}

	@Override
	public UUID getUUIDParent() {
		return descriptor.uuidParent;
	}
	
	@Override
 	public String getID() {
		VirtualDisk base = getBase();
		return "VMDK-" + base.getUUID();
	}
	
	/*
	public int hashCode() {
		return imageCreationUUID().hashCode();
	}

	public boolean equals( Object o ) {
		if( this == o )
			return true;
		if( !( o instanceof VirtualDisk ) )
			return false;
		VirtualDisk that = (VirtualDisk)o;
		return this.imageCreationUUID().equals( that.imageCreationUUID() );
	}
	*/

	/*
	@Override
	public long size() {
		return header.capacity * Constants.SECTORLENGTH;
	}

	@Override
	public String getID() {
		// LOOK: locate some form of id from the descriptor?
		return "VMDK-" + path.getName();
	}
	*/
	

	// provide a hint to apps as how to allocate their read buffers...
	abstract public long contiguousStorage();


	//protected VMDKHeader header;
	protected Descriptor descriptor;

	//protected VMDKDisk parent, child;
	
	static public final String FILESUFFIX = "vmdk";

	static public final FilenameFilter FILEFILTER =
		new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( FILESUFFIX );
			}
		};
		
}

// eof
