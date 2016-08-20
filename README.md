# CopyCat
OpenDedupe Filesystem Sync Listener for Cloud Storage

This service is used in conjuction with the SDFS Filesystem to allow multiple SDFS volumes to share metadata. It listens for metadata changes on the control port of SDFS Volumes and notifies other configured volumes when metadata has changed. 

It is particularly useful when using with backup to cloud storage becuase is allows different backup servers to restore images regardless of location of the origional backup.

To user copycat your sdfs volume must meet the following requirements:
1. Must use an object store backend such as S3,Azure,Google,swift
2. The SDFS control port for the volume must be visible from the copy cat service
3. All SDFS volumes in the cluster must use the same object store bucket
4. All SDFS volumes in the cluster must use the same encryption key


