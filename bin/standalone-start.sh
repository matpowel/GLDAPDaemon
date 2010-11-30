#!/bin/sh
#
# Run GCALDaemon
#

GCALDIR=`dirname "$0"`/..

java -Xmx256m -cp $GCALDIR/lib/commons-codec.jar:$GCALDIR/lib/commons-lang.jar:$GCALDIR/lib/commons-logging.jar:$GCALDIR/lib/gcal-daemon.jar:$GCALDIR/lib/gdata-client.jar:$GCALDIR/lib/google-collect-1.0-rc1.jar:$GCALDIR/lib/gdata-contacts-3.0.jar:$GCALDIR/lib/logger.jar:$GCALDIR/lib/commons-collections.jar:$GCALDIR/lib/commons-io.jar:$GCALDIR/lib/shared-asn1.jar:$GCALDIR/lib/shared-ldap.jar org.gldapdaemon.standalone.Main $GCALDIR/conf/gcal-daemon.cfg
