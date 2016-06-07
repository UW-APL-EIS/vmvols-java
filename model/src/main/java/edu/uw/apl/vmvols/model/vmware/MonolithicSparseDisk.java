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


import java.io.DataInput;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.EndianUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;

/**
 * @author Stuart Maclean.

 * Disk type 0:

   A growable VMware virtual disk contained in a single host file,
   termed a "monolithic sparse" virtual disk.  Monolithic meaning
   'data for the entire virtual disk is contained in a single host
   file', and sparse meaning 'only allocates grains of data in the
   host file as corresponding data areas in the virtual disk are read
   or written'.
 */

public class MonolithicSparseDisk extends VMDKDisk {

	MonolithicSparseDisk( File f, SparseExtentHeader seh, Descriptor d ) {
		super( f, d );
		extent = new SparseExtent( f, seh );
	}

	@Override
	public long size() {
		return extent.size();
	}

	@Override
	public long contiguousStorage() {
		return extent.grainSizeBytes;
	}
	
	@Override
	public InputStream getInputStream() throws IOException {
		InputStream parentIS = parent == null ? null :
			parent.getInputStream();
		return extent.getInputStream( parentIS );
	}

	@Override
	public RandomAccessVirtualDisk getRandomAccess( boolean writable )
		throws IOException {
		RandomAccessVirtualDisk parentRA = parent == null ? null :
			parent.getRandomAccess( writable );
		return extent.getRandomAccess( parentRA );
	}

	private final SparseExtent extent;
}

// eof
