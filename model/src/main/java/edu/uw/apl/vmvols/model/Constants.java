package edu.uw.apl.vmvols.model;

/**
 * @author Stuart Maclean
 *
 * Various simple math constants in the domain of disk size processing.
 * Apache commons-io likely has these too?
 */
public class Constants {

	static public final int SECTORLENGTH = 512;
	
	// 2^10 ~ 10^3
	static public final long KiB = 1024L;

	// 2^20 ~ 10^6
	static public final long MiB = KiB * KiB;

	// 2^30 ~ 10^9
	static public final long GiB = MiB * KiB;

	// 2^40 ~ 10^12
	static public final long TiB = GiB * KiB;

	// 2^50 ~ 10^15
	static public final long PiB = TiB * KiB;
	
}

// eof
