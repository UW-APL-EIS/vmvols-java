package edu.uw.apl.vmvols.model;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


abstract public class VirtualDisk {
	protected VirtualDisk( File source ) {
		this.source = source;
		log = LogFactory.getLog( getClass() );
	}

	public File getPath() {
		return source;
	}

	public long sectorCount() {
		return size() / Constants.SECTORLENGTH;
	}

	public void setChild( VirtualDisk vd ) {
		child = vd;
		vd.setParent( this );
	}
	
	void setParent( VirtualDisk p ) {
		UUID linkage = p.getUUID();
		if( !linkage.equals( getUUIDParent() ) ) {
			throw new IllegalArgumentException
				( "Linkage mismatch setting parent " + source + "," +
				  p.getPath() );
		}
		parent = p;
	}

	
	/**
	 * Mimic what a physical disk would return for a low-level ATA
	 * inquiry of its 'serial number'
	 */
	abstract public String getID();
	
	abstract public long size();

	abstract public UUID getUUID();

	abstract public UUID getUUIDParent();
	
	abstract public InputStream getInputStream() throws IOException;

	abstract public RandomAccessVirtualDisk getRandomAccess()
		throws IOException;

	public int getGeneration() {
		if( parent == null )
			return 0;
		return 1 + parent.getGeneration();
	}

	public VirtualDisk getBase() {
		if( parent == null )
			return this;
		return parent.getBase();
	}

	public VirtualDisk getActive() {
		if( child == null )
			return this;
		return child.getActive();
	}

	public VirtualDisk getGeneration( int i ) {
		int g = getGeneration();
		if( g == i )
			return this;
		if( i < g ) {
			if( parent == null )
				throw new IllegalArgumentException
					( source + ": No generation " + i );
			return parent.getGeneration( i );
		}
		if( child == null )
				throw new IllegalArgumentException
					( source + ": No generation " + i );
		return child.getGeneration( i );
	}
	
	public List<VirtualDisk> getAncestors() {
		List<VirtualDisk> result = new ArrayList<VirtualDisk>();
		getAncestors( result );
		return result;
	}

	private void getAncestors( List<VirtualDisk> accumulator ) {
		if( parent == null )
			return;
		accumulator.add( parent );
		parent.getAncestors( accumulator );
	}

	protected final File source;
	protected VirtualDisk parent, child;
	protected final Log log;

	static public final UUID NULLUUID = new UUID( 0L, 0L );

}

// eof
