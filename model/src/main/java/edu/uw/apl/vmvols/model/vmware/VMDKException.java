package edu.uw.apl.vmvols.model.vmware;

import edu.uw.apl.vmvols.model.VirtualDiskException;

/**
 * @author Stuart Maclean

 * Base class of all things that could go wrong with parsing a .vmdk
 * file.  Note that this is separate from IOExceptions.  Here we just
 * care about understanding the data structures within the file.

 *  LOOK: should this be a checked exception?
*/

public class VMDKException extends VirtualDiskException {

	public VMDKException() {
		super();
	}
	
	public VMDKException( String msg ) {
		super( msg );
	}
}

// eof
