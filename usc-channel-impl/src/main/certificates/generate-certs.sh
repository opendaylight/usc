#!/bin/sh -v

# Steps to generate certificates

# 1.  Create self-signed root CA certificate.

openssl genrsa -out rootCA.key 2048
openssl req -x509 -new -nodes -key rootCA.key -days 1024 -out rootCA.pem<<EOF
US
California
Santa Clara
Futurewei Technologies
IP Lab
USC Sample Root CA
gary.wu1@huawei.com
EOF


# 2.  Create client certificate.

openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr<<EOF
US
California
Santa Clara
Futurewei Technologies
IP Lab
localhost
gary.wu1@huawei.com


EOF
openssl x509 -req -in client.csr -CA rootCA.pem -CAkey rootCA.key -CAcreateserial -out client.pem -days 500
openssl pkcs8 -topk8 -inform pem -in client.key -outform pem -nocrypt -out client.key.pem

# Use the resulting client-chain.pem in UscPlugin.

# 3.  Create server certificate.

openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr<<EOF
US
California
Santa Clara
Futurewei Technologies
IP Lab
localhost
gary.wu1@huawei.com


EOF
openssl x509 -req -in server.csr -CA rootCA.pem -CAkey rootCA.key -CAcreateserial -out server.pem -days 500
openssl pkcs8 -topk8 -inform pem -in server.key -outform pem -nocrypt -out server.key.pem

# The server.pem seems to be adequate for UscAgent (i.e. the chain is not necessary).


