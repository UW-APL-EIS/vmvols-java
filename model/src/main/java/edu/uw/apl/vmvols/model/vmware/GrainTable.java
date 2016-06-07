/**
 * Copyright © 2015, University of Washington
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the University of Washington nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE UNIVERSITY
 * OF WASHINGTON BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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

		
	