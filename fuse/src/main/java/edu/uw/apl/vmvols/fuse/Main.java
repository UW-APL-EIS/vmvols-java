package edu.uw.apl.vmvols.fuse;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.cli.*;

import fuse.FuseMount;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;
import edu.uw.apl.vmvols.model.virtualbox.VDIDisk;
import edu.uw.apl.vmvols.model.virtualbox.VBoxVM;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;


/**
 * @author Stuart Maclean
 *
 * Driver program for a Virtual Machine File System, a mechanism for
 * accessing virtual machine disk content from the host.  Uses
 * Fuse4J/fuse to allow arbitrary system call access to that content,
 * so allowing tools like Sleuthkit (and TSK4J) to traverse virtual
 * disks as well as physical ones.
 *
 * By default, only read access is set up, but if the -w option is
 * supplied as a cmd line arg, write access is allowed too.  Doing
 * this is dicey, you could render your virtual machines unbootable.
 * You most definitely do <b>not</b> want to use this option when the
 * virtual machine in question is powered up and running concurrently
 * with our mounting of it.  In fact, even for read access, the VM is
 * best powered off.
 *
 * Once the mount point is active, to unmount, run fusermount in a
 *  separate terminal, e.g.
 *
 * $ fusermount -u /path/to/mountPoint
 *
 *
 * @see VirtualMachineFileSystem
 */

 /*
   LOOK: Make the actual fuse mount multi-threaded, needs some work in
   VirtualMachineFileSystem.  Currently we are forcing a
   single-threaded fuse mount, which hampers concurrent access to the
   disk set we have mounted.  Will need some locking/synchronization,
   quite easy.
 */
   
public class Main {


	public static void main(String[] args) throws Exception {

		Options os = new Options();
		os.addOption( "h", false, "help" );
		os.addOption( "n", false,
					  "dryrun, show the would-be filesystem but skip the mount (default=false)" );
		os.addOption( "s", false, "include snapshots (false)" );
		os.addOption( "v", false, "verbose (false)" );
		os.addOption( "w", false,
					  "Allow disk writes. WARNING: Make sure VM is not active! (false)" );
		final String USAGE =
			Main.class.getName() +
			" [-h] [-n] [-s] [-v] [-w] vmDir+ mountPoint";
		final String HEADER = "";
		final String FOOTER = "";
		
		CommandLineParser clp = new PosixParser();
		CommandLine cl = null;
		try {
			cl = clp.parse( os, args );
		} catch( Exception e ) {
			System.err.println( e );
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}
		if( cl.hasOption( "h" ) ) {
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}
		
		boolean dryrun = cl.hasOption( "n" );
		boolean verbose = cl.hasOption( "v" );
		boolean includeSnapshots = cl.hasOption( "s" );
		boolean writable = cl.hasOption( "w" );

		args = cl.getArgs();
		if( args.length < 2 ) {
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}

		File mount = new File( args[args.length-1] );
		if( !mount.isDirectory() ) {
			System.err.println( "Mount point " + mount + " not a directory" );
			System.exit(-1);
		}
		
		List<VirtualMachine> vms = new ArrayList<VirtualMachine>();

		for( int i = 0; i < args.length - 1; i++ ) {
			File f = new File( args[i] );
			VirtualMachine vm = VirtualMachine.create( f );
			if( vm == null )
				continue;
			vms.add( vm );
		}
		
		if( vms.isEmpty() ) {
			System.err.println( "No virtual machine dirs" );
			printUsage( os, USAGE, HEADER, FOOTER );
			System.exit(1);
		}

		VirtualMachineFileSystem vmfs = new VirtualMachineFileSystem();
		vmfs.setIncludeSnapshots( includeSnapshots );
		vmfs.setReadOnly( !writable );
		
		for( VirtualMachine vm : vms ) {
			vmfs.add( vm );
		}

		int n = vmfs.volumeCount();
		if( n == 0 ) {
			System.err.println( "No valid volumes" );
			System.exit(1);
		}			
		
		if( verbose || dryrun ) {
			List<String> sorted = new ArrayList<String>
				( vmfs.volumesByPath.keySet() );
			Collections.sort( sorted );
			for( String s : sorted ) {
				VirtualDisk vv = vmfs.volumesByPath.get( s );
				File realPath = vv.getPath();
				File mountPath = new File( mount, s );
				System.out.println( realPath + " => " + mountPath );
			}
		}
		
		if( dryrun )
			return;
		
		if( !mount.isDirectory() ) {
			System.err.println( "Mount point missing: " + mount );
			System.exit(1);
		}

		if( writable ) {
			System.out.println( "Warning: mounting volumes read/write..." );
		}

		/*
		  And finally the fuse mount itself.  To unmount, run
		  fusermount in a separate terminal, e.g.

		  $ fusermount -u /path/to/mountPoint
		*/
		  
		System.out.println( "To umount, in a separate terminal 'fusermount -u "
							+ mount.getPath() + "'" );
		try {
			// -f == no fork, -s == single-threaded
			String[] fmArgs = { mount.getName(),
								"-f", "-s", "-oallow_root" };
			FuseMount.mount( fmArgs, vmfs, null );
		} catch (Exception e) {
            e.printStackTrace();
		}
	}

	static private void printUsage( Options os, String usage,
									String header, String footer ) {
		HelpFormatter hf = new HelpFormatter();
		hf.setWidth( 80 );
		hf.printHelp( usage, header, os, footer );
	}
}

// eof
