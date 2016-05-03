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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;

/**
 * @author Stuart Maclean.
 *
 * A stream-optimized VMware virtual disk is one where grains
 * (nominally, 64K of data) are each compressed, for the purposes of
 * moving the data across networks.  The MonolithicStreamOptimizedDisk
 * is the .vmdk variant you get when
 *
 * Exporting a VM to .ovf/.ova file formats
 *
 * Build a VM with Vagrant (@see https://www.vagrantup.com)
 *
 * Obviously we cannot write to a monolithicstreamoptimizeddisk, since
 * it would need unzipping on the fly, not something we are interested
 * in.
 */

public class MonolithicStreamOptimizedDisk extends VMDKDisk {

	MonolithicStreamOptimizedDisk( File f, SparseExtentHeader seh,
								   Descriptor d ) {
		super( f, d );
		extent = new StreamOptimizedSparseExtent( f, seh );
	}

	@Override
	public long size() {
		return extent.size();
	}

	@Override
	public long contiguousStorage() {
		return extent.grainSizeBytes;
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		return extent.getInputStream();
	}

	@Override
	public RandomAccessVirtualDisk getRandomAccess( boolean writable )
		throws IOException {
		// LOOK: check writable FALSE, error else
		return extent.getRandomAccess();
	}

	private final StreamOptimizedSparseExtent extent;

}

// eof
