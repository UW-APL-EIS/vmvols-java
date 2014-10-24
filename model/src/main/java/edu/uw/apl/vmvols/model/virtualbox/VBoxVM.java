package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Collections;

import org.apache.log4j.Logger;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;

/**
 * To locate the virtual disk(s) in a VirtualBox vm directory, do this:
 *
 * File theDir = ....
 * boolean b = VBoxVM.isVBox( theDir );
 * if( b ) {
 *   VBoxVM vm = new VBoxVM( theDir );
 *   List<VirtualDisk> disks = vm.getActiveDisks();
 * }
 *
 * An 'active' disk is one which would be written to were the VM to be
 * running.  It is the 'current snapshot'.  This API also supports
 * accessing any 'generation' (Snapshot) of any disk, all the way up
 * the 'base disk', which is held in the file VirtualBox created when
 * the VM was first built.
 */
public class VBoxVM extends VirtualMachine {

	static public boolean isVBox( File dir ) {
		if( !dir.isDirectory() )
			return false;
		File[] fs = dir.listFiles( VBOXFILE );
		// If we find a .vbox file we assert yes.
		if( fs.length > 0 )
			return true;
		fs = dir.listFiles( VDIDisk.FILENAMEFILTER );
		// If we find a .vdi file we assert yes.
		if( fs.length > 0 )
			return true;
		// other files that VBox uses????
		return false;
	}

	public VBoxVM( File vboxDir ) throws IOException {
		this( vboxDir, new File( vboxDir, "Snapshots" ) );
	}

	public VBoxVM( File vboxDir, File snapshotsDir ) throws IOException {
		
		log = Logger.getLogger( getClass() );
		dir = vboxDir;
		disks = new ArrayList<VDIDisk>(2);
		
		/*
		  step 1, locate all .vdi files in this dir. These are then
		  maintained as the 'base disks'.  We can then ask any of
		  these for its history, active disk, etc
		*/
		
		File[] vdis = vboxDir.listFiles( VDIDisk.FILENAMEFILTER );
		for( File vdi : vdis ) {
			VDIDisk vd = VDIDisk.create( vdi );
			disks.add( vd );
		}

		/*
		  step 2, locate all .vdi files in any Snapshots dir.  These
		  should all be DifferenceDisks
		*/
		List<DifferenceDisk> children = new ArrayList<DifferenceDisk>();
		if( snapshotsDir.isDirectory() ) {
			vdis = snapshotsDir.listFiles( VDIDisk.FILENAMEFILTER );
			for( File f : vdis ) {
				VDIDisk vd = null;
				try {
					vd = VDIDisk.create( f );
				} catch( VDIException parseFailure ) {
					log.warn( parseFailure );
					continue;
				}
				if( !( vd instanceof DifferenceDisk ) ) {
					log.warn( "Not a difference disk: " + f );
					continue;
				}
				DifferenceDisk dd = (DifferenceDisk)vd;
				children.add( dd );
			}
		}

		// step 3, form parent-child relationships...
		for( VDIDisk d : disks )
			locateChild( d, children );
	}

	@Override
	public String getName() {
		return dir.getName();
	}
	
	// the base disks are what we actually maintain...
	@Override
	public List<? extends VirtualDisk> getBaseDisks() {
		return disks;
	}

	/*
	@Override
	public List<? extends VirtualDisk> getDisks( int generation ) {
		List<VDIDisk> result = new ArrayList<VDIDisk>( disks.size() );
		for( VDIDisk d : disks ) {
			result.add( d.getGeneration( generation ) );
		}
		return result;
	}
	*/
	
	// any active disks are derivable from their base disk counterpart...
	@Override
	public List<? extends VirtualDisk> getActiveDisks() {
		List<VDIDisk> result = new ArrayList<VDIDisk>( disks.size() );
		for( VDIDisk d : disks ) {
			result.add( (VDIDisk)d.getActive() );
		}
		return result;
	}

	static private void locateChild( VDIDisk needle,
									 List<DifferenceDisk> haystack ) {
		String create = needle.imageCreationUUID();
		
		for( DifferenceDisk dd : haystack ) {
			String linkage = dd.imageParentUUID();
			if( linkage.equals( create ) ) {
				needle.setChild( dd );
				// and recursively for the identified child...
				locateChild( dd, haystack );
				break;
			}
		}
	}

	private final File dir;
	private final List<VDIDisk> disks;
	private final Logger log;

	// A VirtualBox vm dir always seems to have a .vbox file...
	static public final FilenameFilter VBOXFILE =
		new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( ".vbox" );
			}
		};

	// TODO: VirtualBox can also manage .vmdk files...
	/*
	static public final FilenameFilter VMDKFILE =
		new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( ".vbox" );
			}
		};
	*/
}


// eof