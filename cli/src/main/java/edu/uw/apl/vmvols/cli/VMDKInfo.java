package edu.uw.apl.vmvols.cli;

import java.io.*;

import edu.uw.apl.vmvols.model.Utils;
import edu.uw.apl.vmvols.model.vmware.*;

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



	