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
package edu.uw.apl.vmvols.cli;

import java.io.*;

import edu.uw.apl.vmvols.model.Utils;
import edu.uw.apl.vmvols.model.vmware.*;

/**
 * @author Stuart Maclean
 *
 * Extract header and descriptor details of the .vmdk file supplied in
 * args[0], and print these details to stdout.
 *
 * If any second argument supplied (could be -v but anything will
 * do!?), read entire logical content of the file and md5sum that
 * content too.
 */

public class VMDKInfo {

	static public void main( String[] args ) {

		final String usage = "Usage: " + VMDKInfo.class.getName() + " vmdkFile";
		if( args.length < 1 ) {
			System.err.println( usage );
			System.exit(1);
		}
		boolean verbose = args.length > 1;
		
		File f = new File( args[0] );
		if( !f.exists() ) {
			System.err.println( f + ": no such file or directory" );
			System.exit(1);
		}

		try {
			SparseExtentHeader seh = VMDKDisk.locateSparseExtentHeader( f );
			System.out.println( seh.paramString() );
			Descriptor d = VMDKDisk.locateDescriptor( f );
			System.out.println( d );

			if( verbose ) {
				VMDKDisk disk = VMDKDisk.readFrom( f );
				InputStream is = disk.getInputStream();
				String md5 = Utils.md5sum( is );
				is.close();
				System.out.println( "MD5: " + md5 );
			}
		} catch( IOException ioe ) {
			System.err.println( f + "-> " + ioe );
		}
	}
}

// eof



	