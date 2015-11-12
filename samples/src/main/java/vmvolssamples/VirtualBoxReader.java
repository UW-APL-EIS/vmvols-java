package vmvolssamples;

import java.io.File;
import java.io.IOException;

import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;

/**
 * @author Stuart Maclean
 */
public class VirtualBoxReader {

	static public void main( String[] args ) {

		if( args.length < 1 ) {
			System.err.println( "Usage: " + VirtualBoxReader.class +
								" /path/to/virtualboxvmdir" );
			System.exit(1);
		}

		File f = new File( args[0] );
		if( !( f.isFile() && f.canRead() ) ) {
			System.err.println( "Cannot read: " + f );
			System.exit(1);
		}

		if( !VBoxVM.isVBoxVM( f ) ) {
			System.err.println
				( "Doesn't appear to be a VirtualBox VM Directory: " + f );
			System.exit(1);
		}

		try {
			VBoxVM vm = new VBoxVM( f );
		} catch( IOException ioe ) {
			System.err.println( ioe );
			System.exit(1);
		}			
	}
}

// eof

	

