package edu.uw.apl.vmvols.model.virtualbox;

interface VDIHeader {
	long imageType();
	long blocksOffset();
	long dataOffset();
	long blockSize();
	long blockCount();
	String imageCreationUUID();
	String imageParentUUID();
	long diskSize();
}

// eof
