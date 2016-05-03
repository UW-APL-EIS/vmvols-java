package edu.uw.apl.vmvols.model.vmware;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.io.EndianUtils;

/**
 * @author Stuart Maclean.
 *
 * See model/doc/vmware/vmdk_specs.pdf for description of the VMDK format.
 */

public class GrainDirectory {

	public GrainDirectory( byte[] raw ) {
		gdes = new long[raw.length/4];
		for( int i = 0; i < gdes.length; i++ ) {
			long gt = EndianUtils.readSwappedUnsignedInteger( raw, 4*i );
			gdes[i] = gt;
		}
	}

	public String paramString() {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter( sw );
		for( int i = 0; i < gdes.length; i++ ) {
			pw.printf( "%d %08x\n", i, gdes[i] );
		}
		return sw.toString();
	}

	/**
	 * A Grain Directory holds fileOffets of GrainTables, each a uint32.
	 * A Grain Directory entry may be 0 (or -1??), denoting no grain table
	 * needed for that section of the virtual data (zeros??)
	 */
	final long[] gdes;
}

// eof

		
	