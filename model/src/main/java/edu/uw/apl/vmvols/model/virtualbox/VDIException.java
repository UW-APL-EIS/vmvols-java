package edu.uw.apl.vmvols.model.virtualbox;

import edu.uw.apl.vmvols.model.VirtualDiskException;

/**
   Base class of all things that could go wrong with parsing a .vdi
   file.  Note that this is separate from IOExceptions.  Here we just
   care about understanding the data structures with the file.

   LOOK: should this be a checked exception?
*/

public class VDIException extends VirtualDiskException {

	public VDIException() {
		super();
	}
	
	public VDIException( String msg ) {
		super( msg );
	}
}

// eof
