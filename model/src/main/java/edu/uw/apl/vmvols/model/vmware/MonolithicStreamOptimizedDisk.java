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

import org.apache.log4j.Logger;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;

/**
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
	public RandomAccessVirtualDisk getRandomAccess() throws IOException {
		return extent.getRandomAccess();
	}

	private final StreamOptimizedSparseExtent extent;

}

// eof
