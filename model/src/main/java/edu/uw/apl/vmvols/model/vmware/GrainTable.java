package edu.uw.apl.vmvols.model.vmware;

import org.apache.commons.io.EndianUtils;

/**
 * @author Stuart Maclean.
 *
 * See model/doc/vmware/vmdk_specs.pdf for description of the VMDK format.
 */

public class GrainTable {

	public GrainTable( byte[] raw ) {
		if( raw.length != SIZEOF ) {
			throw new IllegalArgumentException
				( "GrainTable raw buffer length " +
				  raw.length + ", expected " + SIZEOF);
		}
		gtes = new long[512];
		for( int i = 0; i < gtes.length; i++ ) {
			long gte = EndianUtils.readSwappedUnsignedInteger( raw, 4*i );
			gtes[i] = gte;
		}
	}

	/**
	 * A Grain Table is supposed to ALWAYS have 512 entries, each a
	 * uint32, so the raw buffer we populate from should be 2K long
	 */
	static public final int SIZEOF = 2048;

	final long[] gtes;
}

// eof

		
	