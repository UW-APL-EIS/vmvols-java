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

import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;

/**
 * @author Stuart Maclean
 *
   Disk type 1:

   A growable virtual disk split into 2GB host files (called "split sparse").

   NOTE: This disk type not yet implemented, TODO
 */

public class SplitSparseDisk extends VMDKDisk {

	SplitSparseDisk( File f, Descriptor d ) {
		super( f, d );
	}

	@Override
	public long size() {
		// to do
		return 0L;
	}


	@Override
	public long contiguousStorage() {
		return 0L;
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		throw new IllegalStateException( "To Implement: SplitSparseDisk..." );
	}

	@Override
	public RandomAccessVirtualDisk getRandomAccess( boolean writable )
		throws IOException {
		throw new IllegalStateException( "To Implement: SplitSparseDisk..." );
	}
}

// eof
