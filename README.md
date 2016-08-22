# CopyCat
OpenDedupe Filesystem Sync Listener for Cloud Storage

This service is used in conjuction with the SDFS Filesystem to allow multiple SDFS volumes to share metadata. It listens for metadata changes on the control port of SDFS Volumes and notifies other configured volumes when metadata has changed. 

It is particularly useful when using with backup to cloud storage becuase is allows different backup servers to restore images regardless of location of the origional backup.

CopyCat is licensed under the [Apache License](http://www.apache.org/licenses/LICENSE-2.0)

To user copycat your sdfs volume must meet the following requirements:

1. Must use an object store backend such as S3,Azure,Google,swift
2. The SDFS control port for the volume must be visible from the copy cat service
3. All SDFS volumes in the cluster must use the same object store bucket
4. All SDFS volumes in the cluster must use the same encryption key
5. The SDFS volumes must be accessable from the copycat service.

## Setup Guide for using Filesync service

The below example will allow two volumes to share metadata. It assumes there are two hosts (host1 and host2). This can also run on a single host but the port for volume one would be 6442 and volume2 would be **6443**. In addition if you are testing this on one host the volume-name for volume2 would be **pool1**.

**Step 1 - Create two SDFS Volumes**

As stated above, all volumes must share the same bucket for filesync to work. In addition the control port mu

On host1 create a volume

    mkfs.sdfs --volume-name=pool0 --volume-capacity=1TB --cloud-secret-key=<secret> --cloud-access-key=<access-key> --cloud-bucket-name=<a shared bucket name> --aws-enabled=true --enable-replication-master --sdfscli-password=apassword
    
On host2 create a volume with the same bucket name

    mkfs.sdfs --volume-name=pool0 --volume-capacity=1TB --cloud-secret-key=<secret> --cloud-access-key=<access-key> --cloud-bucket-name=<a shared bucket name> --aws-enabled=true --enable-replication-master --sdfscli-password=apassword

**Step 2 - Mount both volumes**

On host 1

    mount -t sdfs pool0 /mnt
    
On host 2

    mount -t sdfs pool0 /mnt

**Step 3 - Get Volume IDs for each volume**
In this step you will want to verify both volumes are sharing the same pool and get their volume ids. This command can be run on either host.
    
  sdfscli --list-cloud-volumes
  
![alt tag](http://www.opendedup.org/images/listcloudvolumes.png)
  
The volume id's will be listed in the **ID** column. Keep note of these volume id's. The volume ids are also contained the config.xml documents for the volumes in the **serial-number** tag.

![alt tag](http://www.opendedup.org/images/serialnumber.png)

**Step 4 - Configure CopyCat**

CopyCat is configured throught the config.json file within the tar package.

    {
    "persist-path" : "/tmp",
    "debug" : true,
    "servers" : [ {
        "port" : 6442,
        "host": "host0",
        "password" : "admin",
        "volumeid" : 272362632702517055,
        "listen" : true,
        "update" : true
    },
    {
        "port" : 6442,
        "host": "host1",
        "password" : "admin",
        "volumeid" : 1900326592403395044,
        "listen" : true,
        "update" : true
    }]
    }

**persist-path:** This is the path where changes will peristed until they are committed to the other volumes

**debug:** Should debug output be sent to standard out

**servers:** An array of volumes that copycat will listen and commit changes to

**port:** The TCP port that the SDFS Volume is listening on for control. This is 6442 by default but increments up based on the number of volumes mounted on the host.

**host:** The hostname or IP address that the SDFS Volume is on.

**password:** The password that was set for authenticating to the volume. This was set during volume creation with the --sdfscli-password parameter

**volumeid:** The id for the volume as showin with sdfscli --list-cloud-volumes

**listen:** If set to true will listen for volume changes and notify other volumes when changes occure

**update:** It set to true copycat will notify the volume when changes occure on other volumes.

**Step 5 - Start CopyCat**

CopyCat requires java 8+. Mutilple instances of copycat can be running on multiple systems using the same config to provide high availability.

    java -cp cc.jar:libs/* com.datish.copycat.Server config.json





