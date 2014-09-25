package edu.uw.apl.vmvols.model;

import java.util.List;

abstract public class VirtualMachine {

	abstract public String getName();

	abstract public List<? extends VirtualDisk> getBaseDisks();
	abstract public List<? extends VirtualDisk> getActiveDisks();
}

// eof
