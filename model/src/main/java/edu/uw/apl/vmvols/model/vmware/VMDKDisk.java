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

import org.apache.commons.io.EndianUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.VirtualDisk;



/**
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

	public VMDKDisk( File f ) {
		super( f );
	}

	/**
	   Any given .vmdk MAY start with a SparseExtentHeader...
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
	   (quite why they use a common .vmdk suffix for wildly different
	   file formats is beyond me!)
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
			byte[] ba = FileUtils.readFileToByteArray( f );
			return new Descriptor( ba );
		}
	}
	
	static public VMDKDisk create( File f ) throws IOException {
		VMDKDisk result = null;

		Descriptor d = null;
		try {
			d = locateDescriptor( f );
			if( d == null )
				return null;
		} catch( IllegalStateException ise ) {
			return null;
		}
		String type = d.getCreateType().intern();
		if( false ) {
		} else if( "monolithicSparse" == type ) {
			SparseExtentHeader seh = locateSparseExtentHeader( f );
			result = new MonolithicSparseDisk( f, seh, d );
		} else if( "twoGbMaxExtentSparse" == type ) {
			result = new SplitSparseDisk( f, d );
		} else {
			// to finish..
			throw new IllegalStateException( "Disk type not supported: "
											 + type );
		}
		return result;
	}

	@Override
	public int getGeneration() {
		throw new IllegalStateException( "TODO" );
	}
	
	@Override
	public VirtualDisk getGeneration( int i ) {
		throw new IllegalStateException( "TODO" );
	}

	@Override
	public VirtualDisk getActive() {
		throw new IllegalStateException( "TODO" );
	}

	@Override
	public List<? extends VirtualDisk> getAncestors() {
		throw new IllegalStateException( "TODO" );
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
	//protected Descriptor descriptor;

	protected VMDKDisk parent, child;
	

	static public final FilenameFilter FILEFILTER =
		new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( ".vmdk" );
			}
		};
		
}

// eof
