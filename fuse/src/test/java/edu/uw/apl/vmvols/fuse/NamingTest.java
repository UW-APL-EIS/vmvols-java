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
package edu.uw.apl.vmvols.fuse;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.*;

/**
 * Our VirtualMachineFileSystem class uses regexes to parse/dissect a
 * path into components: vmname, generation, disk. Check here that the
 * regexs defined really do (or do not!) permit typical vmnames as
 * used in e.g. VirtualBox.
 *
 * @see VirtualMachineFileSystem
 */
public class NamingTest extends junit.framework.TestCase {

	public void testLiteral() {
		Matcher m = VirtualMachineFileSystem.NAMEP.matcher( "Win7_64" );
		assertTrue( m.matches() );
	}

	public void testLocalVBox() {
		File dir = new File( "data/VBox" );
		if( !dir.isDirectory() )
			return;
		File[] fs = dir.listFiles( new FileFilter() {
				public boolean accept( File pathName ) {
					return pathName.isDirectory();
				}
			} );
		for( File f : fs ) {
			System.out.println( f );
			Matcher m = VirtualMachineFileSystem.NAMEP.matcher( f.getName() );
			assertTrue( "" + f, m.matches() );
		}
	}
}

// eof
