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

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author Stuart Maclean
 *
 * VMware virtual disks contain a section of meta-data called a
 * Descriptor.  This class is the object representation of
 * such. Descriptors can also appear in standalone files, i.e. files
 * containing JUST a Descriptor.
 */

public class Descriptor {

	/*
	static Descriptor parse( VMDKHeader h, File f ) throws IOException {
		byte[] ba = new byte[(int)h.descriptorSize()];
		RandomAccessFile raf = new RandomAccessFile( f, "r" );
		raf.seek( h.descriptorOffset() * 512 );
		raf.readFully( ba );
		raf.close();
		return new Descriptor( ba );
	}
	*/
	
	Descriptor( byte[] ba ) {
		String s = null;
		try {
			s = new String( ba, "ISO-8859-1" );
		} catch( UnsupportedEncodingException never ) {
		}
		data = s;
		BufferedReader br = new BufferedReader( new StringReader( data ) );
		String line;
		try {
			while( (line = br.readLine()) != null ) {
				line = line.trim();
				if( line.length() == 0 || line.startsWith( "#" ) )
					continue;
				Matcher m = reType.matcher( line );
				if( m.matches() ) {
					type = m.group(1);
					continue;
				}
				m = reCID.matcher( line );
				if( m.matches() ) {
					cid = m.group(1);
					continue;
				}
				m = reUUIDImage.matcher( line );
				if( m.matches() ) {
					uuidImage = UUID.fromString( m.group(1) );
					continue;
				}
				m = reUUIDParent.matcher( line );
				if( m.matches() ) {
					uuidParent = UUID.fromString( m.group(1) );
					continue;
				}
				m = reParentFileNameHint.matcher( line );
				if( m.matches() ) {
					parentFileNameHint = m.group(1);
					continue;
				}
			}
		} catch( IOException never ) {
		}
	}

	@Override
	public String toString() {
		return data;
	}
	
	public String getCreateType() {
		return type;
	}

	public String getCID() {
		return cid;
	}
	
	public String getParentFileNameHint() {
		return parentFileNameHint;
	}
	
	// createType="monolithicSparse"
	static final Pattern reType = Pattern.compile
		( "createType=\"([A-Za-z]+)\"" );

	// CID=fe21c26a
	static final Pattern reCID = Pattern.compile
		( "CID=(\\p{XDigit}+)" );

	// parentFileNameHint="path/to/vmware/Windows 7 x64/Windows 7 x64.vmdk"
	static final Pattern reParentFileNameHint = Pattern.compile
		( "parentFileNameHint=\"([^\"]+)\"" );


	// e1246c7c-05dd-48c5-aa5b-5ad44ce0c13e
	static final String REUUID =
		"\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-" +
		"\\p{XDigit}{12}";

	static private final Pattern reUUIDImage = Pattern.compile
		( "ddb\\.uuid\\.image=\"(" + REUUID + ")\"" );

	static private final Pattern reUUIDParent = Pattern.compile
		( "ddb\\.uuid\\.parent=\"(" + REUUID + ")\"" );
	
	final String data;
	String type;

	// Should be present in all (host-based) VMDK disks, used for our ID
	String cid;
	
	/*
	  Only present for VMDK virtual disks created by VMware products,
	  e.g. Workstation, and only present then if this disk is the
	  product of a Snapshot operation. Used by VMware to provide
	  parent-child linkage across Snapshots.
	*/

	String parentFileNameHint;

	/*
	  Only present for VMDK virtual disks created by VirtualBox.  Used
	  by VirtualBox to provide parent-child linkage across Snapshots.
	*/
	UUID uuidImage, uuidParent;
}

// eof
