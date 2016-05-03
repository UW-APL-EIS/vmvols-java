package vmvolssamples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;
import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;

/**
 * @author Stuart Maclean.
 *
 * A vmvols sample: how to open a VirtualBox virtual disk file and
 * perform I/O on it.  We do just two forms of read here (and no writes)
 *
 * 1) Obtain an InputStream
 *
 * 2) Obtain a RandomoAccessVirtualDisk.  This is essentially an
 * InputStream with the added ability to seek to arbitrary locations.
 *
 * The latter is used in our fuse-enabling class
 * edu.uw.apl.vmvols.fuse.VirtualMachineFilesystem.  We let
 * Fuse4j/fuse calculate the seek positionsand byte read counts, we
 * just provide access to the data.
 *
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
			List<VirtualDisk> vds = vm.getActiveDisks();
			VirtualDisk vd = vds.get(0);
			showInputStreamUsage( vd );
			showRandomAccessUsage( vd );
		} catch( IOException ioe ) {
			System.err.println( ioe );
			System.exit(1);
		}			
	}

	static void showInputStreamUsage( VirtualDisk vd )
		throws IOException {

		InputStream is = vd.getInputStream();

		// Arbitrary skip...
		is.skip( 100 );

		// Arbitrary read...
		byte[] bs = new byte[1024];
		int nin = is.read( bs );
		
		is.close();
	}

	static void showRandomAccessUsage( VirtualDisk vd )
		throws IOException {

		boolean writable = false;
		RandomAccessVirtualDisk ra = vd.getRandomAccess( writable );

		// Arbitrary skip...
		ra.skip(1024);

		// Arbitrary read...
		byte[] bs = new byte[1024];
		int nin = ra.read( bs );

		// Arbitrary seek...
		ra.seek( 0L );
		
		ra.close();
		
	}
	
}

// eof

	

