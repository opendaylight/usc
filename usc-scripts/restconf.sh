#!/bin/sh -v

curl -u admin:admin -H "Accept:application/xml" http://localhost:8181/restconf/config/network-topology:network-topology/topology/topology-netconf/node/controller-config-tcp/yang-ext:mount/config:modules | xmllint --format -

curl -u admin:admin 'http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/netopeer-tcp/' | python -m json.tool

curl -u admin:admin 'http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/controller-config-tcp/' | python -m json.tool

curl -u admin:admin 'http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/netopeer-tcp/yang-ext:mount/nc-notifications:netconf/' | python -m json.tool

curl -u admin:admin 'http://localhost:8181/restconf/operational/network-topology:network-topology/topology/topology-netconf/node/controller-config-tcp/yang-ext:mount/nc-notifications:netconf/' | python -m json.tool


