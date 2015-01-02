package edu.uw.apl.vmvols.cli;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.cli.*;
import org.apache.log4j.LogManager;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;

/**
 * Open the identified virtual disk, and stream its entire logical
 * contents to stdout.  By logical, we mean the disk content as seen
 * by the VM were it to be powered up.  We do NOT mean the physical
 * content of actual file on disk on the host (for which plain cat
 * would suffice).  Hence the name 'catvd', mimics the Unix tool cat.
 *
 * For a VirtualBox disk image file:
 *
 * $ CatVD /path/to/some/file.vdi
 *
 * For a VMWare disk image file:
 *
 * CatVD /path/to/some/file.vmdk
 *
 * For a VM directory rather than an identified file:
 *
 * CatVD /path/to/some/vmdir
 *
 * This last invocation will only work if the VM has a single hard
 * drive, else command is ambiguous.
 */

public class CatVD {

	static public void main( String[] args ) {

		final String usage = "Usage: " + CatVD.class.getName() +
			" /path/to/virtualDiskFile (or vmdir)";
		if( args.length < 1 ) {
			System.err.println( usage );
			System.exit(1);
		}

		File f = new File( args[0] );
		if( !f.exists() ) {
			System.err.println( f + ": no such file or directory" );
			System.exit(1);
		}

		try {
			VirtualMachine vm = VirtualMachine.create( f );
			if( vm == null )
				throw new IllegalArgumentException( "Unknown vd file: " + f );
			List<VirtualDisk> disks = vm.getActiveDisks();
			if( disks.size() > 1 ) {
				throw new IllegalArgumentException
					( "VM created from " + f + " has " + disks.size() +
					  " disks, specify one." );
			}
			VirtualDisk vd = disks.get(0);
			InputStream is = vd.getInputStream();
			byte[] ba = new byte[1024*1024];
			while( true ) {
				int nin = is.read( ba );
				if( nin < 1 )
					break;
				System.out.write( ba, 0, nin );
			}
			is.close();
		} catch( IOException ioe ) {
			System.err.println( ioe );
		}
	}
}

// eof
