# Hadoop Cluster Setup

## Target Environment

Target Environment

  * **OS:** CentOS 7.5
  * **Cluster Size:** \~100 Nodes
  * **Designated Roles:**
      * `master1.ncl.internal`: NameNode, ResourceManager
      * `master2.ncl.internal`: Secondary NameNode
      * `worker[01-97].ncl.internal`: DataNode, NodeManager

-----

### Nodes Check 

To ensure every VM in the cluster is standardized and prepared to run Hadoop services correctly. Consistency across nodes is critical to avoid hard-to-diagnose errors.

#### System & Network Setup

1.  Update System & Install NTP:

    ```bash
    # Update all system packages to the latest versions for security and stability.
    sudo yum update -y
    # Install wget for downloading files and ntp for time synchronization.
    sudo yum install -y wget ntp
    ```

2.  Configure NTP:

      * Hadoop cluster nodes must have their system clocks synchronized. Time discrepancies can cause issues with timestamps on data blocks and task coordination. First change the configuration and add the new lines pointing to your internal servers.

    ```bash
    sudo vi /etc/ntp.conf
    ```

    ```bash
    # Comment out the default public servers
    # server 0.centos.pool.ntp.org iburst
    # server 1.centos.pool.ntp.org iburst
    # server 2.centos.pool.ntp.org iburst
    # server 3.centos.pool.ntp.org iburst

    # Point to internal NCL NTP servers
    server ntp-primary.ncl.internal prefer
    server ntp-secondary.ncl.internal
    ```

    ```bash
    sudo systemctl start ntpd
    sudo systemctl enable ntpd
    ```

3.  Configure Hostname Resolution

      * Hadoop services communicate using hostnames. Each node must be able to resolve the hostname of every other node to its correct IP address. The `/etc/hosts` file provides a local DNS lookup for this.

    ```
    # /etc/hosts
    192.168.1.10   master1.ncl.internal master1
    192.168.1.11   master2.ncl.internal master2
    192.168.1.12   worker01.ncl.internal worker01
    ...
    192.168.1.109  worker97.ncl.internal worker97
    ```

4.  Disable Firewall & SELinux

      * In a trusted, private on-premise network, disabling the firewall and SELinux simplifies setup by preventing them from blocking the many ports Hadoop uses for inter-node communication. 


    ```bash
    sudo systemctl stop firewalld
    sudo systemctl disable firewalld
    sudo setenforce 0
    # To make SELinux change persistent, edit /etc/selinux/config
    # and set SELINUX=disabled
    ```

5.  Install Java (OpenJDK 8)

      * Hadoop and all its ecosystem components are Java-based applications, making the Java Development Kit (JDK) a hard requirement.

    ```bash
    sudo yum install -y java-1.8.0-openjdk-devel
    ```

#### Create Dedicated Hadoop User

```bash
sudo useradd hadoop
sudo passwd hadoop
```

-----

### Passwordless SSH Setup (Run from `master1`)

  * The master node (`master1`) uses SSH to start, stop, and manage the Hadoop daemons (DataNode, NodeManager) on all worker nodes. Passwordless SSH allows these control scripts to run automatically without requiring a password for each of the 97+ worker nodes.

1.  Switch to `hadoop` user on `master1`

    ```bash
    su - hadoop
    ```

2.  Generate SSH Key

      * This creates a public/private key pair. The private key stays on `master1`, and the public key will be distributed to all other nodes.

    ```bash
    ssh-keygen -t rsa
    # Press Enter for all prompts to accept defaults (no passphrase).
    ```

3.  Copy Public Key to All Nodes

      * This command appends the public key from `master1` to the `authorized_keys` file on each target node. This authorizes the `master1` node to connect without a password.

    ```bash
    # On master1 as user 'hadoop'
    ssh-copy-id hadoop@master1.ncl.internal
    ssh-copy-id hadoop@master2.ncl.internal
    ssh-copy-id hadoop@worker01.ncl.internal
    # ... repeat for all 97 worker nodes
    ```

4.  Verify Passwordless Login

    ```bash
    ssh worker15.ncl.internal 'hostname'
    # Should return "worker15.ncl.internal" without a password prompt.
    ```

-----

### Hadoop Installation & Configuration (Run on ALL Nodes)

#### Download and Extract Hadoop

1.  Switch to `hadoop` user:

    ```bash
    su - hadoop
    ```

2.  Download and Unpack Hadoop (e.g., version 2.7.7)

    ```bash
    wget https://archive.apache.org/dist/hadoop/core/hadoop-2.7.7/hadoop-2.7.7.tar.gz
    tar -xzf hadoop-2.7.7.tar.gz
    mv hadoop-2.7.7 hadoop
    ```

#### Set Environment Variables

  * Setting these variables in `.bashrc` makes Hadoop commands globally available and tells Hadoop where to find its own configuration files and the Java installation.
    Edit `/home/hadoop/.bashrc` and add the following:

```bash
# HADOOP ENVIRONMENT VARIABLES
export HADOOP_HOME=/home/hadoop/hadoop
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export PATH=$PATH:$HADOOP_HOME/bin:$HADOOP_HOME/sbin
export JAVA_HOME=$(readlink -f /usr/bin/java | sed "s:bin/java::")
```

Source the file to apply the changes: `source ~/.bashrc`

#### Configure Hadoop Files

  * These XML files control the behavior of the Hadoop cluster. They must be distributed identically across all nodes.

Navigate to `$HADOOP_CONF_DIR`.

1.  **`hadoop-env.sh`:** Sets environment variables specifically for Hadoop, most importantly `JAVA_HOME`.

2.  **`core-site.xml`:** The primary configuration file. `fs.defaultFS` tells all nodes where to find the HDFS NameNode, which is the entry point for the distributed filesystem.

    ```xml
    <configuration>
        <property>
            <name>fs.defaultFS</name>
            <value>hdfs://master1.ncl.internal:9000</value>
        </property>
    </configuration>
    ```

3.  **`hdfs-site.xml`:** HDFS-specific settings.

      * `dfs.namenode.name.dir` / `dfs.datanode.data.dir`: Local filesystem paths where the NameNode stores its metadata and DataNodes store the actual data blocks.
      * `dfs.replication`: A critical setting for fault tolerance. A value of `3` means every data block written to HDFS will be copied to three different worker nodes.

    ```xml
    <configuration>
        <property>
            <name>dfs.namenode.name.dir</name>
            <value>file:///home/hadoop/data/hdfs/namenode</value>
        </property>
        <property>
            <name>dfs.datanode.data.dir</name>
            <value>file:///home/hadoop/data/hdfs/datanode</value>
        </property>
        <property>
            <name>dfs.replication</name>
            <value>3</value>
        </property>
        <property>
            <name>dfs.blocksize</name>
        <value>128m</value>
    </property>
    </configuration>
    ```

4.  **`mapred-site.xml`:** Configures the execution framework for MapReduce jobs. Here, we tell it to run on YARN, the cluster resource manager.

    ```xml
    <configuration>
        <property>
            <name>mapreduce.framework.name</name>
            <value>yarn</value>
        </property>
    </configuration>
    ```

5.  **`yarn-site.xml`:** YARN-specific settings. This tells the worker nodes (NodeManagers) where to find the central master process for resource management (the ResourceManager).

    ```xml
    <configuration>
        <property>
            <name>yarn.nodemanager.aux-services</name>
            <value>mapreduce_shuffle</value>
        </property>
        <property>
            <name>yarn.resourcemanager.hostname</name>
            <value>master1.ncl.internal</value>
        </property>
    </configuration>
    ```

6.  **`slaves` file:** A simple text file used by the `start-dfs.sh` and `start-yarn.sh` scripts on the master node to know which machines are the workers in the cluster.

    ```
    worker01.ncl.internal
    # ...
    worker97.ncl.internal
    ```

-----

### Starting the Cluster (Run from `master1`)

1.  Format the NameNode

      * This is a one-time initialization step that prepares the storage directory for the NameNode's metadata. **It deletes any existing data in HDFS.**

    ```bash
    hdfs namenode -format
    ```

2.  Start HDFS Daemons

      * This script starts the `NameNode` on `master1`, the `SecondaryNameNode` on `master2`, and the `DataNode` daemon on all hosts listed in the `slaves` file.

    ```bash
    start-dfs.sh
    ```

3.  Start YARN Daemons

      * This script starts the `ResourceManager` on `master1` and the `NodeManager` daemon on all hosts listed in the `slaves` file.

    ```bash
    start-yarn.sh
    ```

-----

### Verification

1.  Check Running Daemons with `jps`

      * The `jps` (Java Virtual Machine Process Status) command lists all running Java processes. This is a quick way to confirm that the correct Hadoop daemons have started on each node.
      * **On `master1`**, you should see: `NameNode`, `ResourceManager`, `Jps`.
      * **On `master2`**, you should see: `SecondaryNameNode`, `Jps`.
      * **On any `worker` node**, you should see: `DataNode`, `NodeManager`, `Jps`.

2.  Check HDFS Report

      * This command contacts the NameNode and asks for a complete status report of the filesystem, including total capacity, space used, and the number of live DataNodes.


    ```bash
    hdfs dfsadmin -report
    # Look for "Live datanodes (97)"
    ```

3.  Perform a Test File Operation

      * The ultimate test is to perform a simple filesystem operation to confirm you can read and write to HDFS.

    ```bash
    hdfs dfs -mkdir /test
    hdfs dfs -ls /
    # Should show the /test directory you created.
    ```

The Hadoop cluster is now installed, configured, and running.