#!/bin/sh -v
cd ../../../../
mkdir -p usc-karaf/target/assembly/src/main/certificates
cp -r usc-impl/src/main/certificates usc-karaf/target/assembly/src/main/
mkdir -p usc-karaf/target/assembly/etc/opendaylight/karaf
cp usc-karaf/src/test/scripts/karaf-config/01-usc-netconf.xml usc-karaf/target/assembly/etc/opendaylight/karaf/01-netconf.xml
cp usc-karaf/src/test/scripts/karaf-config/99-netconf-connector.xml usc-karaf/target/assembly/etc/opendaylight/karaf
./usc-karaf/target/assembly/bin/karaf 
