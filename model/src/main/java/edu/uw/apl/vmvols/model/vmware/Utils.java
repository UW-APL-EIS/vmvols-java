package edu.uw.apl.vmvols.model.vmware;

public class Utils {

	static long asBytes( long grainSize ) {
		return grainSize * edu.uw.apl.vmvols.model.Constants.SECTORLENGTH;
	}
}

// eof
