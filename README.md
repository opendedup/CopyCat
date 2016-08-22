# CopyCat
OpenDedupe Filesystem Sync Listener for Cloud Storage

This service is used in conjuction with the SDFS Filesystem to allow multiple SDFS volumes to share metadata. It listens for metadata changes on the control port of SDFS Volumes and notifies other configured volumes when metadata has changed. 

It is particularly useful when using with backup to cloud storage becuase is allows different backup servers to restore images regardless of location of the origional backup.

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
    mkfs.sdfs --volume-name=pool0 --volume-capacity=1TB --cloud-secret-key=<secret> --cloud-access-key=<access-key> --cloud-bucket-name=<a shared bucket name> --aws-enabled=true --enable-replication-master
On host2 create a volume with the same bucket name
   mkfs.sdfs --volume-name=pool0 --volume-capacity=1TB --cloud-secret-key=<secret> --cloud-access-key=<access-key> --cloud-bucket-name=<a shared bucket name> --aws-enabled=true --enable-replication-master

  


