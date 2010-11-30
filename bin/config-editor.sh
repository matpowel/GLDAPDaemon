#!/bin/sh
#
# Run Visual Config Editor
#

GCALDIR=`dirname "$0"`/..

java -Xmx256m -cp $GCALDIR/lib/commons-codec.jar:$GCALDIR/lib/commons-lang.jar:$GCALDIR/lib/commons-logging.jar:$GCALDIR/lib/gcal-daemon.jar:$GCALDIR/lib/gdata-calendar.jar:$GCALDIR/lib/gdata-client.jar:$GCALDIR/lib/ical4j.jar:$GCALDIR/lib/logger.jar:$GCALDIR/lib/commons-collections.jar:$GCALDIR/lib/commons-io.jar:$GCALDIR/lib/shared-asn1.jar:$GCALDIR/lib/shared-ldap.jar:$GCALDIR/lib/rome.jar:$GCALDIR/lib/commons-httpclient.jar:$GCALDIR/lib/jdom.jar:$GCALDIR/lib/mail.jar:$GCALDIR/lib/activation.jar org.gcaldaemon.standalone.Main $GCALDIR/conf/gcal-daemon.cfg configeditor
