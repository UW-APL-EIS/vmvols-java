package edu.uw.apl.vmvols.model.vmware;

import java.io.DataInput;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
//import java.util.zip.DeflaterInputStream;
//import java.util.zip.InflaterInputStream;

import org.apache.commons.io.EndianUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVirtualDisk;

/**
 * Stream-optimized compressed sparse extent.  The normal format for
 * vmdk files in .ovf bundles.  As well as being 'sparse', each grain
 * for which there _IS_ data is compressed using RFC1951 (Deflate).
 */
public class StreamOptimizedSparseExtent {

	public StreamOptimizedSparseExtent( File f, SparseExtentHeader h ) {
		source = f;
		header = h;
		log = LogFactory.getLog( getClass() );
	}

	public long size() {
		return header.capacity * Constants.SECTORLENGTH;
	}
	
	public SparseExtentHeader locateSparseExtentFooter() throws IOException {
		long len = source.length();
		long footerOffset = len - 2 * Constants.SECTORLENGTH;
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( footerOffset );
		byte[] ba = new byte[SparseExtentHeader.SIZEOF];
		raf.readFully( ba );
		raf.close();
		SparseExtentHeader result = new SparseExtentHeader( ba );
		return result;
	}

	// local and test case access only...
	void readMetaData() throws IOException {
		readGrainData();
	}

	private void readGrainData() throws IOException {
		// only need to read the directory at most once, it is invariant...
		if( grainDirectory != null )
			return;

		// recall we use the footer for meta-data, NOT the header
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		// Contained data ends with footer and eos marker, each 1 sector long
		long footerOffset = raf.length() - (2 * Constants.SECTORLENGTH );

		// sanity check, locate the Footer marker, precedes the Footer
		if( true ) {
			raf.seek( footerOffset - MetadataMarker.SIZEOF );
			MetadataMarker mdm = MetadataMarker.readFrom( raf );
			log.debug( "Expected FOOTER: actual " + mdm );
			if( mdm.type != MetadataMarker.TYPE_FOOTER )
				throw new IllegalStateException
					( "Expected footer marker, found " + mdm.type );
		}

		raf.seek( footerOffset );
		byte[] ba = new byte[Constants.SECTORLENGTH];
		raf.readFully( ba );
		SparseExtentHeader footer = new SparseExtentHeader( ba );
		log.info( "Footer.gdOffset: " + footer.gdOffset );

		// sanity check, locate the GD marker, precedes the GD
		if( true ) {
			raf.seek( footer.gdOffset * Constants.SECTORLENGTH -
					  MetadataMarker.SIZEOF );
			MetadataMarker mdm = MetadataMarker.readFrom( raf );
			log.debug( "Expected GD: actual " + mdm );
			if( mdm.type != MetadataMarker.TYPE_GD )
				throw new IllegalStateException
					( "Expected GD marker, found " + mdm.type );
		}

		long grainCount = footer.capacity / footer.grainSize;
		int grainTableCount = (int)(grainCount / footer.numGTEsPerGT );
		log.info( "GrainCount: "+ grainCount );
		log.info( "GrainTableCount: "+ grainTableCount );

		byte[] gdBuf = new byte[4*grainTableCount];
		raf.seek( footer.gdOffset * Constants.SECTORLENGTH );
		raf.readFully( gdBuf );
		long[] gdes = new long[grainTableCount];
		for( int i = 0; i < gdes.length; i++ ) {
			long gde = EndianUtils.readSwappedUnsignedInteger( gdBuf, 4*i );
			//			log.debug( i + " " + gde );
			gdes[i] = gde;
		}

		byte[] gtBuf = new byte[(int)(4*footer.numGTEsPerGT)];
		grainDirectory = new long[gdes.length][];
		for( int i = 0; i < gdes.length; i++ ) {
			long gde = gdes[i];
			//log.debug( i + " " + gde );

			if( gde == 0 ) {
				//				log.info( "GDE Zero: " + i );
				grainDirectory[i] = SparseExtent.PARENTGDE;
				continue;
			}

			if( gde == 1 ) {
				log.info( "GDE One: " + i );
				if( true )
					throw new IllegalStateException( "GDE 1 " + source );
				grainDirectory[i] = SparseExtent.ZEROGDE;
					continue;
			}

			// sanity check, locate the GT marker, precedes the GT
			if( true ) {
				raf.seek( gde * Constants.SECTORLENGTH -
						  MetadataMarker.SIZEOF );
				MetadataMarker mdm = MetadataMarker.readFrom( raf );
				//log.debug( i + ", expected GT: actual " + mdm ); 
				if( mdm.type != MetadataMarker.TYPE_GT )
					throw new IllegalStateException
						( "Expected GT marker, found " + mdm.type );
			}

			raf.seek( gde * Constants.SECTORLENGTH );
			raf.readFully( gtBuf );
			
			long[] grainTable = new long[(int)footer.numGTEsPerGT];
			for( int gt = 0; gt < grainTable.length; gt++ ) {
				long gte = EndianUtils.readSwappedUnsignedInteger( gtBuf,
																   4*gt );
				grainTable[gt] = gte;
			}
			grainDirectory[i] = grainTable;
		}
		raf.close();
	}

	// for test case access only
	long[][] getGrainDirectory() {
		return grainDirectory;
	}
	
	private void buildZeroGrains() {
		if( zeroGrain != null ) {
			return;
		}

		// LOOK: use footer values, though header and footer same ??
		grainSizeBytes = header.grainSize * Constants.SECTORLENGTH;
		grainTableCoverageBytes = grainSizeBytes * header.numGTEsPerGT;
	    if( header.grainSize == SparseExtent.GRAINSIZE_DEFAULT ) {
			zeroGrain =	SparseExtent.ZEROGRAIN_DEFAULT;
			zeroGrainTable = SparseExtent.ZEROGRAINTABLE_DEFAULT;
		} else {
			zeroGrain = new byte[(int)grainSizeBytes];
			zeroGrainTable = new byte[(int)grainTableCoverageBytes];
		}
	}

	private int uncompressGrain( byte[] ba, int offset, int len, byte[] output )
		throws IOException, DataFormatException {
		int result = 0;
		switch( header.compressAlgorithm ) {
		case 1:
			Inflater inf = new Inflater();
			inf.setInput( ba, offset, len );
			result = inf.inflate( output );
			inf.end();
			break;
		default:
			throw new IllegalStateException( "Unknown compression: " +
											 header.compressAlgorithm );
		}
		return result;
	}

	public InputStream getInputStream() throws IOException {
		readMetaData();
		buildZeroGrains();
		//		checkParent();
		InputStream pis = null;//parent.getInputStream();
		return new StreamOptimizedRandomAccess();
	}

	public RandomAccessVirtualDisk getRandomAccess() throws IOException {
		readMetaData();
		buildZeroGrains();
		//	readMetaData();
		//		checkParent();
		//		RandomAccessVirtualDisk pra = parent.getRandomAccess();
		return new StreamOptimizedRandomAccess();
	}

	class StreamOptimizedRandomAccess extends RandomAccessVirtualDisk {
		StreamOptimizedRandomAccess() throws IOException {
			super( size() );
			this.parentRA = null;
			raf = new RandomAccessFile( source, "r" );
			dPos();
			compressedGrainBuffer = new byte[(int)(2*grainSizeBytes)];
			grainBuffer = new byte[(int)grainSizeBytes];
			gtePrev = 0;
		}

		@Override
		public void close() throws IOException {
			//			parentRA.close();
			raf.close();
		}
		   
		@Override
		public void seek( long s ) throws IOException {
			/*
			  According to java.io.RandomAccessFile, no restriction on
			  seek.  That is, seek posn can be -ve or past eof
			*/
			//			parentRA.seek( s );
			posn = s;
			dPos();
		}

		@Override
		public long skip( long n ) throws IOException {
			//	parentRA.skip( n );
			long result = super.skip( n );
			dPos();
			return result;
	    }

		@Override
		public int readImpl( byte[] ba, int off, int len ) throws IOException {
			if( log.isDebugEnabled() )
				log.debug( "Posn " + posn + " len  " + len );

			// do min in long space, since size - posn may overflow int...
			long actualL = Math.min( size - posn, len );

			// Cannot blindly coerce a long to int, result could be -ve
			int actual = actualL > Integer.MAX_VALUE ? Integer.MAX_VALUE :
				(int)actualL;

			//logger.debug( "Actual " + actualL + " " + actual );
			int total = 0;
			while( total < actual ) {
				int left = actual - total;
				long[] gt = grainDirectory[gdIndex];
				if( false ) {
				} else if( gt == SparseExtent.PARENTGDE ) {
					if( parentRA != null ) {
						// look, read from parent...
					}
					log.debug( "Zero GD : " + gdIndex );
					int grainTableOffset = (int)
						(gtIndex * grainSizeBytes + gOffset);
					int inGrainTable = (int)
						(grainTableCoverageBytes - grainTableOffset);
					int fromGrainTable = Math.min( left, inGrainTable );
					System.arraycopy( zeroGrainTable, grainTableOffset,
									  ba, off+total, fromGrainTable );
					if( log.isDebugEnabled() )
						log.debug( len + " " + actual + " " +
								   left + " " + inGrainTable + " " +
								   fromGrainTable );
					total += fromGrainTable;
					posn += fromGrainTable;
				} else if( gt == SparseExtent.ZEROGDE ) {
					throw new IllegalStateException( "ZEROGDE!" );
				} else {
					int inGrain = (int)(grainSizeBytes - gOffset);
					int fromGrain = Math.min( left, inGrain );
					if( log.isDebugEnabled() )
						log.debug( len + " " + actual + " " + left + " " +
								   inGrain + " " + fromGrain );
					long gte = gt[gtIndex];
					if( false ) {
					} else if( gte == 0 ) {
						if( log.isDebugEnabled() )
							log.debug( "Zero GT : "+ gdIndex + " " + gtIndex );
						if( parentRA != null ) {
							// look: read parent if exists,
						}
						System.arraycopy( zeroGrain, 0,
										  ba, off+total, fromGrain );
						total += fromGrain;
						posn += fromGrain;
						if( parentRA != null )
							parentRA.skip( fromGrain );
					} else if( gte == 1 ) {
						System.arraycopy( zeroGrain, 0,
										  ba, off+total, fromGrain );
						total += fromGrain;
						posn += fromGrain;
						if( parentRA != null )
							parentRA.skip( fromGrain );
					} else {
						if( gte != gtePrev ) {
							raf.seek( gte * Constants.SECTORLENGTH );
							GrainMarker gm = GrainMarker.readFrom( raf );
							int nin = raf.read( compressedGrainBuffer,
												0, gm.size );
							if( nin != gm.size )
								throw new IllegalStateException
									( "Partial read: "+ nin + " " + gm.size);
							if( log.isDebugEnabled() )
								log.debug( "Inflating " + gdIndex +
										   " "+ gtIndex +
										   " = " + nin + " " + gm.lba );
							try {
								int actualLength = uncompressGrain
									( compressedGrainBuffer, 0, nin,
									  grainBuffer );
								if( actualLength != grainSizeBytes ) {
									throw new IllegalStateException
										( "Bad inflate len: " + actualLength );
								}
							} catch( DataFormatException dfe ) {
									// what now??
								log.warn( dfe );
							}
							gtePrev = gte;
						}
						System.arraycopy( grainBuffer, (int)gOffset,
										  ba, off+total, fromGrain );
						total += fromGrain;
						posn += fromGrain;
						if( parentRA != null )
							parentRA.skip( fromGrain );
					}
				}
				if( log.isDebugEnabled() )
					log.debug( total + " " + posn );
				dPos();
			}
			return total;
		}

		@Override
		public void writeImpl( byte[] ba, int off, int len )
			throws IOException {

			throw new UnsupportedOperationException
				( "StreamOptimized disks not writable" );
		}

		/**
		 *   Called whenever the local posn changes value.
		 */
		private void dPos() {

			/*
			  This is the crux of the sparse reading. We map logically
			  map the 'file pointer' on the input stream to a block
			  map and block offset.  To do the next read, we then calc
			  a physical seek offset into the atual file and read.

			  According to java.io.RandomAccessFile, a file posn
			  is permitted to be past its size limit.  We cannot
			  map such a posn to the block map info of course...
			*/
			if( posn >= size )
				return;

			gdIndex = (int)(posn / grainTableCoverageBytes);
			// this next operation MUST be done in long space, NOT int...
			long inTable = posn - ((long)gdIndex * grainTableCoverageBytes);
			gtIndex = (int)(inTable / grainSizeBytes);
			gOffset = (int)(inTable % grainSizeBytes);
			/*
			  long imageOffset = posn;
			bIndex = (int)(imageOffset / header.blockSize());
			bOffset = (int)(imageOffset % header.blockSize());
			
			if( log.isDebugEnabled() )
				log.debug( getGeneration() + " posn: "+ posn +
						   " bIndex: " + bIndex +
						   " bOffset: " + bOffset );
			*/
		}

		private final RandomAccessFile raf;
		private final RandomAccessVirtualDisk parentRA;
		private int gdIndex, gtIndex;
		private long gOffset;
		private long gtePrev;
		private byte[] compressedGrainBuffer;
		private byte[] grainBuffer;

	}
	
	/**
	   struct GrainMarker {
	     long lba;
		 int size;
	   }
	*/
	static class GrainMarker {
		GrainMarker( long lba, int size ) {
			this.lba = lba;
			this.size = size;
		}
		static GrainMarker readFrom( DataInput di ) throws IOException {
			long l = di.readLong();
			long lba = EndianUtils.swapLong( l );
			int i = di.readInt();
			int size = EndianUtils.swapInteger( i );
			return new GrainMarker( lba, size );
		}
		
		final long lba;
		final int size;

		static final int SIZEOF = 8 + 4;
	}

	/**
	   struct MetadataMarker {
	     long numSectors;
		 int size = 0;
		 int type;
	   }
	*/

	static class MetadataMarker {
		MetadataMarker( long numSectors, int type ) {
			this.numSectors = numSectors;
			this.type = type;
		}

		static MetadataMarker readFrom( DataInput di ) throws IOException {
			long l = di.readLong();
			long numSectors = EndianUtils.swapLong( l );
			int i = di.readInt();
			int size = EndianUtils.swapInteger( i );
			// LOOK: check size is zero
			i = di.readInt();
			int type = EndianUtils.swapInteger( i );
			return new MetadataMarker( numSectors, type );
		}

		@Override
		public String toString() {
			return "" + numSectors + "," + type;
		}
		
			   
		final long numSectors;
		final int type;
		static final int SIZE = 0;

		// Three fields on disk (the 'int size' field is implicit and set to 0)
		static final int FIELDSSIZEOF = 8 + 4 + 4;

		/*
		  When written out to the managed data file, all markers are
		  padded to a sector boundary.  That way, the metadata they
		  describe (grain table, grain directory, etc) starts on its
		  own sector boundary
		*/
		static final int SIZEOF = Constants.SECTORLENGTH;

		static final byte[] PADDING = new byte[SIZEOF-FIELDSSIZEOF];
		
		static final int TYPE_EOS = 0;
		static final int TYPE_GT = 1;
		static final int TYPE_GD = 2;
		static final int TYPE_FOOTER = 3;
	}


	File source;
	SparseExtentHeader header;
	Log log;
	
	long grainSizeBytes, grainTableCoverageBytes;
	private byte[] zeroGrain;
	private byte[] zeroGrainTable;
	private long[][] grainDirectory;

	/*
	  List<GrainMarker> grainMarkers;
	List<MetadataMarker> metadataMarkers;

	long grainTableCount;
	GrainTable[] grainTables;
	GrainDirectory grainDirectory;
	*/
}

// eof
