package edu.uw.apl.vmvols.model.virtualbox;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Collections;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.io.FileUtils;

import edu.uw.apl.vmvols.model.VirtualDisk;
import edu.uw.apl.vmvols.model.VirtualMachine;
import edu.uw.apl.vmvols.model.vmware.VMDKDisk;
import edu.uw.apl.vmvols.model.vmware.VMDKException;

/**
 * @author Stuart Maclean
 *
 * To locate the virtual disk(s) in a VirtualBox vm directory, do this:
 *
 <code>
 File theDir = ....
 boolean b = VBoxVM.isVBox( theDir );
 if( b ) {
  VBoxVM vm = new VBoxVM( theDir );
  List<VirtualDisk> disks = vm.getActiveDisks();
 }
 </code>
 
 * Alternatively, and better, is the VM-agnostic api:
 *
 <code>
 VirtualMachine vm = VirtualMachine.create( theDir );
 List<VirtualDisk> disks = vm.getActiveDisks();
 </code>
 *
 * An 'active' disk is one which would be written to were the VM to be
 * running.  It is the 'current snapshot'.  This API also supports
 * accessing any 'generation' (Snapshot) of any disk, all the way up
 * the 'base disk' (which has generation 1), which is held in the file
 * VirtualBox created when the VM was first built.  If a VM has never
 * had a Snapshot taken, its active disk(s) and base disk(s) are the
 * same thing.
 *
 * Currently we support just .vdi (native to VirtualBox) and .vmdk
 * (imported from VMWare) virtual disk drive formats.  VirtualBox
 * itself supports others, but I have never used/created one so have
 * no experience with those formats.
 *
 * @see VMwareVM
 */
public class VBoxVM extends VirtualMachine {

	static public boolean isVBoxVM( File dir ) {
		if( !dir.isDirectory() )
			return false;
		File[] fs = dir.listFiles( VBOXFILE );
		// If we find a .vbox file we assert yes.
		if( fs.length > 0 )
			return true;
		return false;
	}

	/**
	 * Expected that file f been passed to isVBoxVM prior to this call
	 */
	public VBoxVM( File f ) throws IOException {
		if( !isVBoxVM( f ) )
			throw new IllegalArgumentException
				( "Not a VirtualBox VM directory: " + f );
		dir = f;
		baseDisks = new ArrayList<VirtualDisk>();
		log = LogFactory.getLog( getClass() );
		List<VirtualDisk> children = new ArrayList<VirtualDisk>();
		
		// A VBox VM can manage .vdi disks...
		Collection<File> fs = FileUtils.listFiles
			( dir, new String[] { VDIDisk.FILESUFFIX }, true );
		for( File el : fs ) {
			VDIDisk vdi = null;
			try {
				vdi = VDIDisk.readFrom( el );
			} catch( VDIException e ) {
				log.warn( el + ": " + e );
				continue;
			}
			if( vdi == null )
				continue;
			if( vdi.getUUIDParent().equals( VirtualDisk.NULLUUID ) )
				baseDisks.add( vdi );
			else
				children.add( vdi );
		}
			
		/*
		  A VBox VM can also manage VMware .vmdk disks. If so,
		  it buries its own uuidImage/uuidParent info in the
		  Descriptor's 'ddb.*' entries.
		*/
		fs = FileUtils.listFiles
			( dir, new String[] { VMDKDisk.FILESUFFIX }, true );
		for( File el : fs ) {
			VMDKDisk vmdk = null;
			try {
				vmdk = VMDKDisk.readFrom( el );
			} catch( VMDKException e ) {
				log.warn( el + ": " + e );
				continue;
			}
			if( vmdk == null )
				// LOOK: log e ??
				continue;
			if( vmdk.getUUIDParent().equals( VirtualDisk.NULLUUID ) )
				baseDisks.add( vmdk );
			else
				children.add( vmdk );
		}
		
		// Link base disks to child disks...
		for( VirtualDisk d : baseDisks )
			link( d, children );
	}

	@Override
	public String getName() {
		return dir.getName();
	}
	
	// the base disks are what we actually maintain...
	@Override
	public List<VirtualDisk> getBaseDisks() {
		return baseDisks;
	}

	// any active disks are derivable from their base disk counterpart...
	@Override
	public List<VirtualDisk> getActiveDisks() {
		List<VirtualDisk> result = new ArrayList<VirtualDisk>();
		for( VirtualDisk vd : baseDisks ) {
			result.add( vd.getActive() );
		}
		return result;
	}

	/**
	 * @param childDisks - All non-base disks found when identifying
	 * all .vdi and .vmdk files in a VirtualBox VM directory.  Not
	 * necessarily true that this baseDisk is the parent of any/all of
	 * the childDisks.  In fact, when called recursively (see
	 * recursive call), baseDisk need not be a true baseDisk
	 * (generation=1) at all.
	 *
	 * The way VirtualBox imports .vmdk files, it always adds
	 * uuidImage/uuidParent info to the Descriptor, so NO need to
	 * consult VMware's parentFileNameHint.
	 */
	private void link( VirtualDisk baseDisk,
					   List<VirtualDisk> childDisks ) {

		UUID linkage = baseDisk.getUUID();
		for( VirtualDisk vd : childDisks ) {
			/*
			  Due to recursive nature of the link process, the needle
			  and haystack member could be same object.
			*/
			if( vd == baseDisk )
				continue;
			if( linkage.equals( vd.getUUIDParent() ) ) {
				baseDisk.setChild( vd );
				vd.setParent( baseDisk );
				// and recursively for the identified child...
				link( vd, childDisks );
				break;
			}
		}
	}

	private final File dir;
	private final List<VirtualDisk> baseDisks;
	private final Log log;

	static public final String FILESUFFIX = ".vbox";

	// A VirtualBox vm dir always seems to have a .vbox file...
	static public final FilenameFilter VBOXFILE =
		new FilenameFilter() {
			public boolean accept( File dir, String name ) {
				return name.endsWith( FILESUFFIX );
			}
		};
}

// eof