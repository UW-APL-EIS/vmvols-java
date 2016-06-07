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

		
	