#!/bin/sh -v

apt-get install -y openbsd-inetd
echo "8383 stream tcp nowait root /usr/local/bin/netopeer-no-stderr.sh" >> /etc/inetd.conf
cat > /usr/local/bin/netopeer-no-stderr.sh <<EOF
#!/bin/sh
/usr/local/bin/netopeer-server-sl 2>/dev/null
EOF
inetd
exit



