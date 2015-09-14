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
 */
public class VBoxVM extends VirtualMachine {

	static public boolean isVBox( File dir ) {
		if( !dir.isDirectory() )
			return false;
		File[] fs = dir.listFiles( VBOXFILE );
		// If we find a .vbox file we assert yes.
		if( fs.length > 0 )
			return true;
		return false;
	}

	/**
	 * Expected that file f (and its parent, if f not a directory)
	 * have been passed to isVBoxVM prior to this call
	 */
	public VBoxVM( File f ) throws IOException {
		baseDisks = new ArrayList<VirtualDisk>();
		log = LogFactory.getLog( getClass() );
		List<VirtualDisk> children = new ArrayList<VirtualDisk>();
		if( f.isDirectory() ) {
			dir = f;

			// A VBox VM can manage .vdi disks...
			Collection<File> fs = FileUtils.listFiles
				( dir, new String[] { VDIDisk.FILESUFFIX }, true );
			for( File el : fs ) {
				VDIDisk vdi = null;
				try {
					vdi = VDIDisk.readFrom( el );
				} catch( VDIException e ) {
					continue;
				}
				if( vdi == null )
					continue;
				if( vdi.getUUIDParent().equals( VirtualDisk.NULLUUID ) )
					baseDisks.add( vdi );
				else
					children.add( vdi );
			}
			
			// A VBox VM can also manage VMware .vmdk disks...
			fs = FileUtils.listFiles
				( dir, new String[] { VMDKDisk.FILESUFFIX }, true );
			for( File el : fs ) {
				VMDKDisk vmdk = null;
				try {
					vmdk = VMDKDisk.readFrom( el );
				} catch( VMDKException e ) {
					continue;
				}
				if( vmdk == null )
					continue;
				if( vmdk.getUUIDParent().equals( VirtualDisk.NULLUUID ) )
					baseDisks.add( vmdk );
				else
					children.add( vmdk );
			}
			
		} else {
			/*
			  When the supplied file is a single vd file and not
			  a vbox vm dir, we add JUST the supplied file to the basedisks.
			  We do NOT try to locate any other disks that are also owned
			  by the same VM.
			  
			  We still derive the children the single disk may have,
			  since it must be linked so we can access its active disk.
			*/
			
			dir = f.getParentFile();

			// A VBox VM can manage .vdi disks...
			Collection<File> fs = FileUtils.listFiles
				( dir, new String[] { VDIDisk.FILESUFFIX }, true );
			for( File el : fs ) {
				VDIDisk vdi = null;
				try {
					vdi = VDIDisk.readFrom( el );
				} catch( VDIException e ) {
					continue;
				}
				if( vdi == null )
					continue;
				if( el.equals( f ) ) {
					baseDisks.add( vdi );
				} else {
					if( !vdi.getUUIDParent().equals( VirtualDisk.NULLUUID ) ) {
						children.add( vdi );
					}
				}
			}

			
			// A VBox VM can also manage VMware .vmdk disks...
			fs = FileUtils.listFiles
				( dir, new String[] { VMDKDisk.FILESUFFIX }, true );
			for( File el : fs ) {
				VMDKDisk vmdk = null;
				try {
					vmdk = VMDKDisk.readFrom( el );
				} catch( VMDKException e ) {
					continue;
				}
				if( vmdk == null )
					continue;
				if( el.equals( f ) ) {
					baseDisks.add( vmdk );
				} else {
					if( !vmdk.getUUIDParent().equals( VirtualDisk.NULLUUID ) )
						children.add( vmdk );
				}
			}
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

	static private void link( VirtualDisk needle,
							  List<VirtualDisk> haystack ) {
		UUID linkage = needle.getUUID();
		for( VirtualDisk vd : haystack ) {
			if( linkage.equals( vd.getUUIDParent() ) ) {
				needle.setChild( vd );
				// and recursively for the identified child...
				link( vd, haystack );
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