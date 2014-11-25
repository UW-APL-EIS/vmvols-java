package edu.uw.apl.vmvols.model.virtualbox;

import java.util.UUID;

public interface VDIHeader {
	long imageType();
	long blocksOffset();
	long dataOffset();
	long blockSize();
	long blockCount();
	UUID imageCreationUUID();
	UUID imageParentUUID();
	long diskSize();
}

// eof
