package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;

/**
 * Superclass for NormalDisk and DifferenceDisk, which make use of the
 * BlockMap structure.  This class just maintains the BlockMap, making
 * it available to NormalDisk, DifferenceDisk.
 */

public abstract class DynamicDisk extends VDIDisk {
	
	protected DynamicDisk( File f, VDIHeader h ) {
		super( f, h );
	}

	@Override
	long contiguousStorage() {
		return header.blockSize();
	}
}

// eof
