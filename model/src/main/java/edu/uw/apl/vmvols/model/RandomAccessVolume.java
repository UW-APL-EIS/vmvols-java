package edu.uw.apl.vmvols.model;

import java.io.IOException;

/**
  Needed by our VirtualDisk implementations to provide random access
  to the embedded image.  Just pick a subset of functions from
  java.io.RandomAccessFile without providing the entire feature set of
  that class --- we have no need for primitive type manipulation
  (e.g. DataInput,DataOutput signatures)

  RandomAccessVolume(Volume) analogous to RandomAccessFile(File).
  It's the mechanism for getting bytes in and out of the embedded
  Volume.
*/

public interface RandomAccessVolume {

	public void close() throws IOException;
	public long length() throws IOException;
	public void seek( long pos ) throws IOException;

	public int read() throws IOException;
	public int read( byte[] b ) throws IOException;
	public int read( byte[] b, int off, int len ) throws IOException;

	public void write( int b ) throws IOException;
	public void write( byte[] b ) throws IOException;
	public void write( byte[] b, int off, int len ) throws IOException;
}

// eof