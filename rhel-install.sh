mkdir -p /usr/share/sdfs/oddcopycat
cp cc.jar /usr/share/sdfs/oddcopycat
mkdir /etc/sdfs/
cp copycat.json /etc/sdfs/
cp init.oddcopycat /etc/init.d/oddcopycat
chmod +x /etc/init.d/oddcopycat