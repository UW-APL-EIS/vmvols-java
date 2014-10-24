package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;

import org.apache.log4j.Logger;


abstract public class VirtualDisk {
	protected VirtualDisk( File source ) {
		this.source = source;
		logger = Logger.getLogger( getClass() );
	}

	public File getPath() {
		return source;
	}

	public long sectorCount() {
		return size() / Constants.SECTORLENGTH;
	}

	/**
	 * Mimic what a physical disk would return for a low-level ATA
	 * inquiry of its 'serial number'
	 */
	abstract public String getID();
	
	abstract public long size();

	abstract public InputStream getInputStream() throws IOException;

	abstract public RandomAccessVolume getRandomAccessVolume()
		throws IOException;

	abstract public int getGeneration();

	abstract public VirtualDisk getActive();

	abstract public VirtualDisk getGeneration( int i );

	abstract public List<? extends VirtualDisk> getAncestors();

	protected final File source;
	protected final Logger logger;
}

// eof
