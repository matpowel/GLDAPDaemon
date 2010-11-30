#!/bin/sh
#
# Run the password encoder
#

GCALDIR=`dirname "$0"`/..

java -cp $GCALDIR/lib/gcal-daemon.jar org.gcaldaemon.core.PasswordEncoder
