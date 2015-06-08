package edu.uw.apl.vmvols.model;

/**
 *
 * @author Stuart Maclean
 
   Base class of all things that could go wrong with parsing any form
   of virtual disk file, i.e. a VirtualBox .vdi or VMWare .vmdk file.

   Note that this is separate from IOExceptions.  Here we just care
   about understanding the data structures with the file.

   LOOK: should this be a checked exception?
*/

public class VirtualDiskException extends RuntimeException {

	public VirtualDiskException() {
		super();
	}
	
	public VirtualDiskException( String msg ) {
		super( msg );
	}
}

// eof
