## config.properties - Referenzdokumentation

Die Datei ist in einer ContentBroker immer unter 

    ${CB_HOME}/conf/config.properties
    
abzulegen bzw. zu finden. Sie ist notwendiger Bestandteil jeder DNSCore Installation.

Die Datei ist in verschiedene Blöcke unterteilt, die je nach gewünschter Gesamtkonfiguration (zusammen mit der beans.xml) vorhanden sein müssen.

### localNode

Beispiel aus [config.properties.ci](../conf/config.properties.ci)

    localNode.userAreaRootPath=/ci/storage/UserArea
    localNode.ingestAreaRootPath=/ci/storage/IngestArea 
    localNode.workAreaRootPath=/ci/storage/WorkArea
    localNode.gridCacheAreaRootPath=/ci/storage/GridCacheArea
    localNode.workingResource=ciWorkingResource 
    localNode.replDestinations=ciArchiveResourceGroup
    localNode.name=localnode
    localNode.id=

The localNode block contains all the settings related to the configuration of the machine itself as well as information
regarding the administrator role. Note that the localNode is the implementation of the node concept of the domain business model.

    localNode.userAreaRootPath=
    localNode.ingestAreaRootPath=
    localNode.workAreaRootPath=
    localNode.gridCacheAreaRootPath=
    
These four properties should be set to the absolute physical paths of the mount points in the file system which hold
the aip data during the ContentBroker workflow processing stages. If you don't know anything about the concept of areas
in context of DNSCore, have a look at [this](processing_stages.md) document. It doesn't matter if the paths carry a trailing slash or not. But make sure the paths are absolute and not relative.

    localNode.replDestinations=ciArchiveResourceGroup
   
For this setting you can insert either a single destination or a comma seperated list of several destinations which are considered endpoints for the node. These are the nodes holding the secondary copies of objects for long term archival.
It depends on the implementations of the underlying grid what these node names TODO(why not node names?, here is a discrepancy between the business model side and the technical implementation details) refer to. In a typical iRODS (zone) installation these are the names of resource groups to which the objects can be replicated to.

    localNode.workingResource=

TODO does it belong on the node section?
    
    localNode.name=localnode

This property lets you specify the name which identifies the node in the system. It is used for example for synchronizing jobs between nodes (by using the DistributedConversionAdapter). While the name is theoretically arbitrary, it is recommended to use the fully qualified domain name of the node, which typically should equal the irods.server setting on iRODS based node installations (see description of irods settings below).

    localNode.id= 
    
This setting must contain the integer value primary key of the nodes correspoding entry in the nodes table of the object db.

    localNode.admin_email=da-nrw-notifier@uni-koeln.de

The email address of the administrator responsible for the node. Note that who is meant here is the administrator in the domain model sense, the person who takes responsibility for the working of the node and supervision of packages and workflow. It can be but has not necessarily to be the same person who is responsible for maintaining the machine in the infrastructure sense.

### cb

Beispiel aus [config.properties.ci](../conf/config.properties.ci)

    cb.serverSocketNumber=4455
    cb.implementation.grid=irodsGridFacade
    cb.implementation.distributedConversion=irodsDistributedConversionAdapter
    cb.implementation.repository=fedoraRepositoryFacade
    cb.implementation.metadataExtractor=jhoveMetadataExtractor
    cb.bin.python=/ci/python/python

There are a couple of settings that relate strongly to the node concept, but are not related to business concepts in any way. Instead they relate to technical concepts only. Hence they merit their own category which is called "cb" which stands for ContentBroker settings.

    cb.serverSocketNumber=

In order to let peripheral components of DNSCore (primary use of course is DA-Web) talk to and have introspection into the application state of the ContentBroker, a server process is beeing established by the ContentBroker at startup. Any free port will do. As under normal circumstances DA-Web runs on the same node (i.r. localnode will suffice) as the ContentBroker, firewall issues could possibly be neglected.

    cb.implementation.grid=

The full and node mode installations of the ContentBroker require a grid component onto which they put and from which they retrieve the long term archive contents, which correspond to the AIP in OAIS terms, and always relate to containered files (.tar) on the technical level. At the moment there exist three implementations of grid subsystems, of which two relate to iRODS configurations in different modes.

    cb.implementation.grid=irodsGridFacade
    
If set to irodsGridFacade, the iRODS installation is assumed to be configured properly to run a one zone based grid.

block described below must be inserted to a working config.properties. 
    
    cb.implementation.grid=irodsFederatedGridFacade

As opposed to the irodsGridFacade, the irodsFederatedGridFacade assumes to have an iRODS system running which is configured in a federated (TODO link to documents) manner. Note: At the moment this feature is considered experimental until it is fully tested in a load test environment.

    cb.implementation.grid=fakeGridFacade
    
The fakeGridFacade is a simple implementation which resigns any third party subsystems but only the local file system.
It has been written primarily for purposes of testing or easy experimentation for evaluation or showcasing purposes.

    cb.implementation.distributedConversion=

The ContentBroker is able to synchronize jobs between nodes in the system. To accomplish this, the unpacked objects in the WorkArea can be transfered to another node and an action can then create a job for the other node to execute which will then work with the replicated data. So two or more nodes can modify the objects state sequentially. The setting lets you choose an implementation which provides the necessary replicaton facilities.

    cb.implementation.distributedConversion=irodsDistributedConversionAdapter

When selecting the irodsDistributedConversionAdapter, an installation of iRODS (in zone mode) is used. The irods settings in config.properties have to be present (also see irods section below).
    
    cb.implementation.distributedConversion=fakeDistributedConversionAdapter

This implementation is useful for testing or evaluation purposes.
    
This setting determines the connection to the presentation repository subsystem.
    
    cb.implementation.repository=
    cb.implementation.repository=fedoraRepositoryFacade
    cb.implementation.repository=fakeRepositoryFacade (default)
    
When choosing fedoraRepositoryFacade, Fedora is used as the presentation layer subsystem.
This fakeRepositoryFacade implementation is useful for testing or evaluation purposes.

    cb.bin.python=

Here you have to insert the command to run an instance of python (at the moment >= 2.7 is required). If you are sure the required command is globally visible in the environment (the shell or process) in which the ContentBroker.jar is intended to run, you simple can insert something as simple as "python" as a value. If this is not the case, for example if the packaging system of your distro has only python in a version < 2.7 and you have a self compiled version at another path
on your file system, you should insert the full path to the python binary as a value.

	cb.implementation.metadataExtractor=
	cb.implementation.metadataExtractor=jhoveMetadataExtractor (defaul)
	cb.implementation.metadataExtractor=fakeMetadataExtractor
	
	
### irods

Wenn mindestens eines der Subsysteme "gridFacade" bzw. "distributedConversionAdapter", konfigurierbar per

    cb.implementation.grid=
    cb.implementation.distributedConversionAdapter=
    
auf die Verwendung von iRODS hin konfiguriert sind, siehe

    cb.implementation.grid=irodsGridFacade
    cb.implementation.distributedConversionAdapter=irodsDistributedConversionAdapter
    
so ist es erforderlich, dass der optionale "irods.*"-Block auch innerhalb der config.properties vorhanden ist.

Ein vollständiges Beispiel für den Block ist [config.properties.ci](../conf/config.properties.ci)

    irods.user=rods
    irods.password=WpXlLLg3a4/S/iYrs6UhtQ==
    irods.server=cihost
    irods.zone=c-i
    irods.default_resc=ciWorkingResource
    irods.pam=false
    irods.keyStorePassword=
    irods.keyStorePath=
    irods.trustStorePath=


 

These settings are optional and must be set only if cb.implementation.grid or cb.implementaion.districutedConversion
are set to use the corresponding irods specific implementations. Nodes not using irods dont need these parameters.

    irods.user=
    irods.server=
    irods.zone=
    irods.default_resc=
    irods.pam=
    irods.keyStorePassword=
    irods.keyStorePath=
    irods.trustStorePath=

asdf

    irods.password= 

The password has to be encrypted with the password encryptor/decryptor which is part of the DNSCore project itself (if you haven't already, you can see the sub project [here](https://github.com/da-nrw/DNSCore/tree/master/PasswordEncryptor).

### fedora

Beispiel aus [config.properties.ci](../conf/config.properties.ci)

    fedora.url=http://localhost:8080/fedora
    fedora.user=fedoraAdmin
    fedora.password=BYi/MFjKDFd5Dpe52PSUoA==

When the application has been installed in one of wither pres or full mode, the presentation module is activated via its respective import in the import section of the beans.xml (see down below).

    fedora.url=
    fedora.user=

In pres or full mode the ContentBroker and the presentation repository are hosted on one and the same machine. Fedora runs on a tomcat and fedora.url points to the http://... adress of Fedora while fedora.user is a fedora user prepared
for usage by the ContentBroker.

    fedora.password=
    
The passwort has to be encrypted/decrypted with the PasswordEncryptor of DNSCore.

### elasticsearch 

Beispiel [config.properties.ci](../conf/config.properties.ci)

    elasticsearch.index=portal_ci
    elasticsearch.hosts=localhost
    elasticsearch.cluster=cluster_ci

The elasticsearch settings only are necessary on nodes which provide presentation repository functionality, which is enabled by choosing either the full or pres setting during installation.

    elasticsearch.index=
    elasticsearch.hosts=
    elasticsearch.cluster=
    
Make sure you insert the same settings you have used during your elasticsearch installation.