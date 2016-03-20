# PL/Java VM option recommendations

The PostgreSQL configuration variable `pljava.vmoptions` can be used to
supply custom options to the Java VM when it is started. Several of these
options are likely to be worth setting.

## Byte order for PL/Java-implemented user-defined types

PL/Java is free of byte-order issues except when using its features for building
user-defined types in Java. At sites with no current or planned use of
those features, this section does not require attention.

The 1.5.0 release of PL/Java begins a transition affecting the byte-order
defaults, which will be completed in a future release. No immediate action is
recommended; there is a [byte-order page](../use/byteorder.html) for more on
the topic and an advance notice of an expected future migration step.

## Class data sharing

Class data sharing is a commonly-supported Java virtual machine feature
that reduces both VM startup time and memory footprint by having many of
Java's runtime classes preprocessed into a `classes.jsa` file that can
be quickly memory-mapped into the process at Java startup, and shared
if there are multiple processes running Java VMs. It is enabled by including

    -Xshare:on

in the `pljava.vmoptions` string. In rough terms in 64-bit Java 8 it can
reduce the 'Metaspace' size per PL/Java backend by slightly over 4 MB, and
cut about 15 percent from the PL/Java startup time per process.

Sharing may be enabled automatically if the Java VM runs in `client` mode
(described below), but usually *must* be turned on with `-Xshare:on` if the
VM runs in `server` mode.

*Note: the earliest documentation on class data sharing, dating to Java 1.5,
suggested that the feature was not available at all in server mode. In recent
VMs that is no longer the case, but it does have to be turned on.*

To confirm that sharing is on, look at PL/Java's startup message for a line
similar to:

    Java HotSpot(TM) 64-Bit Server VM (25.74-b02, mixed mode, sharing)

To see the startup message after PL/Java is already installed, use

    SET client_min_messages TO debug1;

in a new session, before executing any PL/Java function;

    SELECT sqlj.get_classpath('public');

for example.

### `classes.jsa` not present

If the option `-Xshare:on` makes PL/Java fail to start, with a message like

    An error has occurred while processing the shared archive file.
    Specified shared archive not found.

then the `classes.jsa` file was not automatically created when Java was
installed. It can be created with the simple command `java -Xshare:dump`
(run with adequate privilege to write in Java's installed location).

### Preloading PL/Java's classes as well as the Java runtime's

The basic class data sharing feature includes only Java's own runtime
classes in the shared archive. When using Java 8 from Oracle, 8u40 or
later, an expanded feature called [application class data sharing][appcds]
is available, with which you can build a shared archive that preloads
PL/Java's classes as well as Java's own. In rough terms this doubles the
improvement in startup time seen with basic class data sharing alone,
for a total improvement (compared to no sharing) of 30 to 35 percent.
It also allows the memory per backend to be even further
scaled back, as discussed under "plausible settings" below.

#### Licensing considerations

Basic class data sharing is widely available with no restriction, but
*application class data sharing* is a "commercial feature" exclusive to
Oracle Java 8. It will not work unless `pljava.vmoptions` also contain
`-XX:+UnlockCommercialFeatures` , with implications described in the
"supplemental license terms" of the Oracle
[binary code license for Java SE][bcl]. The license seems
to impose no burden on use for internal development and testing, but requires
negotiating an additional agreement with Oracle if the feature will be used
"in your internal business operations or for any commercial or production
purpose." It is available to consider for any application where the
additional performance margin can be given a price.

Looking ahead to Java 9, there are some promising signs that OpenJDK may have
an equivalent feature.

Here are the [instructions for setting up application class data sharing][iads].

[appcds]: http://docs.oracle.com/javase/8/docs/technotes/tools/unix/java.html#app_class_data_sharing
[bcl]: http://www.oracle.com/technetwork/java/javase/terms/license/index.html
[iads]: appcds.html

## `-XX:+DisableAttachMechanism`

Management and monitoring tools like `jvisualvm` (included with the Oracle JDK)
can show basic information about any running Java process, but can show much
more detailed information by 'attaching' to the process.

By default, attachment requires the tool to be run on the same host as the
database server, and under the same identity. Those existing restrictions
should satisfy most security requirements (after all, a local process under
the postgres identity has many other ways to bypass PostgreSQL's assurances),
but there can also be non-security-related reasons to add the
`-XX:+DisableAttachMechanism` option:

Interaction with class data sharing
: Depending on the versions of Java and `jvisualvm`, it is possible for
    `jvisualvm` to hang after selecting a Java process to display, if that VM
    has class data sharing enabled. In such cases, adding
    `-XX:+DisableAttachMechanism` to the VM options will ensure that `jvisualvm`
    can at least display the usual overview information that is available when
    it cannot attach.

Memory footprint
: In Java 8, running the VM with `-XX:+DisableAttachMechanism` seems to
    reduce the necessary `MetaspaceSize` by about 3 megabytes.

## `client` and `server` VM

A Java VM may be able to operate in a `client` mode (optimized for starting
small apps with minimal latency), or a `server` mode, tailored to large,
long-running apps emphasizing throughput. Typically the `server` mode is
selected automatically if a 'server class' (large memory or 64-bit) platform
is detected.

PostgreSQL will often be run on 'server class' platforms, causing the VM to
select `server` mode, when the usage pattern in PL/Java more closely resembles
the `client` scenario.

While there is no one simple option to force `client` mode if the VM has
selected `server`, it is possible to adjust individual settings to more
appropriate values.

The heap size settings are important, because the defaults on a server-class
machine will be to grab a substantial fraction of all memory. In a PL/Java
application where many processes may run, it is more important to choose
smaller heap sizes close to what is actually needed. The size can be set
with `-Xms` to give a starting size, which the VM can expand if needed; this
is a safe option when you have an idea what the usual case memory demand will
be, but are not confident about the maximum that could sometimes be needed.
A hard limit can be set with `-Xmx` if you know a heap size that your code
should never need to exceed.

The [choice of garbage collector][gcchoice] can also affect the memory
footprint, as the different collectors incur different memory overheads.
If it performs adequately for the application, the `Serial` collector
seems to lead the choices in space efficiency, followed by `ConcMarkSweep`.
The `G1` collector, favored as `ConcMarkSweep`'s replacement, uses slightly
more space to work, while `Parallel` and `ParallelOld` will occupy more than
double the space of any of these.

### Plausible settings

The optimal memory settings and garbage collector for a specific PL/Java
application are best determined by testing, but it can be helpful to have
an idea what is realistic. Java's default heap and metaspace sizes, especially
on a server-class machine, tend to be far larger than necessary.

As a point of reference, PL/Java can load, and execute the tests in, its
shipped `pljava-examples` jar with the following settings:

    -Xshare:on -XX:+DisableAttachMechanism -Xms2m -XX:+UseSerialGC

and end up with a heap grown to slightly under 4 MB, and a 5 MB metaspace
with less than 2 MB used. (The only way to get a metaspace smaller than 5 MB
is to use the `-XX:MaxMetaspaceSize` option, which is a hard limit, with the
risk of out-of-memory errors if set too low. The test described here can be
completed with the metaspace limited to 3 MB.)

If the Oracle *application class data sharing* feature is enabled, the
metaspace limit can be set at 2 MB, and conclude the test without growing
past 1 MB.

For an application where startup time is most critical (for example, only
trivial work is being done in Java, but needed in many short-lived
backend sessions), a few percent can be saved by setting the initial heap size
slightly larger than the smallest that works, to avoid an initial garbage
collection. In a test using PL/Java to do trivial work (nothing but
`SELECT sqlj.get_classpath('public')`), the sweet spot comes around
`-Xms5m` (which seems to end up allocating 6, but completes
with no GC in my testing).
