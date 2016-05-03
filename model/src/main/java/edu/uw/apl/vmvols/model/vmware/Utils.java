package edu.uw.apl.vmvols.model.vmware;

/**
 * @author Stuart Maclean
 *
 * Operations on VMware disk data
 */
public class Utils {

	static long asBytes( long grainSize ) {
		return grainSize * edu.uw.apl.vmvols.model.Constants.SECTORLENGTH;
	}
}

// eof
