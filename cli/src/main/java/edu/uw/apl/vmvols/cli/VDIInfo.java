package edu.uw.apl.vmvols.cli;

import java.io.*;

import edu.uw.apl.vmvols.model.Utils;
import edu.uw.apl.vmvols.model.virtualbox.*;

public class VDIInfo {

	static public void main( String[] args ) {

		final String usage = "Usage: " + VDIInfo.class.getName() + " vdiFile";
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
			VDIDisk vdi = VDIDisk.readFrom( f );
			VDIHeader h = vdi.getHeader();
			System.out.println( h );

			if( verbose ) {
				InputStream is = vdi.getInputStream();
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



	