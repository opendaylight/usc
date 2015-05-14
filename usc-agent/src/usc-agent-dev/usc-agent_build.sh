#!/bin/bash
echo
echo "cd ./external/source/openssl/"
cd ./external/source/openssl/
echo "Building Openssl library"
make
cd ../../../
echo
sleep 2

echo
echo "cd ./common/source/libnetconf"
cd ./common/source/libnetconf/
echo "Building netconf library"
make
cd ../../../
echo
sleep 2

echo
echo "cd ../pyang"
cd ./common/source/pyang
echo "Building Yang model"
make
cd ../../../
echo

echo
echo "cd ../server/nc_server/server/"
cd ./server/nc_server/server/
echo "Building Netconf Server"
make
echo
sleep 2

echo
echo "cd ../cli/"
cd ../cli/
echo "Building Netconf client"
make 
echo

sleep 2
echo "cd ../server-sl/"
cd ../server-sl/
echo "Building Netconf Server-sl"
make
echo

sleep 2
echo "cd ../transAPI/cfgsystem"
cd ../transAPI/cfgsystem
echo "Building trans API"
make
cd ../../../../
echo
sleep 2

echo
echo "cd ../usc-agent/source/agent/"
cd ./usc-agent/source/agent/
echo "Building USC Agent"
make
echo
sleep 2
