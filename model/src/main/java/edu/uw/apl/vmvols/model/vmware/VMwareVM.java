package edu.uw.apl.vmvols.model.vmware;

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

/**
 * @author Stuart Maclean
 *
 * To locate the virtual disk(s) in a VMWare vm directory, do this:
 *
 * File theDir = ....
 * boolean b = VMwareVM.isVMware( theDir );
 * if( b ) {
 *   VMwareVM vm = new VMwareVM( theDir );
 *   List<VirtualDisk> disks = vm.getActiveDisks();
 * }
 *
 * An 'active' disk is one which would be written to were the VM to be
 * running.  It is the 'current snapshot'.  This API also supports
 * accessing any 'generation' (Snapshot) of any disk, all the way up
 * the 'base disk' (which has generation 1), which is held in the file
 * VirtualBox created when the VM was first built.  If a VM has never
 * had a Snapshot taken, its active disk(s) and base disk(s) are the
 * same thing.
 *
 * @see VBoxVM
 */
public class VMwareVM extends VirtualMachine {

	static public boolean isVMware( File dir ) {
		if( !dir.isDirectory() )
			return false;

		// TODO: fill this in..
		return false;
	}

	public VMwareVM( File vmDir ) throws IOException {
		baseDisks = new ArrayList<VirtualDisk>();
		log = LogFactory.getLog( getClass() );
		dir = vmDir;
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

	private final File dir;
	private final List<VirtualDisk> baseDisks;
	private final Log log;

}


// eof