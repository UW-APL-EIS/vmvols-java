Virtual Machine Volumes
=======================

Make the contents of virtual machine hard drives available to
host-based software.


Platforms
---------

The vmvols codebase is pure Java.  We have developed Java class
libraries to read (and write!) VirtualBox .vdi files and VMWare .vmdk
files (for host-based VMWare products only, not the esxi/vmfs-derived
ones).

That said, the most useful aspect of vmvols is its use of FUSE to
expose VM drive contents under mount points on the host.  This
limits the usefulness of the 'fuse' sub-artifact to platforms
supporting FUSE, which I think means Linux and Mac OSX (?)  Note that
we use FUSE4J to bridge the Java world of vmvols to the C world of FUSE.
More info on FUSE4J at https://github.

Required Tools
--------------

This codebase is Java, and so needs a Java compiler system, aka a
'Java Developmment Kit (JDK)'.  A 1.7 or later JDK is required.
Sun/Oracle's JDK works well, as does OpenJDK's JDK.

The build is via Maven, a build and project management tool for Java
artifacts. So Maven is required too.  All code dependencies are
resolved by Maven. At time of writing (Mar 2016), the author uses
Maven 3.2.5 on both Ubuntu, Mint and Debian variants of Linux. See
http://maven.apache.org/download.cgi for more details.

To verify you are running suitable versions of Java and Maven, run
'mvn -v' and inspect the output, like this:

```
[stuart-vaio]$ mvn -v
Apache Maven 3.2.5 (12a6b3acb947671f09b81f49094c53f426d8cea1; 2014-12-14T09:29:23-08:00)
Maven home: /usr/local/apache-maven/apache-maven-3.2.5
Java version: 1.7.0_17, vendor: Oracle Corporation
Java home: /usr/lib/jvm/jdk1.7.0_17/jre
Default locale: en_US, platform encoding: UTF-8
OS name: "linux", version: "2.6.32-73-generic", arch: "i386", family: "unix"
```

Build/Install
------------

```
$ cd /path/to/vmvols

$ mvn install

$ mvn javadoc:aggregate
```

The Javadoc APIs should then be available at ./target/site/apidocs.

The code is organized into Maven 'modules', with a parent pom at
the root. There are unit tests for some modules.  These are only run
when the 'tester' profile is activated.  If you want to run unit
tests, try:

```
$ mvn test -Ptester
```

Modules
-------

The vmvols codebase is organised as four Maven 'modules', with a
parent pom at the root level.  The modules are as follows

* model

* fuse

* cli

* samples

# Model

The primary vmvols module.  Contains pure Java parsers for .vdi and
.vmdk file formats, as so enables access to virtual disk content with
the VM powered off.  Indeed no VirtualBox/VMware software need be
present at all.  The primary classes in this module are probably

* [VirtualDisk]
  (./model/src/main/java/edu/uw/apl/vmvols/model/VirtualDisk.java)
  
* [VDIDisk] (./model/src/main/java/edu/uw/apl/vmvols/model/virtualbox/VDIDisk.java)

* [VMDKDisk] (./model/src/main/java/edu/uw/apl/vmvols/model/vmware/VMDKDisk.java)

Access to virtualdisk content is then via these two methods, defined
in VirtualDisk:

* getInputStream

* getRandomAccess

# Fuse

This module contains classes bridging the model classes (above) to the
FUSE4J api (and hence to FUSE).  Also included is arguably the most
useful tool in all of vmvols --- the vmmount shell script driver for
the class which enables host-level access to virtual machine disk
content. See [vmmount] (./fuse/vmmount) and its [Java sources]
(./fuse/src/main/java/edu/uw/apl/vmvols/fuse/) for the gory details.

To use vmmount, just locate one of your local VMs:

```
$ cd /path/to/vmvols/fuse

$ mvn package

$ mkdir mount

$ ./vmmount /path/to/my/vbox/windowsVM/ mount

$ ls mount

// mmls is a Sleuthkit tool for volume system inspection
$ mmls mount/windowsVM/sda
```
# CLI

To finish...

# Samples

To finish

```
Motivation/FAQ
--------------

Why use vmvols?

* You wish to perform host forensics on virtual machines which are
  powered off.  Sleuthkit is what we use (and see [TSK4J]
  (https://github.com/uw-dims/tsk4j) too) for such a task.

* It is more fully-featured than existing VM mount utilities. Or
  perhaps we should say "contains features existing tools do
  not". Under a single mount point, you can inspect many Snapshots,
  over many disks, of many VMs.

* You don't have a local VirtualBox, VMWare installation. vmvols is
  pure Java, it doesn't rely on any VM manager libraries/tools.

* You prefer Java over C.  The file-format parsers for .vdi, .vmdk
files are in Java.

Why use vmvols when I could just use...

* VMware Virtual Disk Manager?

* libguestfs?  I cannot find any option to expose many snapshots at
  once using the guestmount tool.

* vdfuse?

Why does vmvols exist?

* I fancied the challenge of reverse engineering file formats for
Virtualbox, and to a lesser extent VMware, managed VM hard disk
contents.

* I thought it might be useful in a malware analysis workflow using
say Cuckoo Sandbox.  We take Snapshots before and after a run, mount
both Snapshots, and run Sleuthkit tools over the two filesystems to
infer changes.

Local Repository
----------------

The Maven artifacts built here themselves depend on the following
existing Maven artifacts which are not (yet) available on a public
Maven repository (like Maven Central):

* fuse4j:fuse4j-core:jar:3.0.0

* edu.uw.apl.commons:native-lib-loader:jar:2.1.0

The source for both of these is available on GitHub.  The native
loader code is [here]
(https://github.com/uw-dims/java-native-loader), while the Java-to-C
fuse bridge that is Fuse4j is [there]
(https://github.com/uw-dims/fuse4j).

To save the vmvols user the chore of building and installing these
dependencies, we are bundling these compiled artifacts into a 'project-local
Maven repo' at [./.repository](./.repository).  The relevant [pom] (./fuse/pom.xml)
refers to this repo to resolve the artifact dependencies.  The
project-local repo looks like this:

```
$ cd /path/to/vmvols
$ tree .repository/
.repository/
`-- edu
    `-- uw
        `-- apl
            `-- commons
                `-- native-lib-loader
                    `-- 2.1.0
                        |-- native-lib-loader-2.1.0.jar
                        `-- native-lib-loader-2.1.0.pom
`-- fuse4j
    `-- fuse4j-core
		`-- 3.0.0
		|-- fuse4j-core-3.0.0.jar
		|-- fuse4j-core-3.0.0.pom
```

# To Do

To finish

# Video/Slides

Ideas related to this work were presented at the [OSDFCon]
(http://www.osdfcon.org/2013-event/) workshop in 2013.  A local copy
of the slides is also included [here](https://github.com/uw-dims/tsk4j/blob/master/doc/Maclean-OSDF2013-tsk4j.pdf).

# Contact

stuart at apl dot uw dot edu

